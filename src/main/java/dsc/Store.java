package dsc;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Versioned store. Raw inputs (instrument output, mass, scan rate, atmosphere,
 * calibration, user tangents) are archived separately; every derivation is an
 * independent version and the current pointer only switches after a complete
 * success. Concurrent edits from the same base version raise resolvable
 * conflicts rows; nothing is overwritten irreversibly.
 */
public class Store {
    public final Db db;

    public Store(Db db) {
        this.db = db;
    }

    // ---------- ids ----------
    public String newId(String prefix) {
        String key = "seq:" + prefix;
        String cur = db.meta(key);
        int n = cur == null ? 1 : Integer.parseInt(cur) + 1;
        db.setMeta(key, String.valueOf(n));
        return prefix + "-" + String.format("%04d", n);
    }

    // ---------- raw inputs ----------
    public void addInstrumentOutput(String id, String sample, String filename,
                                    String headerJson, String raw) {
        exec("INSERT INTO instrument_output(id,seq,sample,filename,header_json,raw,imported_at)"
                        + " VALUES(?,?,?,?,?,?,?)",
                id, nextSeq("instrument_output"), sample, filename, headerJson, raw,
                Instant.now().toString());
    }

    public void addMass(String id, String sample, double value, String unit,
                        String raw, String note, String supersedes) {
        exec("INSERT INTO mass_version(id,seq,sample,value,unit,raw,note,supersedes)"
                        + " VALUES(?,?,?,?,?,?,?,?)",
                id, nextSeq("mass_version"), sample, value, unit, raw, note, supersedes);
    }

    public void addCalibration(String id, String sample, String raw, double coeff, double uncertainty) {
        exec("INSERT INTO calibration(id,seq,sample,raw,coeff,uncertainty) VALUES(?,?,?,?,?,?)",
                id, nextSeq("calibration"), sample, raw, coeff, uncertainty);
    }

    public Map<String, Object> currentMass(String sample) {
        List<Map<String, Object>> all = query("SELECT * FROM mass_version WHERE sample=? ORDER BY seq DESC", sample);
        return all.isEmpty() ? null : all.get(0);
    }

    public Map<String, Object> currentCalibration(String sample) {
        List<Map<String, Object>> all = query("SELECT * FROM calibration WHERE sample=? ORDER BY seq DESC", sample);
        return all.isEmpty() ? null : all.get(0);
    }

    // ---------- tangents (optimistic concurrency) ----------
    public static class ConflictException extends RuntimeException {
        public final String conflictId;

        public ConflictException(String conflictId, String msg) {
            super(msg);
            this.conflictId = conflictId;
        }
    }

    /** Save a tangent interval. baseVersion must match the current version. */
    public String saveTangent(String segmentId, String pairId, String side,
                              double tLo, double tHi, String author, int baseVersion) {
        Map<String, Object> cur = currentTangent(pairId, side);
        int curVersion = cur == null ? 0 : ((Number) cur.get("version")).intValue();
        if (curVersion != baseVersion) {
            ObjectNode mine = Json.obj();
            mine.put("segment_id", segmentId).put("pair_id", pairId).put("side", side)
                    .put("t_lo", tLo).put("t_hi", tHi).put("author", author);
            mine.put("version", baseVersion + 1);
            ObjectNode theirs = cur == null ? Json.obj() : Json.from(cur);
            String cid = newId("cfl");
            exec("INSERT INTO conflict(id,seq,entity,entity_id,base_version,mine_json,theirs_json,status,resolution)"
                            + " VALUES(?,?,?,?,?,?,?,?,?)",
                    cid, nextSeq("conflict"), "tangent", pairId + ":" + side, baseVersion,
                    Json.write(mine), Json.write(theirs), "open", null);
            return "CONFLICT:" + cid;
        }
        if (cur != null) {
            exec("UPDATE tangent SET status='superseded' WHERE id=?", cur.get("id"));
        }
        String id = newId("tan");
        exec("INSERT INTO tangent(id,seq,segment_id,pair_id,side,t_lo,t_hi,author,version,status)"
                        + " VALUES(?,?,?,?,?,?,?,?,?,?)",
                id, nextSeq("tangent"), segmentId, pairId, side, tLo, tHi, author,
                curVersion + 1, "current");
        return id;
    }

