package dsc.store;

import dsc.model.Event;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * SQLite-backed append-only event store.
 *
 * <p>Every state transition is one committed transaction: either the whole
 * batch (derived versions + pointer moves) is visible, or the previous
 * version is. A crash (or {@link #crashAfter(String) injected fault}) leaves
 * at most the last, fully committed transaction.
 */
public class EventStore implements AutoCloseable {

    static final int APP_SCHEMA_VERSION = 2;

    private final Connection conn;
    // When non-null, the next COMMIT for this event type is followed by a
    // hard fault, simulating a power loss mid-write.
    private volatile String crashAfter;

    public EventStore(String jdbcUrl) {
        try {
            this.conn = DriverManager.getConnection(jdbcUrl);
            conn.setAutoCommit(false);
            migrate();
        } catch (SQLException e) {
            throw new IllegalStateException("cannot open store: " + jdbcUrl, e);
        }
    }

    public Connection rawConnection() {
        return conn;
    }

    private void migrate() throws SQLException {
        int version = 0;
        try (Statement st = conn.createStatement()) {
            st.execute("create table if not exists schema_version (version integer not null)");
            try (ResultSet rs = st.executeQuery("select coalesce(max(version),0) from schema_version")) {
                rs.next();
                version = rs.getInt(1);
            }
        }
        if (version < 1) {
            try (Statement st = conn.createStatement()) {
                // v1 shipped with actor and payload only; at/basis are added in v2.
                st.execute("create table if not exists events ("
                        + "seq integer primary key autoincrement, "
                        + "id text not null unique, "
                        + "type text not null, "
                        + "object_id text not null, "
                        + "parent_id text, "
                        + "actor text not null, "
                        + "payload_json text not null, "
                        + "at text not null default '', "
                        + "superseded_by text)");
                st.execute("create table if not exists pointers (name text primary key, event_id text not null)");
                st.execute("create table if not exists meta (key text primary key, value text not null)");
                st.execute("insert into schema_version values (1)");
            }
            conn.commit();
        }
        if (version < 2) {
            try (Statement st = conn.createStatement()) {
                st.execute("alter table events add column basis_id text");
                st.execute("update events set basis_id = '' where basis_id is null");
                st.execute("insert into schema_version values (2)");
            }
            conn.commit();
        }
    }

    /** Expose schema version for migration tests. */
    public int schemaVersion() {
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("select coalesce(max(version),0) from schema_version")) {
            rs.next();
            return rs.getInt(1);
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    public long lastSeq() {
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("select coalesce(max(seq),0) from events")) {
            rs.next();
            return rs.getLong(1);
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    /** All events in commit order. */
    public List<Event> readAll() {
        List<Event> out = new ArrayList<>();
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(
                     "select seq,id,type,object_id,parent_id,basis_id,actor,payload_json,at,superseded_by "
                             + "from events order by seq")) {
            while (rs.next()) {
                out.add(map(rs));
            }
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
        return out;
    }

    public Event findEvent(String id) {
        try (PreparedStatement ps = conn.prepareStatement(
                "select seq,id,type,object_id,parent_id,basis_id,actor,payload_json,at,superseded_by "
                        + "from events where id = ?")) {
            ps.setString(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? map(rs) : null;
            }
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    public boolean exists(String id) {
        return findEvent(id) != null;
    }

    /** Input record for an append. */
    public record Append(String id, String type, String objectId, String parentId, String basisId,
                         String actor, String at, String payloadJson) {
    }

    /**
     * Append events plus pointer moves atomically.
     *
     * @param expectedLastSeq optimistic-concurrency guard: callers editing from
     *                        the same base revision pass the seq they read.
     * @throws ConflictException if another writer committed first.
     */
    public synchronized void commit(long expectedLastSeq, List<Append> appends,
                                    List<String[]> pointerMoves) {
        tx(c -> {
            long current = lastSeq();
            if (current != expectedLastSeq) {
                throw new ConflictException(expectedLastSeq, current);
            }
            String sql = "insert into events(id,type,object_id,parent_id,basis_id,actor,payload_json,at) "
                    + "values(?,?,?,?,?,?,?,?)";
            try (PreparedStatement ps = c.prepareStatement(sql)) {
                for (Append a : appends) {
                    if (findEvent(a.id) != null) {
                        // Deterministic derived id already present: idempotent replay.
                        continue;
                    }
                    ps.setString(1, a.id);
                    ps.setString(2, a.type);
                    ps.setString(3, a.objectId);
                    ps.setString(4, a.parentId);
                    ps.setString(5, a.basisId == null ? "" : a.basisId);
                    ps.setString(6, a.actor);
                    ps.setString(7, a.payloadJson);
                    ps.setString(8, a.at);
                    ps.executeUpdate();
                    if (crashAfter != null && a.type.equals(crashAfter)) {
                        crashAfter = null;
                        // Simulated power loss: connection dies before commit.
                        try {
                            c.close();
                        } catch (SQLException ignored) {
                        }
                        throw new CrashException();
                    }
                }
            }
            try (PreparedStatement ps = c.prepareStatement(
                    "insert into pointers(name,event_id) values(?,?) "
                            + "on conflict(name) do update set event_id=excluded.event_id")) {
                for (String[] mv : pointerMoves) {
                    ps.setString(1, mv[0]);
                    ps.setString(2, mv[1]);
                    ps.executeUpdate();
                }
            }
            return null;
        });
    }

    /** Mark an old event superseded (history stays readable; used for rejects). */
    public void markSuperseded(String eventId, String byEventId) {
        tx(c -> {
            try (PreparedStatement ps = c.prepareStatement(
                    "update events set superseded_by = ? where id = ?")) {
                ps.setString(1, byEventId);
                ps.setString(2, eventId);
                ps.executeUpdate();
            }
            return null;
        });
    }

    public String pointer(String name) {
        try (PreparedStatement ps = conn.prepareStatement("select event_id from pointers where name=?")) {
            ps.setString(1, name);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString(1) : null;
            }
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    public void putMeta(String key, String value) {
        tx(c -> {
            try (PreparedStatement ps = c.prepareStatement(
                    "insert into meta(key,value) values(?,?) on conflict(key) do update set value=excluded.value")) {
                ps.setString(1, key);
                ps.setString(2, value);
                ps.executeUpdate();
            }
            return null;
        });
    }

    public String getMeta(String key) {
        try (PreparedStatement ps = conn.prepareStatement("select value from meta where key=?")) {
            ps.setString(1, key);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString(1) : null;
            }
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Test hook: kill the connection mid-transaction when this event type is written. */
    public void crashAfter(String eventType) {
        this.crashAfter = eventType;
    }

    /** Reopen a crashed database file; SQLite recovers the hot journal automatically. */
    public static EventStore reopen(String jdbcUrl) {
        return new EventStore(jdbcUrl);
    }

    private <T> T tx(Function<Connection, T> body) {
        try {
            T result = body.apply(conn);
            conn.commit();
            return result;
        } catch (ConflictException | CrashException e) {
            rollbackQuiet();
            throw e;
        } catch (SQLException e) {
            rollbackQuiet();
            if ("23000".equals(e.getSQLState()) || (e.getMessage() != null && e.getMessage().contains("UNIQUE"))) {
                throw e;
            }
            throw new IllegalStateException(e);
        }
    }

    private void rollbackQuiet() {
        try {
            if (!conn.isClosed()) {
                conn.rollback();
            }
        } catch (SQLException ignored) {
        }
    }

    private Event map(ResultSet rs) throws SQLException {
        return new Event(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4),
                rs.getString(5), rs.getString(6), rs.getString(7), rs.getString(8),
                rs.getString(9), rs.getString(10));
    }

    @Override
    public void close() {
        try {
            conn.close();
        } catch (SQLException ignored) {
        }
    }

    public static class ConflictException extends RuntimeException {
        public final long expected;
        public final long actual;

        public ConflictException(long expected, long actual) {
            super("optimistic lock conflict: base seq " + expected + ", head seq " + actual);
            this.expected = expected;
            this.actual = actual;
        }
    }

    public static class CrashException extends RuntimeException {
        public CrashException() {
            super("simulated write interruption before commit");
        }
    }
}
