package dsc.store;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dsc.domain.Json;

import java.sql.ResultSet;

/**
 * Whole-dossier snapshot: a single JSON document containing every table in event order.
 * Re-importing it into an empty database preserves ids, seq ordering and provenance links.
 */
public final class Snapshot {
    private Snapshot() {}

    public static ObjectNode export_(Database db) {
        ObjectNode root = Json.obj();
        root.put("format", "dsc-dossier");
        root.put("formatVersion", db.userVersionSafe());
        root.set("meta", dump(db, "select key,value from meta order by key", "key", "value"));
        root.set("events", dump(db,
                "select seq,id,branch,type,actor,at,payload_json,basis_seq,parent_event_id from events order by seq",
                "seq","id","branch","type","actor","at","payload_json","basis_seq","parent_event_id"));
        root.set("sources", dump(db,
                "select id,version,kind,content_json,supersedes,created_event_seq from sources order by id,version",
                "id","version","kind","content_json","supersedes","created_event_seq"));
        root.set("derived", dump(db,
                "select id,kind,segment_id,content_json,input_hash,rule_version,dep_hashes_json,created_event_seq,superseded_by " +
                "from derived order by created_event_seq,id",
                "id","kind","segment_id","content_json","input_hash","rule_version","dep_hashes_json","created_event_seq","superseded_by"));
        root.set("intervals", dump(db,
                "select id,version,segment_id,content_json,created_event_seq from intervals order by segment_id,id,version",
                "id","version","segment_id","content_json","created_event_seq"));
        root.set("current_ptr", dump(db,
                "select scope,kind,derived_id,updated_event_seq,history_seq from current_ptr order by scope,kind",
                "scope","kind","derived_id","updated_event_seq","history_seq"));
        return root;
    }

    private static ArrayNode dump(Database db, String sql, String... cols) {
        ArrayNode arr = Json.arr();
        try (var st = db.cx.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                ObjectNode row = Json.obj();
                for (String c : cols) {
                    String v = rs.getString(c);
                    if ("payload_json".equals(c) || "content_json".equals(c) || "dep_hashes_json".equals(c)) {
                        if (v == null) row.putNull(c); else row.set(c, Json.parse(v));
                    } else if ("seq".equals(c) || "version".equals(c) || "created_event_seq".equals(c)
                            || "updated_event_seq".equals(c) || "history_seq".equals(c)) {
                        long lv = rs.getLong(c);
                        if (rs.wasNull()) row.putNull(c); else row.put(c, lv);
                    } else {
                        row.put(c, v);
                    }
                }
                arr.add(row);
            }
        } catch (Exception e) { throw new IllegalStateException(e); }
        return arr;
    }

    /** Import into an EMPTY database. Rows are inserted in event order; derived ids are stable. */
    public static void importInto(Database db, ObjectNode root) {
        try {
            db.cx.setAutoCommit(false);
            for (var r : root.withArray("events")) {
                try (var ps = db.cx.prepareStatement(
                        "insert into events(seq,id,branch,type,actor,at,payload_json,basis_seq,parent_event_id) " +
                        "values(?,?,?,?,?,?,?,?,?)")) {
                    ps.setLong(1, r.get("seq").asLong());
                    ps.setString(2, t(r,"id")); ps.setString(3, t(r,"branch")); ps.setString(4, t(r,"type"));
                    ps.setString(5, t(r,"actor")); ps.setString(6, t(r,"at"));
                    ps.setString(7, Json.string(r.get("payload_json")));
                    ps.setString(8, t(r,"basis_seq")); ps.setString(9, t(r,"parent_event_id"));
                    ps.executeUpdate();
                }
            }
            for (var r : root.withArray("sources")) {
                try (var ps = db.cx.prepareStatement(
                        "insert into sources(id,version,kind,content_json,supersedes,created_event_seq) values(?,?,?,?,?,?)")) {
                    ps.setString(1,t(r,"id")); ps.setInt(2,r.get("version").asInt());
                    ps.setString(3,t(r,"kind")); ps.setString(4,Json.string(r.get("content_json")));
                    ps.setString(5,t(r,"supersedes")); ps.setLong(6,r.get("created_event_seq").asLong());
                    ps.executeUpdate();
                }
            }
            for (var r : root.withArray("derived")) {
                try (var ps = db.cx.prepareStatement(
                        "insert into derived(id,kind,segment_id,content_json,input_hash,rule_version,dep_hashes_json,created_event_seq,superseded_by) " +
                        "values(?,?,?,?,?,?,?,?,?)")) {
                    ps.setString(1,t(r,"id")); ps.setString(2,t(r,"kind")); ps.setString(3,t(r,"segment_id"));
                    ps.setString(4,Json.string(r.get("content_json"))); ps.setString(5,t(r,"input_hash"));
                    ps.setString(6,t(r,"rule_version")); ps.setString(7,Json.string(r.get("dep_hashes_json")));
                    ps.setLong(8,r.get("created_event_seq").asLong()); ps.setString(9,t(r,"superseded_by"));
                    ps.executeUpdate();
                }
            }
            for (var r : root.withArray("intervals")) {
                try (var ps = db.cx.prepareStatement(
                        "insert into intervals(id,version,segment_id,content_json,created_event_seq) values(?,?,?,?,?)")) {
                    ps.setString(1,t(r,"id")); ps.setInt(2,r.get("version").asInt());
                    ps.setString(3,t(r,"segment_id")); ps.setString(4,Json.string(r.get("content_json")));
                    ps.setLong(5,r.get("created_event_seq").asLong());
                    ps.executeUpdate();
                }
            }
            if (root.has("current_ptr")) {
                for (var r : root.withArray("current_ptr")) {
                    try (var ps = db.cx.prepareStatement(
                            "insert into current_ptr(scope,kind,derived_id,updated_event_seq,history_seq) values(?,?,?,?,?)")) {
                        ps.setString(1,t(r,"scope")); ps.setString(2,t(r,"kind"));
                        ps.setString(3,t(r,"derived_id"));
                        ps.setLong(4,r.get("updated_event_seq").asLong());
                        if (r.hasNonNull("history_seq")) ps.setLong(5,r.get("history_seq").asLong());
                        else ps.setNull(5,java.sql.Types.INTEGER);
                        ps.executeUpdate();
                    }
                }
            }
            db.cx.commit();
            db.cx.setAutoCommit(true);
        } catch (Exception e) {
            try { db.cx.rollback(); db.cx.setAutoCommit(true); } catch (Exception ignored) {}
            throw new IllegalStateException("snapshot import failed: " + e.getMessage(), e);
        }
    }

    private static String t(com.fasterxml.jackson.databind.JsonNode r, String key) {
        return r.hasNonNull(key) ? r.get(key).asText() : null;
    }
}