    public Map<String, Object> currentTangent(String pairId, String side) {
        List<Map<String, Object>> r = query(
                "SELECT * FROM tangent WHERE pair_id=? AND side=? AND status='current'", pairId, side);
        return r.isEmpty() ? null : r.get(0);
    }

    public List<Map<String, Object>> currentTangents() {
        return query("SELECT * FROM tangent WHERE status='current' ORDER BY seq");
    }

    // ---------- adoption decisions (also optimistic) ----------
    public String adopt(String transitionId, String candidate, String author, int baseVersion) {
        List<Map<String, Object>> cur = query(
                "SELECT * FROM adoption WHERE transition_id=? AND status='current' ORDER BY seq DESC",
                transitionId);
        int curVersion = cur.isEmpty() ? 0 : ((Number) cur.get(0).get("seq")).intValue();
        if (curVersion != baseVersion) {
            ObjectNode mine = Json.obj();
            mine.put("transition_id", transitionId).put("candidate", candidate).put("author", author);
            ObjectNode theirs = cur.isEmpty() ? Json.obj() : Json.from(cur.get(0));
            String cid = newId("cfl");
            exec("INSERT INTO conflict(id,seq,entity,entity_id,base_version,mine_json,theirs_json,status,resolution)"
                            + " VALUES(?,?,?,?,?,?,?,?,?)",
                    cid, nextSeq("conflict"), "adoption", transitionId, baseVersion,
                    Json.write(mine), Json.write(theirs), "open", null);
            return "CONFLICT:" + cid;
        }
        exec("UPDATE adoption SET status='superseded' WHERE transition_id=?", transitionId);
        exec("INSERT INTO adoption(transition_id,candidate,author,base_version,status) VALUES(?,?,?,?,?)",
                transitionId, candidate, author, baseVersion, "current");
        return "OK";
    }

    public Map<String, Object> currentAdoption(String transitionId) {
        List<Map<String, Object>> r = query(
                "SELECT * FROM adoption WHERE transition_id=? AND status='current' ORDER BY seq DESC",
                transitionId);
        return r.isEmpty() ? null : r.get(0);
    }

    public void resolveConflict(String conflictId, String choice) {
        Map<String, Object> c = query("SELECT * FROM conflict WHERE id=?", conflictId).get(0);
        JsonNode chosen = Json.parse((String) c.get(choice.equals("mine") ? "mine_json" : "theirs_json"));
        if (c.get("entity").equals("tangent")) {
            Map<String, Object> cur = currentTangent(chosen.get("pair_id").asText(),
                    chosen.get("side").asText());
            int v = cur == null ? 0 : ((Number) cur.get("version")).intValue();
            if (cur != null) exec("UPDATE tangent SET status='superseded' WHERE id=?", cur.get("id"));
            exec("INSERT INTO tangent(id,seq,segment_id,pair_id,side,t_lo,t_hi,author,version,status)"
                            + " VALUES(?,?,?,?,?,?,?,?,?,?)",
                    newId("tan"), nextSeq("tangent"), chosen.get("segment_id").asText(),
                    chosen.get("pair_id").asText(), chosen.get("side").asText(),
                    chosen.get("t_lo").asDouble(), chosen.get("t_hi").asDouble(),
                    chosen.get("author").asText(), v + 1, "current");
        } else if (c.get("entity").equals("adoption")) {
            String tid = chosen.get("transition_id").asText();
            exec("UPDATE adoption SET status='superseded' WHERE transition_id=?", tid);
            exec("INSERT INTO adoption(transition_id,candidate,author,base_version,status) VALUES(?,?,?,?,?)",
                    tid, chosen.get("candidate").asText(), chosen.get("author").asText(), -1, "current");
        }
        exec("UPDATE conflict SET status='resolved', resolution=? WHERE id=?", choice, conflictId);
    }

    // ---------- events ----------
    public void event(String derivationId, String type, String subject, JsonNode payload) {
        exec("INSERT INTO event(id,derivation_id,type,subject,payload_json,created_at)"
                        + " VALUES(?,?,?,?,?,?)",
                newId("evt"), derivationId, type, subject, Json.write(payload),
                Instant.now().toString());
    }

