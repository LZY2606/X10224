package dsc.store;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dsc.domain.Event;
import dsc.domain.EventType;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * SQLite-backed event store. A state change is one transaction that inserts
 * the event(s), derived rows and updates pointers together. If the process dies
 * mid-write, SQLite rolls the transaction back: on restart readers see either
 * the old pointer/version or the complete new one, never a torn write.
 */
public final class EventStore implements AutoCloseable {

    private final String url;
    private final Connection conn;
    private final ObjectMapper mapper = new ObjectMapper();

    public EventStore(String path) {
        this.url = "jdbc:sqlite:" + path;
        try {
            this.conn = DriverManager.getConnection(url);
            this.conn.setAutoCommit(false);
            try (Statement st = conn.createStatement()) {
                st.execute("PRAGMA journal_mode=WAL");
                st.execute("PRAGMA foreign_keys=ON");
                st.execute("PRAGMA busy_timeout=5000");
            }
            conn.commit();
            init();
        } catch (SQLException e) {
            throw new IllegalStateException("cannot open store " + path, e);
        }
    }

    /** Test/helper constructor with a pre-existing connection (e.g. in-memory). */
    EventStore(Connection connection) {
        this.url = null;
        this.conn = connection;
        try {
            this.conn.setAutoCommit(false);
            init();
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    private void init() throws SQLException {
        int version = schemaVersion();
        if (version == 0) {
            try (Statement st = conn.createStatement()) {
                for (String sql : Schema.DDL_V1.split(";")) {
                    if (!sql.isBlank()) {
                        st.execute(sql);
                    }
                }
            }
            setMeta("schema_version", String.valueOf(Schema.CURRENT_VERSION));
            conn.commit();
        } else if (version < Schema.CURRENT_VERSION) {
            migrate(version);
        }
    }

    private int schemaVersion() throws SQLException {
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(
                     "SELECT name FROM sqlite_master WHERE type='table' AND name='meta'")) {
            if (!rs.next()) {
                return 0;
            }
        }
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT value FROM meta WHERE key='schema_version'")) {
            return rs.next() ? Integer.parseInt(rs.getString(1)) : 1;
        }
    }

    private void migrate(int from) throws SQLException {
        // v1 -> v2: derived baselines/transitions are reconstructed from events;
        // add a provenance watermark table recording the migration itself.
        try (Statement st = conn.createStatement()) {
            if (from < 2) {
                st.execute("CREATE TABLE IF NOT EXISTS migrations_log ("
                        + "applied_version INTEGER PRIMARY KEY, ts TEXT NOT NULL, note TEXT)");
                st.execute("INSERT OR REPLACE INTO migrations_log VALUES (2, "
                        + "strftime('%Y-%m-%dT%H:%M:%fZ','now'), "
                        + "'v1->v2: derivations rebuilt from event log')");
            }
        }
        setMeta("schema_version", String.valueOf(Schema.CURRENT_VERSION));
        conn.commit();
    }

