package dsc.store;

import com.fasterxml.jackson.core.type.TypeReference;
import dsc.event.Event;
import dsc.event.Hashing;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Append-only event log over SQLite. An append, its branch-head move and its
 * audit row share one transaction: a crash leaves either the old head or the
 * fully written new event.
 */
public class EventStore implements AutoCloseable {

    public interface PreCommitHook {
        void beforeCommit(Connection connection) throws SQLException;
    }

    private final Connection conn;
    private final boolean ownsConnection;
    private volatile PreCommitHook preCommitHook;

    public EventStore(Path file) {
        this(Db.open(file), true);
    }

    public EventStore(Connection connection, boolean ownsConnection) {
        this.conn = connection;
        this.ownsConnection = ownsConnection;
        try {
            cleanupIncomplete();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public void setPreCommitHook(PreCommitHook hook) {
        this.preCommitHook = hook;
    }

    public Connection connection() {
        return conn;
    }

    /** Rows marked incomplete belong to a transaction that never committed. */
    private void cleanupIncomplete() throws SQLException {
        conn.setAutoCommit(false);
        int deleted;
        try (PreparedStatement ps = conn.prepareStatement(
                "DELETE FROM derived_versions WHERE complete=0")) {
            deleted = ps.executeUpdate();
        }
        if (deleted > 0 || conn.isReadOnly() == false) {
            conn.commit();
        }
    }

    public synchronized String head(String branch) {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT head_event_id FROM branches WHERE name=?")) {
            ps.setString(1, branch);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString(1) : null;
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public synchronized List<String> branches() {
        List<String> out = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT name FROM branches ORDER BY created_at_millis, name");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                out.add(rs.getString(1));
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        return out;
    }

    public synchronized Map<String, Object> branchRow(String name) {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT name, parent_branch, forked_from_event_id, head_event_id, created_at_millis "
                        + "FROM branches WHERE name=?")) {
            ps.setString(1, name);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("name", rs.getString("name"));
                m.put("parentBranch", rs.getString("parent_branch"));
                m.put("forkedFromEventId", rs.getString("forked_from_event_id"));
                m.put("headEventId", rs.getString("head_event_id"));
                m.put("createdAtMillis", rs.getLong("created_at_millis"));
                return m;
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Append an event. The new event's content parent must equal the current
     * branch head unless {@code forceParent} matches an existing event on the
     * branch (used to rebuild an imported log / fork a branch).
     */
    public synchronized Event append(String branchName,
                                     String actor,
                                     String type,
                                     Map<String, Object> payload,
                                     String clientKey,
                                     String expectsParentId,
                                     String forceParent,
                                     long createdAtMillis) {
        Objects.requireNonNull(branchName, "branch");
        String actualHead = head(branchName);
        String parentId;
        long seq;
        if (forceParent != null) {
            parentId = forceParent;
            seq = countBranch(branchName);
        } else {
            if (expectsParentId != null && actualHead != null
                    && !expectsParentId.equals(actualHead)) {
                throw new ConflictException(
                        "branch " + branchName + " moved: expected parent "
                                + expectsParentId + " but head is " + actualHead,
                        actualHead);
            }
            if (expectsParentId == null && actualHead != null
                    && payload.get("_allowMissingExpectedParent") == null) {
                throw new ConflictException(
                        "expectedParent is required for append on non-empty branch "
                                + branchName,
                        actualHead);
            }
            parentId = actualHead;
            seq = actualHead == null ? 0L : event(actualHead).seq() + 1;
        }
        Map<String, Object> clean = new LinkedHashMap<>(payload);
        clean.remove("_allowMissingExpectedParent");
        Event event = new Event(null, parentId, branchName, seq,
                actor, type, createdAtMillis,
                clientKey, expectsParentId, clean);
        String id = Hashing.eventId(event.bodyForHash());
        Event withId = new Event(id, parentId, branchName, seq,
                actor, type, createdAtMillis,
                clientKey, expectsParentId, clean);

        try {
            conn.setAutoCommit(false);
            insertEvent(conn, withId);
            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE branches SET head_event_id=? WHERE name=?")) {
                ps.setString(1, id);
                ps.setString(2, branchName);
                if (ps.executeUpdate() == 0) {
                    throw new SQLException("unknown branch " + branchName);
                }
            }
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO append_audit(event_id, branch, previous_head, new_head, at_millis) "
                            + "VALUES(?,?,?,?,?)")) {
                ps.setString(1, id);
                ps.setString(2, branchName);
                ps.setString(3, parentId);
                ps.setString(4, id);
                ps.setLong(5, createdAtMillis);
                ps.executeUpdate();
            }
            PreCommitHook hook = preCommitHook;
            if (hook != null) {
                hook.beforeCommit(conn);
            }
            conn.commit();
        } catch (SQLException e) {
            rollback();
            if (e.getMessage() != null && e.getMessage().contains("UNIQUE")) {
                throw new ConflictException(
                        "duplicate client key or event on branch " + branchName,
                        head(branchName));
            }
            throw new RuntimeException(e);
        }
        return withId;
    }

