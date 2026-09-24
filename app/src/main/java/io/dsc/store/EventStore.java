package io.dsc.store;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.dsc.model.Event;
import io.dsc.model.EventType;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * SQLite-backed append-only store.
 *
 * Commit semantics: append + head-pointer update happen in ONE transaction.
 * A crash (test hook) before commit leaves the previous version; after commit
 * the whole new version is visible. Nothing partial is ever readable.
 */
public final class EventStore implements AutoCloseable {

    private final String url;
    private final ObjectMapper mapper = new ObjectMapper();
    private Consumer<String> crashHook; // invoked just before commit with branchId

    public EventStore(String dbPath) {
        this.url = "jdbc:sqlite:" + dbPath;
        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException(e);
        }
        initialise();
    }

    public void setCrashHook(Consumer<String> hook) {
        this.crashHook = hook;
    }

    private Connection connect() {
        try {
            Connection c = DriverManager.getConnection(url);
            try (Statement st = c.createStatement()) {
                st.execute("PRAGMA journal_mode=WAL");
                st.execute("PRAGMA foreign_keys=ON");
                st.execute("PRAGMA busy_timeout=5000");
            }
            c.setAutoCommit(false);
            return c;
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private void initialise() {
        try (Connection c = connect(); Statement st = c.createStatement()) {
            for (String ddl : Schema.CREATE.split(";\\s*")) {
                if (!ddl.isBlank()) st.execute(ddl);
            }
            Integer version = readVersion(st);
            if (version == null) {
                st.execute("INSERT INTO schema_meta(key, value) VALUES('version', '"
                        + Schema.CURRENT_VERSION + "')");
            } else if (version < Schema.CURRENT_VERSION) {
                for (String migration : Schema.MIGRATIONS) {
                    if (!migration.isBlank()) st.execute(migration);
                }
                st.execute("UPDATE schema_meta SET value='" + Schema.CURRENT_VERSION
                        + "' WHERE key='version'");
            }
            c.commit();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private Integer readVersion(Statement st) throws SQLException {
        try (ResultSet rs = st.executeQuery("SELECT value FROM schema_meta WHERE key='version'")) {
            return rs.next() ? Integer.parseInt(rs.getString(1)) : null;
        }
    }

    public long headSeq(String branchId) {
        try (Connection c = connect();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT head_seq FROM branch_heads WHERE branch_id=?")) {
            ps.setString(1, branchId);
            try (ResultSet rs = ps.executeQuery()) {
                long seq = rs.next() ? rs.getLong(1) : 0L;
                c.commit();
                return seq;
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public String headHash(String branchId) {
        try (Connection c = connect();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT head_hash FROM branch_heads WHERE branch_id=?")) {
            ps.setString(1, branchId);
            try (ResultSet rs = ps.executeQuery()) {
                String hash = rs.next() ? rs.getString(1) : null;
                c.commit();
                return hash;
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public List<Event> events(String branchId) {
        try (Connection c = connect();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT event_id, seq, branch_id, type, occurred_at, actor, payload, "
                             + "prev_hash, hash FROM events WHERE branch_id=? ORDER BY seq")) {
            ps.setString(1, branchId);
            List<Event> out = new ArrayList<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) out.add(map(rs));
            }
            c.commit();
            return out;
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public Event event(String eventId) {
        try (Connection c = connect();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT event_id, seq, branch_id, type, occurred_at, actor, payload, "
                             + "prev_hash, hash FROM events WHERE event_id=?")) {
            ps.setString(1, eventId);
            try (ResultSet rs = ps.executeQuery()) {
                Event e = rs.next() ? map(rs) : null;
                c.commit();
                return e;
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Atomically append events. All supplied events are one commit; they share
     * the caller's expected head for optimistic concurrency.
     */
    public List<Event> append(String caseId, String branchId, long expectedHeadSeq,
                              List<UnsavedEvent> unsaved) {
        try (Connection c = connect()) {
            long current = readHeadSeq(c, branchId);
            if (current != expectedHeadSeq) {
                c.rollback();
                throw new ConflictException(branchId, expectedHeadSeq, current);
            }
            String prevHash = readHeadHash(c, branchId);
            long seq = current;
            List<Event> saved = new ArrayList<>();
            for (UnsavedEvent u : unsaved) {
                seq++;
                String payloadJson = u.payload.toString();
                String eventId = u.eventId != null ? u.eventId
                        : branchId + "-e" + seq;
                String hash = Hashes.eventHash(branchId, seq, u.type.name(),
                        u.occurredAt, u.actor, payloadJson, prevHash);
                try (PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO events(event_id, case_id, branch_id, seq, type, "
                                + "occurred_at, actor, payload, prev_hash, hash) "
                                + "VALUES(?,?,?,?,?,?,?,?,?,?)")) {
                    ps.setString(1, eventId);
                    ps.setString(2, caseId);
                    ps.setString(3, branchId);
                    ps.setLong(4, seq);
                    ps.setString(5, u.type.name());
                    ps.setLong(6, u.occurredAt);
                    ps.setString(7, u.actor);
                    ps.setString(8, payloadJson);
                    ps.setString(9, prevHash);
                    ps.setString(10, hash);
                    ps.executeUpdate();
                }
                Event ev = new Event(eventId, seq, branchId, u.type, u.occurredAt,
                        u.actor, u.payload, prevHash, hash);
                saved.add(ev);
                prevHash = hash;
            }
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO branch_heads(branch_id, head_seq, head_hash, version_token) "
                            + "VALUES(?,?,?,1) ON CONFLICT(branch_id) DO UPDATE SET "
                            + "head_seq=excluded.head_seq, head_hash=excluded.head_hash, "
                            + "version_token=version_token+1")) {
                ps.setString(1, branchId);
                ps.setLong(2, seq);
                ps.setString(3, prevHash);
                ps.executeUpdate();
            }

            if (crashHook != null) crashHook.accept(branchId); // simulates power loss pre-commit
            c.commit();
            return saved;
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public void putRawBody(String ref, String caseId, String kind, String content, String mediaType) {
        try (Connection c = connect();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO raw_bodies(ref, case_id, kind, content, media_type) "
                             + "VALUES(?,?,?,?,?) ON CONFLICT(ref) DO UPDATE SET "
                             + "content=excluded.content, media_type=excluded.media_type")) {
            ps.setString(1, ref);
            ps.setString(2, caseId);
            ps.setString(3, kind);
            ps.setString(4, content);
            ps.setString(5, mediaType);
            ps.executeUpdate();
            c.commit();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public String rawBody(String ref) {
        try (Connection c = connect();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT content FROM raw_bodies WHERE ref=?")) {
            ps.setString(1, ref);
            try (ResultSet rs = ps.executeQuery()) {
                String body = rs.next() ? rs.getString(1) : null;
                c.commit();
                return body;
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public void archiveLog(String caseId, String op, long at, String detail) {
        try (Connection c = connect();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO archive_log(case_id, op, at, detail) VALUES(?,?,?,?)")) {
            ps.setString(1, caseId);
            ps.setString(2, op);
            ps.setLong(3, at);
            ps.setString(4, detail);
            ps.executeUpdate();
            c.commit();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public void insertEventForImport(String caseId, Event ev) {
        try (Connection c = connect();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT OR REPLACE INTO events(event_id, case_id, branch_id, seq, type, "
                             + "occurred_at, actor, payload, prev_hash, hash) "
                             + "VALUES(?,?,?,?,?,?,?,?,?,?)")) {
            bind(ps, caseId, ev);
            ps.executeUpdate();
            c.commit();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private void bind(PreparedStatement ps, String caseId, Event ev) throws SQLException {
        ps.setString(1, ev.eventId());
        ps.setString(2, caseId);
        ps.setString(3, ev.branchId());
        ps.setLong(4, ev.seq());
        ps.setString(5, ev.type().name());
        ps.setLong(6, ev.occurredAt());
        ps.setString(7, ev.actor());
        ps.setString(8, ev.payload().toString());
        ps.setString(9, ev.prevHash());
        ps.setString(10, ev.hash());
    }

    public List<String> allBranches() {
        try (Connection c = connect();
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT DISTINCT branch_id FROM events ORDER BY branch_id")) {
            List<String> out = new ArrayList<>();
            while (rs.next()) out.add(rs.getString(1));
            c.commit();
            return out;
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private long readHeadSeq(Connection c, String branchId) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT head_seq FROM branch_heads WHERE branch_id=?")) {
            ps.setString(1, branchId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 0L;
            }
        }
    }

    private String readHeadHash(Connection c, String branchId) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT head_hash FROM branch_heads WHERE branch_id=?")) {
            ps.setString(1, branchId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString(1) : null;
            }
        }
    }

    private Event map(ResultSet rs) throws SQLException {
        try {
            ObjectNode payload = (ObjectNode) mapper.readTree(rs.getString("payload"));
            return new Event(
                    rs.getString("event_id"),
                    rs.getLong("seq"),
                    rs.getString("branch_id"),
                    EventType.valueOf(rs.getString("type")),
                    rs.getLong("occurred_at"),
                    rs.getString("actor"),
                    payload,
                    rs.getString("prev_hash"),
                    rs.getString("hash"));
        } catch (Exception e) {
            throw new SQLException(e);
        }
    }

    @Override public void close() { }

    public record UnsavedEvent(String eventId, EventType type, long occurredAt,
                               String actor, ObjectNode payload) { }
}