    public List<Map<String, Object>> events() {
        return query("SELECT * FROM event ORDER BY seq");
    }

    // ---------- derivation pointer ----------
    public String currentDerivation() {
        return db.meta("current_derivation");
    }

    // ---------- export / import ----------
    static final String[] TABLES = {"meta", "instrument_output", "mass_version", "calibration",
            "tangent", "derivation", "derived", "dep", "event", "conflict", "adoption"};

    public ObjectNode exportAll() {
        ObjectNode root = Json.obj();
        root.put("format", "dsc-case-export");
        root.put("version", Db.SCHEMA_VERSION);
        ObjectNode tables = root.putObject("tables");
        for (String t : TABLES) {
            ArrayNode arr = tables.putArray(t);
            String order = t.equals("meta") ? "key" : (t.equals("derived") || t.equals("dep"))
                    ? "derivation_id" : (t.equals("event") || t.equals("adoption")) ? "seq" : "seq";
            for (Map<String, Object> row : query("SELECT * FROM " + t + " ORDER BY " + order)) {
                arr.add(Json.from(row));
            }
        }
        return root;
    }

    public void importAll(JsonNode root) {
        JsonNode tables = root.get("tables");
        try {
            db.c.setAutoCommit(false);
            for (String t : TABLES) {
                db.c.createStatement().executeUpdate("DELETE FROM " + t);
            }
            for (String t : TABLES) {
                JsonNode rows = tables.get(t);
                if (rows == null) continue;
                for (JsonNode row : rows) {
                    insertRow(t, row);
                }
            }
            db.c.commit();
        } catch (Exception e) {
            try {
                db.c.rollback();
            } catch (SQLException ignored) {
            }
            throw new RuntimeException("import failed, rolled back", e);
        } finally {
            try {
                db.c.setAutoCommit(true);
            } catch (SQLException ignored) {
            }
        }
    }

    private void insertRow(String table, JsonNode row) throws SQLException {
        List<String> cols = new ArrayList<>();
        row.fieldNames().forEachRemaining(cols::add);
        StringBuilder sql = new StringBuilder("INSERT INTO ").append(table).append("(");
        StringBuilder ph = new StringBuilder();
        for (int i = 0; i < cols.size(); i++) {
            if (i > 0) {
                sql.append(",");
                ph.append(",");
            }
            sql.append(cols.get(i));
            ph.append("?");
        }
        sql.append(") VALUES(").append(ph).append(")");
        try (var ps = db.c.prepareStatement(sql.toString())) {
            for (int i = 0; i < cols.size(); i++) {
                JsonNode v = row.get(cols.get(i));
                if (v == null || v.isNull()) ps.setObject(i + 1, null);
                else if (v.isIntegralNumber()) ps.setLong(i + 1, v.asLong());
                else if (v.isNumber()) ps.setDouble(i + 1, v.asDouble());
                else ps.setString(i + 1, v.asText());
            }
            ps.executeUpdate();
        }
    }

    // ---------- helpers ----------
    public List<Map<String, Object>> query(String sql, Object... args) {
        try (var ps = db.c.prepareStatement(sql)) {
            for (int i = 0; i < args.length; i++) ps.setObject(i + 1, args[i]);
            try (ResultSet rs = ps.executeQuery()) {
                List<Map<String, Object>> out = new ArrayList<>();
                var md = rs.getMetaData();
                while (rs.next()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    for (int i = 1; i <= md.getColumnCount(); i++) {
                        row.put(md.getColumnName(i), rs.getObject(i));
                    }
                    out.add(row);
                }
                return out;
            }
        } catch (SQLException e) {
            throw new RuntimeException(sql, e);
        }
    }

    public void exec(String sql, Object... args) {
        try (var ps = db.c.prepareStatement(sql)) {
            for (int i = 0; i < args.length; i++) ps.setObject(i + 1, args[i]);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException(sql, e);
        }
    }

    private int nextSeq(String table) {
        List<Map<String, Object>> r = query("SELECT COALESCE(MAX(seq),0)+1 AS n FROM " + table);
        return ((Number) r.get(0).get("n")).intValue();
    }
}