    private int countBranch(String branchName) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT COUNT(*) FROM events WHERE branch=?")) {
            ps.setString(1, branchName);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    public synchronized Event event(String id) {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT * FROM events WHERE id=?")) {
            ps.setString(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new IllegalArgumentException("unknown event " + id);
                }
                return mapEvent(rs);
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public synchronized boolean exists(String id) {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT 1 FROM events WHERE id=?")) {
            ps.setString(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    /** Ancestor chain from the branch head (newest first). */
    public synchronized List<Event> ancestry(String branchName, String stopBeforeId) {
        List<Event> out = new ArrayList<>();
        String cursor = head(branchName);
        while (cursor != null) {
            Event e = event(cursor);
            if (stopBeforeId != null && stopBeforeId.equals(cursor)) {
                break;
            }
            out.add(e);
            cursor = e.parentId();
        }
        return out;
    }

    /** Oldest-first events on a branch, optionally stopping at an event (inclusive). */
    public synchronized List<Event> chain(String branchName, String upToInclusive) {
        List<Event> newestFirst = new ArrayList<>();
        String cursor = upToInclusive != null ? upToInclusive : head(branchName);
        while (cursor != null) {
            Event e = event(cursor);
            if (!branchName.equals(e.branch())) {
                break;
            }
            newestFirst.add(e);
            cursor = e.parentId();
        }
        java.util.Collections.reverse(newestFirst);
        return newestFirst;
    }

    public synchronized List<Event> allEvents(String branchName) {
        List<Event> out = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT * FROM events WHERE branch=? ORDER BY seq")) {
            ps.setString(1, branchName);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(mapEvent(rs));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        return out;
    }

    public synchronized void createBranch(String name, String parentBranch,
                                          String forkedFromEventId, long now) {
        String head = forkedFromEventId != null
                ? forkedFromEventId
                : head(parentBranch == null ? "trunk" : parentBranch);
        try {
            conn.setAutoCommit(false);
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO branches(name, parent_branch, forked_from_event_id, "
                            + "head_event_id, created_at_millis) VALUES(?,?,?,?,?)")) {
                ps.setString(1, name);
                ps.setString(2, parentBranch);
                ps.setString(3, forkedFromEventId);
                ps.setString(4, head);
                ps.setLong(5, now);
                ps.executeUpdate();
            }
            conn.commit();
        } catch (SQLException e) {
            rollback();
            throw new RuntimeException(e);
        }
    }

    private void insertEvent(Connection c, Event e) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO events(id, parent_id, branch, seq, actor, type, "
                        + "created_at_millis, client_key, expects_parent_id, payload_json) "
                        + "VALUES(?,?,?,?,?,?,?,?,?,?)")) {
            ps.setString(1, e.id());
            ps.setString(2, e.parentId());
            ps.setString(3, e.branch());
            ps.setLong(4, e.seq());
            ps.setString(5, e.actor());
            ps.setString(6, e.type());
            ps.setLong(7, e.createdAtMillis());
            ps.setString(8, e.clientKey());
            ps.setString(9, e.expectsParentId());
            ps.setString(10, Json.write(e.payload()));
            ps.executeUpdate();
        }
    }

    private Event mapEvent(ResultSet rs) throws SQLException {
        Map<String, Object> payload = Json.MAPPER.readValue(
                rs.getString("payload_json"), new TypeReference<Map<String, Object>>() {
                });
        return new Event(
                rs.getString("id"),
                rs.getString("parent_id"),
                rs.getString("branch"),
                rs.getLong("seq"),
                rs.getString("actor"),
                rs.getString("type"),
                rs.getLong("created_at_millis"),
                rs.getString("client_key"),
                rs.getString("expects_parent_id"),
                payload);
    }

    private void rollback() {
        try {
            conn.rollback();
        } catch (SQLException ignored) {
            // best effort
        }
    }

    @Override
    public synchronized void close() {
        if (!ownsConnection) {
            return;
        }
        try {
            conn.close();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }
}
