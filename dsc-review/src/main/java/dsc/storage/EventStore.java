package dsc.storage;

import com.fasterxml.jackson.core.JsonProcessingException;
import dsc.events.Event;
import dsc.events.EventType;

import java.sql.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Append-only event log with optimistic concurrency.
 *
 * <p>A command ({@link #append} or {@link #commitBatch}) runs inside a single
 * SQLite transaction. The write lock is taken first; the base-seq check makes
 * concurrent edits from the same version fail with {@link ConflictException},
 * which is resumable (rebase onto the newer log and retry). Either every event
 * of a batch is visible afterwards or none is.
 */
public final class EventStore {

    private final Database db;
    private final Object writeLock = new Object();

    public EventStore(Database db) { this.db = db; }

    public List<Event> readAll(String historyId) {
        String sql = "SELECT seq, history_id, entity_id, type, schema_ver, created_at, actor, comment, "
                + "payload_json, judgments_json, base_seq, deps_json, supersedes FROM events "
                + "WHERE history_id=? ORDER BY seq ASC";
        try (var ps = db.connection().prepareStatement(sql)) {
            ps.setString(1, historyId);
            try (var rs = ps.executeQuery()) {
                List<Event> out = new ArrayList<>();
                while (rs.next()) out.add(row(rs));
                return out;
            }
        } catch (SQLException e) {
            throw new StorageException("read events failed", e);
        }
    }

    public long currentSeq(String historyId) {
        try (var ps = db.connection().prepareStatement(
                "SELECT COALESCE(MAX(seq), 0) FROM events WHERE history_id=?")) {
            ps.setString(1, historyId);
            try (var rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 0;
            }
        } catch (SQLException e) {
            throw new StorageException("currentSeq failed", e);
        }
    }

    public Event append(String historyId, Event e, long baseSeq) {
        return commitBatch(historyId, List.of(e), baseSeq).get(0);
    }

    /**
     * Atomic batch commit. After return either all events (and pointer
     * updates embedded in the events' semantics) are durable, or nothing.
     *
     * @param crashBeforeCommit test hook: throw after all INSERTs but before COMMIT
     */
    public List<Event> commitBatch(String historyId, List<Event> events, long baseSeq) {
        return commitBatch(historyId, events, baseSeq, false);
    }

    public List<Event> commitBatch(String historyId, List<Event> events, long baseSeq,
                                   boolean crashBeforeCommit) {
        synchronized (writeLock) {
            Connection cx = db.connection();
            try {
                cx.setAutoCommit(false);
                long head = currentSeq(historyId);
                if (baseSeq != head) {
                    cx.rollback();
                    cx.setAutoCommit(true);
                    throw new ConflictException(baseSeq, head);
                }
                List<Event> stored = new ArrayList<>();
                for (Event e : events) {
                    e.setSeq(head + stored.size() + 1);
                    e.setHistoryId(historyId);
                    e.setSchemaVersion(Schema.CURRENT_VERSION);
                    if (e.getAt() == null) e.setAt(Instant.now());
                    insert(cx, e);
                    stored.add(e);
                }
                if (crashBeforeCommit) {
                    // Simulated power loss: WAL never reaches COMMIT.
                    cx.rollback();
                    cx.setAutoCommit(true);
                    throw new StorageException("simulated crash before commit");
                }
                cx.commit();
                cx.setAutoCommit(true);
                return stored;
            } catch (SQLException e) {
                safeRollback(cx);
                throw new StorageException("commit failed", e);
            }
        }
    }

    private void safeRollback(Connection cx) {
        try { cx.rollback(); cx.setAutoCommit(true); } catch (SQLException ignored) {}
    }

    private void insert(Connection cx, Event e) throws SQLException {
        String sql = "INSERT INTO events(seq, history_id, entity_id, type, schema_ver, created_at, actor, "
                + "comment, payload_json, judgments_json, base_seq, deps_json, supersedes) "
                + "VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?)";
        try (var ps = cx.prepareStatement(sql)) {
            ps.setLong(1, e.getSeq());
            ps.setString(2, e.getHistoryId());
            ps.setString(3, e.getEntityId());
            ps.setString(4, e.getType().name());
            ps.setInt(5, e.getSchemaVersion());
            ps.setString(6, e.getAt() == null ? Instant.now().toString() : e.getAt().toString());
            ps.setString(7, e.getActor());
            ps.setString(8, e.getComment());
            ps.setString(9, Json.MAPPER.writeValueAsString(e.getPayload()));
            ps.setString(10, e.getJudgments() == null ? null : Json.MAPPER.writeValueAsString(e.getJudgments()));
            if (e.getBaseSeq() == null) ps.setNull(11, Types.INTEGER);
            else ps.setLong(11, e.getBaseSeq());
            ps.setString(12, e.getDependencies() == null ? null : Json.MAPPER.writeValueAsString(e.getDependencies()));
            ps.setString(13, e.getSupersedes());
            ps.executeUpdate();
        } catch (JsonProcessingException j) {
            throw new SQLException("payload serialization failed", j);
        }
    }

    private Event row(ResultSet rs) throws SQLException {
        try {
            Event e = new Event();
            e.setSeq(rs.getLong("seq"));
            e.setHistoryId(rs.getString("history_id"));
            e.setEntityId(rs.getString("entity_id"));
            e.setType(EventTypeValue(rs.getString("type")));
            e.setSchemaVersion(rs.getInt("schema_ver"));
            e.setAt(Instant.parse(rs.getString("created_at")));
            e.setActor(rs.getString("actor"));
            e.setComment(rs.getString("comment"));
            String payload = rs.getString("payload_json");
            e.setPayload(payload == null || payload.equals("null")
                    ? null : Json.MAPPER.readTree(payload));
            String judg = rs.getString("judgments_json");
            if (judg != null && !judg.equals("null")) {
                e.setJudgments(Json.MAPPER.readValue(judg,
                        Json.MAPPER.getTypeFactory().constructCollectionType(List.class, dsc.events.Judgment.class)));
            }
            long base = rs.getLong("base_seq");
            e.setBaseSeq(rs.wasNull() ? null : base);
            String deps = rs.getString("deps_json");
            if (deps != null && !deps.equals("null")) {
                e.setDependencies(Json.MAPPER.readValue(deps,
                        Json.MAPPER.getTypeFactory().constructCollectionType(List.class, String.class)));
            }
            e.setSupersedes(rs.getString("supersedes"));
            return e;
        } catch (JsonProcessingException j) {
            throw new SQLException("row deserialization failed", j);
        }
    }

    private EventType EventTypeValue(String s) {
        try {
            return dsc.events.EventType.valueOf(s);
        } catch (IllegalArgumentException ex) {
            // Forward compatibility: an event written by a newer app is read as a marker,
            // replay stops with an explicit error rather than silently dropping it.
            throw new StorageException("unknown event type in log: " + s);
        }
    }

    public void openHistory(String historyId, String name, Instant openedAt) {
        synchronized (writeLock) {
            try (var ps = db.connection().prepareStatement(
                    "INSERT INTO histories(history_id, name, opened_at) VALUES(?,?,?) "
                            + "ON CONFLICT(history_id) DO NOTHING")) {
                ps.setString(1, historyId);
                ps.setString(2, name);
                ps.setString(3, openedAt.toString());
                ps.executeUpdate();
            } catch (SQLException e) {
                throw new StorageException("openHistory failed", e);
            }
        }
    }
}
