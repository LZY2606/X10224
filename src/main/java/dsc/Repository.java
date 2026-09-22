package dsc;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Versioned repository. Every derivation is an immutable row in `derived`;
 * a `pointer` row names the current version. Commits are single SQLite
 * transactions: after an interrupted write a restart sees either the old
 * pointer or the fully written new version, never a partial state.
 */
public class Repository {
    final Database db;

    /** test hook: simulate a crash between version insert and pointer switch */
    public boolean crashBeforePointerSwitch = false;

    public Repository(Database db) {
        this.db = db;
    }

    public static class Conflict extends RuntimeException {
        public final long currentSeq;
        public final long baseSeq;
        public Conflict(long base, long current) {
            super("stale base: client based on seq " + base + " but store is at " + current);
            this.baseSeq = base;
            this.currentSeq = current;
        }
    }

    public long currentSeq() {
        try (Statement st = db.conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT COALESCE(MAX(seq),0) FROM event_log")) {
            return rs.next() ? rs.getLong(1) : 0;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public void checkBase(long baseSeq) {
        long cur = currentSeq();
        if (baseSeq >= 0 && baseSeq != cur) throw new Conflict(baseSeq, cur);
    }

    // ---------- generic row access ----------

    public void insert(String table, Map<String, Object> fields) {
        String cols = String.join(",", fields.keySet());
        String marks = String.join(",", fields.keySet().stream().map(k -> "?").toList());
        try (PreparedStatement ps = db.conn.prepareStatement(
                "INSERT INTO " + table + "(" + cols + ") VALUES (" + marks + ")")) {
            int i = 1;
            for (Object v : fields.values()) {
                if (v == null) ps.setObject(i++, null);
                else if (v instanceof Number n) ps.setObject(i++, n);
                else ps.setString(i++, String.valueOf(v));
            }
            ps.executeUpdate();
        } catch (Exception e) {
            throw new RuntimeException("insert into " + table + " failed", e);
        }
    }

    public List<ObjectNode> query(String sql, Object... args) {
        List<ObjectNode> out = new ArrayList<>();
        try (PreparedStatement ps = db.conn.prepareStatement(sql)) {
            for (int i = 0; i < args.length; i++) ps.setObject(i + 1, args[i]);
            ResultSet rs = ps.executeQuery();
            var md = rs.getMetaData();
            while (rs.next()) {
                ObjectNode row = Json.obj();
                for (int c = 1; c <= md.getColumnCount(); c++) {
                    String name = md.getColumnLabel(c);
                    Object v = rs.getObject(c);
                    if (v == null) row.putNull(name);
                    else if (v instanceof Number n) {
                        if (v instanceof Double || v instanceof Float) row.put(name, ((Number) v).doubleValue());
                        else row.put(name, n.longValue());
                    } else row.put(name, String.valueOf(v));
                }
                out.add(row);
            }
        } catch (Exception e) {
            throw new RuntimeException("query failed: " + sql, e);
        }
        return out;
    }

    public ObjectNode one(String sql, Object... args) {
        List<ObjectNode> r = query(sql, args);
        return r.isEmpty() ? null : r.get(0);
    }

    // ---------- versioned derived objects ----------

    public record DerivedWrite(ObjectNode row, String pointerKey) {}

    /** a row in a source archive table written inside the same transaction */
    public record SourceRow(String table, Map<String, Object> fields) {}

    /**
     * Atomic commit: append event, insert new derived versions, switch
     * pointers. Either everything lands or nothing does.
     */
    public synchronized long commit(String eventId, String eventType, String actor,
                                    JsonNode eventPayload, long baseSeq,
                                    List<DerivedWrite> writes, List<ObjectNode> diagnostics) {
        return commit(eventId, eventType, actor, eventPayload, baseSeq, List.of(), writes, diagnostics);
    }

    public synchronized long commit(String eventId, String eventType, String actor,
                                    JsonNode eventPayload, long baseSeq,
                                    List<SourceRow> sourceRows,
                                    List<DerivedWrite> writes, List<ObjectNode> diagnostics) {
        checkBase(baseSeq);
        try {
            db.conn.setAutoCommit(false);
            long seq;
            try (PreparedStatement ps = db.conn.prepareStatement(
                    "INSERT INTO event_log(id,type,actor,payload_json,base_seq) VALUES (?,?,?,?,?)",
                    Statement.RETURN_GENERATED_KEYS)) {
                ps.setString(1, eventId);
                ps.setString(2, eventType);
                ps.setString(3, actor);
                ps.setString(4, Json.write(eventPayload));
                ps.setLong(5, baseSeq);
                ps.executeUpdate();
                ResultSet keys = ps.getGeneratedKeys();
                keys.next();
                seq = keys.getLong(1);
            }
            for (DerivedWrite w : writes) {
                ObjectNode row = w.row();
                row.put("created_seq", seq);
                try (PreparedStatement ps = db.conn.prepareStatement(
                        "INSERT INTO derived(id,kind,subject_id,version,status,deps_json,payload_json,provenance_json,created_seq)"
                                + " VALUES (?,?,?,?,?,?,?,?,?)")) {
                    ps.setString(1, row.get("id").asText());
                    ps.setString(2, row.get("kind").asText());
                    ps.setString(3, row.get("subject_id").asText());
                    ps.setInt(4, row.get("version").asInt());
                    ps.setString(5, row.get("status").asText());
                    ps.setString(6, row.get("deps_json").asText());
                    ps.setString(7, row.get("payload_json").asText());
                    ps.setString(8, row.hasNonNull("provenance_json") ? row.get("provenance_json").asText() : null);
                    ps.setLong(9, seq);
                    ps.executeUpdate();
                }
            }
            for (SourceRow sr : sourceRows) {
                insert(sr.table(), sr.fields());
            }
            if (crashBeforePointerSwitch) {
                // simulate power loss after versions written, before pointer switch
                db.conn.rollback();
                db.conn.setAutoCommit(true);
                return -1;
            }
            for (DerivedWrite w : writes) {
                if (w.pointerKey() == null) continue;
                try (PreparedStatement ps = db.conn.prepareStatement(
                        "INSERT INTO pointer(pkey,derived_id,version) VALUES (?,?,?) "
                                + "ON CONFLICT(pkey) DO UPDATE SET derived_id=excluded.derived_id, version=excluded.version")) {
                    ps.setString(1, w.pointerKey());
                    ps.setString(2, w.row().get("id").asText());
                    ps.setInt(3, w.row().get("version").asInt());
                    ps.executeUpdate();
                }
                try (PreparedStatement ps = db.conn.prepareStatement(
                        "INSERT INTO pointer_history(pkey,derived_id,version,seq) VALUES (?,?,?,?)")) {
                    ps.setString(1, w.pointerKey());
                    ps.setString(2, w.row().get("id").asText());
                    ps.setInt(3, w.row().get("version").asInt());
                    ps.setLong(4, seq);
                    ps.executeUpdate();
                }
            }
            if (diagnostics != null) {
                for (ObjectNode d : diagnostics) {
                    try (PreparedStatement ps = db.conn.prepareStatement(
                            "INSERT OR REPLACE INTO diagnostic(id,code,severity,subject_id,detail_json,created_seq) VALUES (?,?,?,?,?,?)")) {
                        ps.setString(1, d.get("id").asText());
                        ps.setString(2, d.get("code").asText());
                        ps.setString(3, d.get("severity").asText());
                        ps.setString(4, d.get("subject_id").asText());
                        ps.setString(5, d.get("detail_json").asText());
                        ps.setLong(6, seq);
                        ps.executeUpdate();
                    }
                }
            }
            db.conn.commit();
            db.conn.setAutoCommit(true);
            return seq;
        } catch (RuntimeException e) {
            try { db.conn.rollback(); db.conn.setAutoCommit(true); } catch (Exception ignored) {}
            throw e;
        } catch (Exception e) {
            try { db.conn.rollback(); db.conn.setAutoCommit(true); } catch (Exception ignored) {}
            throw new RuntimeException(e);
        }
    }

    /** plain append for source-archive events that carry no derived writes */
    public long appendEvent(String id, String type, String actor, JsonNode payload, long baseSeq) {
        return commit(id, type, actor, payload, baseSeq, List.of(), List.of());
    }

    public ObjectNode currentDerived(String pointerKey) {
        ObjectNode p = one("SELECT derived_id, version FROM pointer WHERE pkey=?", pointerKey);
        if (p == null) return null;
        ObjectNode d = one("SELECT * FROM derived WHERE id=? AND version=?",
                p.get("derived_id").asText(), p.get("version").asInt());
        if (d != null) d.put("pointer_key", pointerKey);
        return d;
    }

    public List<ObjectNode> allVersions(String derivedId) {
        return query("SELECT * FROM derived WHERE id=? ORDER BY version", derivedId);
    }

    public void markStatus(String derivedId, int version, String status) {
        try (PreparedStatement ps = db.conn.prepareStatement(
                "UPDATE derived SET status=? WHERE id=? AND version=?")) {
            ps.setString(1, status);
            ps.setString(2, derivedId);
            ps.setInt(3, version);
            ps.executeUpdate();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /** pointer state as of a historical event sequence number */
    public Map<String, ObjectNode> pointersAt(long seq) {
        Map<String, ObjectNode> out = new LinkedHashMap<>();
        for (ObjectNode h : query(
                "SELECT pkey, derived_id, version, MAX(seq) AS s FROM pointer_history WHERE seq<=? GROUP BY pkey", seq)) {
            ObjectNode d = one("SELECT * FROM derived WHERE id=? AND version=?",
                    h.get("derived_id").asText(), h.get("version").asInt());
            if (d != null) out.put(h.get("pkey").asText(), d);
        }
        return out;
    }

    // ---------- export / import ----------

    static final String[] TABLES = {
            "sample", "instrument_output", "segment", "scan_rate", "atmosphere",
            "mass_version", "calibration", "tangent_selection",
            "derived", "pointer", "pointer_history", "event_log", "diagnostic"
    };

    public ObjectNode exportAll() {
        ObjectNode out = Json.obj();
        out.put("format", "dsc-archive/1");
        out.put("schema_version", Database.SCHEMA_VERSION);
        ObjectNode tables = out.putObject("tables");
        for (String t : TABLES) {
            ArrayNode rows = tables.putArray(t);
            String order = t.equals("event_log") ? " ORDER BY seq"
                    : t.equals("derived") ? " ORDER BY id, version"
                    : t.equals("pointer_history") ? " ORDER BY seq" : "";
            for (ObjectNode row : query("SELECT * FROM " + t + order)) rows.add(row);
        }
        return out;
    }

    /** wipe and re-import; ids, event order (seq) and provenance are preserved verbatim */
    public void importAll(ObjectNode dump) {
        if (!"dsc-archive/1".equals(dump.path("format").asText())) {
            throw new IllegalArgumentException("unsupported archive format");
        }
        try {
            db.conn.setAutoCommit(false);
            for (String t : TABLES) db.exec("DELETE FROM " + t);
            JsonNode tables = dump.get("tables");
            for (String t : TABLES) {
                JsonNode rows = tables.get(t);
                if (rows == null) continue;
                for (JsonNode row : rows) {
                    Map<String, Object> fields = new LinkedHashMap<>();
                    row.fields().forEachRemaining(e -> {
                        JsonNode v = e.getValue();
                        if (v == null || v.isNull()) fields.put(e.getKey(), null);
                        else if (v.isNumber()) fields.put(e.getKey(), v.numberValue());
                        else fields.put(e.getKey(), v.asText());
                    });
                    insert(t, fields);
                }
            }
            db.exec("DELETE FROM sqlite_sequence WHERE name='event_log'");
            long maxSeq = currentSeq();
            db.exec("INSERT INTO sqlite_sequence(name, seq) VALUES ('event_log', " + maxSeq + ")");
            db.conn.commit();
            db.conn.setAutoCommit(true);
        } catch (Exception e) {
            try { db.conn.rollback(); db.conn.setAutoCommit(true); } catch (Exception ignored) {}
            throw new RuntimeException("import failed, database unchanged", e);
        }
    }
}