    private void setMeta(String key, String value) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO meta(key,value) VALUES(?,?) "
                        + "ON CONFLICT(key) DO UPDATE SET value=excluded.value")) {
            ps.setString(1, key);
            ps.setString(2, value);
            ps.executeUpdate();
        }
    }

    public String meta(String key) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("SELECT value FROM meta WHERE key=?")) {
            ps.setString(1, key);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString(1) : null;
            }
        }
    }

    /**
     * Append events and optional side-table writes inside one transaction.
     *
     * @param writes    callback performing additional inserts using the same connection
     * @param expectedHeadSeq optimistic-concurrency base; call fails if head moved
     * @param branch    branch to append onto
     */
    public synchronized AppendResult append(String branch, long expectedHeadSeq,
                                            List<EventDraft> drafts, WriteCallback writes) {
        try {
            long head = headSeq(branch);
            if (expectedHeadSeq >= 0 && head != expectedHeadSeq) {
                conn.rollback();
                return AppendResult.conflict(head);
            }
            List<Event> appended = new ArrayList<>();
            for (EventDraft d : drafts) {
                long seq = nextSeq();
                String payloadJson = mapper.writeValueAsString(d.payload() == null ? Map.of() : d.payload());
                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO events(id,type,actor,ts,branch,parent_event_id,payload_json) "
                                + "VALUES(?,?,?,?,?,?,?)")) {
                    ps.setString(1, d.id());
                    ps.setString(2, d.type().name());
                    ps.setString(3, d.actor() == null ? "system" : d.actor());
                    ps.setString(4, d.ts());
                    ps.setString(5, branch);
                    ps.setString(6, d.parentEventId());
                    ps.setString(7, payloadJson);
                    ps.executeUpdate();
                }
                appended.add(new Event(seq, d.id(), d.type(), d.actor(), d.ts(), branch,
                        d.payload(), d.parentEventId()));
            }
            if (writes != null) {
                writes.write(conn);
            }
            long newHead = head;
            try (Statement st = conn.createStatement();
                 ResultSet rs = st.executeQuery("SELECT COALESCE(MAX(seq),0) FROM events WHERE branch='"
                         + escape(branch) + "'")) {
                rs.next();
                newHead = rs.getLong(1);
            }
            upsertBranch(branch, newHead);
            conn.commit();
            return AppendResult.ok(appended, newHead);
        } catch (Exception e) {
            try {
                conn.rollback();
            } catch (SQLException ignored) {
            }
            if (e instanceof ConflictException ce) {
                return AppendResult.conflict(ce.currentHead);
            }
            throw new IllegalStateException("append failed and was rolled back", e);
        }
    }

    /** Simulate a process crash at the physical layer during a write. */
    public void crashDuringWrite(Runnable partialWrite) {
        try {
            partialWrite.run();
            // No commit: discard everything like a killed process.
            conn.rollback();
        } catch (RuntimeException e) {
            try {
                conn.rollback();
            } catch (SQLException ignored) {
            }
            throw e;
        }
    }

    private long nextSeq() throws SQLException {
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT COALESCE(MAX(seq),0)+1 FROM events")) {
            rs.next();
            return rs.getLong(1);
        }
    }

    private void upsertBranch(String branch, long head) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO branches(name,head_seq) VALUES(?,?) "
                        + "ON CONFLICT(name) DO UPDATE SET head_seq=excluded.head_seq")) {
            ps.setString(1, branch);
            ps.setLong(2, head);
            ps.executeUpdate();
        }
    }

    public long headSeq(String branch) {
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(
                     "SELECT COALESCE(MAX(seq),0) FROM events WHERE branch='" + escape(branch) + "'")) {
            rs.next();
            return rs.getLong(1);
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    public List<Event> readAll(String branch) {
        List<Event> out = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT seq,id,type,actor,ts,branch,parent_event_id,payload_json FROM events "
                        + "WHERE branch=? ORDER BY seq")) {
            ps.setString(1, branch);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(mapEvent(rs));
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
        return out;
    }

    public List<Event> readAllUpTo(String branch, long upToSeq) {
        List<Event> out = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT seq,id,type,actor,ts,branch,parent_event_id,payload_json FROM events "
                        + "WHERE branch=? AND seq<=? ORDER BY seq")) {
            ps.setString(1, branch);
            ps.setLong(2, upToSeq);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(mapEvent(rs));
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
        return out;
    }

    private Event mapEvent(ResultSet rs) throws SQLException {
        try {
            String json = rs.getString("payload_json");
            Map<String, String> payload = json == null || json.isBlank()
                    ? new LinkedHashMap<>()
                    : mapper.readValue(json, new TypeReference<LinkedHashMap<String, String>>() {
                    });
            return new Event(rs.getLong("seq"), rs.getString("id"),
                    EventType.valueOf(rs.getString("type")),
                    rs.getString("actor"), rs.getString("ts"), rs.getString("branch"),
                    payload, rs.getString("parent_event_id"));
        } catch (Exception e) {
            throw new SQLException("bad event row", e);
        }
    }

    public void setPointer(String name, String eventId, long seq, int version) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO pointers(name,event_id,seq,version) VALUES(?,?,?,?) "
                        + "ON CONFLICT(name) DO UPDATE SET event_id=excluded.event_id, "
                        + "seq=excluded.seq, version=excluded.version")) {
            ps.setString(1, name);
            ps.setString(2, eventId);
            ps.setLong(3, seq);
            ps.setInt(4, version);
            ps.executeUpdate();
        }
    }

    public java.util.Optional<Pointer> pointer(String name) {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT event_id,seq,version FROM pointers WHERE name=?")) {
            ps.setString(1, name);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return java.util.Optional.of(
                            new Pointer(rs.getString(1), rs.getLong(2), rs.getInt(3)));
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
        return java.util.Optional.empty();
    }

    public Map<String, Pointer> pointers() {
        Map<String, Pointer> out = new LinkedHashMap<>();
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT name,event_id,seq,version FROM pointers")) {
            while (rs.next()) {
                out.put(rs.getString(1), new Pointer(rs.getString(2), rs.getLong(3), rs.getInt(4)));
            }
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
        return out;
    }

    private static String escape(String s) {
        return s.replace("'", "''");
    }

    public Connection connection() {
        return conn;
    }

    @Override
    public void close() {
        try {
            conn.close();
        } catch (SQLException ignored) {
        }
    }

    public record Pointer(String eventId, long seq, int version) {
    }

    @FunctionalInterface
    public interface WriteCallback {
        void write(Connection conn) throws SQLException;
    }

    public record EventDraft(String id, EventType type, String actor, String ts,
                             String parentEventId, Map<String, String> payload) {
        public static EventDraft of(String id, EventType type, String actor, String ts,
                                    Map<String, String> payload) {
            return new EventDraft(id, type, actor, ts, null, payload);
        }
    }

    public static class ConflictException extends RuntimeException {
        final long currentHead;

        public ConflictException(long currentHead) {
            super("optimistic lock conflict, current head=" + currentHead);
            this.currentHead = currentHead;
        }
    }

    public record AppendResult(boolean ok, List<Event> events, long headSeq, Long conflictHead) {
        static AppendResult ok(List<Event> events, long head) {
            return new AppendResult(true, events, head, null);
        }

        static AppendResult conflict(long head) {
            return new AppendResult(false, List.of(), head, head);
        }
    }
}
