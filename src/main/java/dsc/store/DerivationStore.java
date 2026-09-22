package dsc.store;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import dsc.Json;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Versioned derivations. Every recomputation inserts a new version; the current
 * pointer switches only after the full row is written, inside the same transaction.
 * A crash mid-write therefore leaves either the old version or a complete new one.
 */
public final class DerivationStore {
    private final Db db;

    public DerivationStore(Db db) {
        this.db = db;
    }

    /** A typed dependency reference: type in {source, derivation, decision}. */
    public record Dep(String type, String ref, long version) {
        public JsonNode toJson() {
            var n = Json.obj();
            n.put("type", type);
            n.put("ref", ref);
            n.put("version", version);
            return n;
        }

        public static Dep fromJson(JsonNode n) {
            return new Dep(n.get("type").asText(), n.get("ref").asText(), n.get("version").asLong());
        }
    }

    public record Derivation(String id, String key, String kind, int version, List<Dep> inputs,
            JsonNode payload, String status, long createdSeq, long validFrom, Long validTo) {}

    /**
     * Insert a new derivation version and switch the pointer atomically.
     * Caller must hold a transaction. faultHook (may be null) runs between the
     * insert and the pointer switch to simulate an interrupted write in tests.
     */
    public Derivation commit(String key, String kind, List<Dep> inputs, JsonNode payload,
            long seq, Runnable faultHook) {
        int version = currentVersion(key) + 1;
        String id = "drv-" + db.nextSeq("derivation");
        ArrayNode deps = Json.M.createArrayNode();
        inputs.forEach(d -> deps.add(d.toJson()));
        try {
            Optional<Derivation> prev = current(key);
            try (PreparedStatement ps = db.conn().prepareStatement(
                    "INSERT INTO derivations(id,key,kind,version,inputs,payload,status,created_seq,valid_from,valid_to)"
                            + " VALUES(?,?,?,?,?,?,'current',?,?,NULL)")) {
                ps.setString(1, id);
                ps.setString(2, key);
                ps.setString(3, kind);
                ps.setInt(4, version);
                ps.setString(5, Json.write(deps));
                ps.setString(6, Json.write(payload));
                ps.setLong(7, seq);
                ps.setLong(8, seq);
                ps.executeUpdate();
            }
            if (faultHook != null) {
                faultHook.run();
            }
            if (prev.isPresent()) {
                try (PreparedStatement ps = db.conn().prepareStatement(
                        "UPDATE derivations SET status='superseded', valid_to=? WHERE id=?")) {
                    ps.setLong(1, seq);
                    ps.setString(2, prev.get().id());
                    ps.executeUpdate();
                }
            }
            try (PreparedStatement ps = db.conn().prepareStatement(
                    "INSERT INTO pointers(key,derivation_id) VALUES(?,?)"
                            + " ON CONFLICT(key) DO UPDATE SET derivation_id=excluded.derivation_id")) {
                ps.setString(1, key);
                ps.setString(2, id);
                ps.executeUpdate();
            }
            return new Derivation(id, key, kind, version, inputs, payload, "current", seq, seq, null);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public Derivation commit(String key, String kind, List<Dep> inputs, JsonNode payload, long seq) {
        return commit(key, kind, inputs, payload, seq, null);
    }

    public int currentVersion(String key) {
        try (Statement s = db.conn().createStatement();
                ResultSet rs = s.executeQuery(
                        "SELECT COALESCE(MAX(version),0) FROM derivations WHERE key='" + key + "'")) {
            rs.next();
            return rs.getInt(1);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public Optional<Derivation> current(String key) {
        try (PreparedStatement ps = db.conn().prepareStatement(
                "SELECT d.id,d.key,d.kind,d.version,d.inputs,d.payload,d.status,d.created_seq,d.valid_from,d.valid_to"
                        + " FROM pointers p JOIN derivations d ON d.id=p.derivation_id WHERE p.key=?")) {
            ps.setString(1, key);
            ResultSet rs = ps.executeQuery();
            return rs.next() ? Optional.of(row(rs)) : Optional.empty();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public Optional<Derivation> byId(String id) {
        try (PreparedStatement ps = db.conn().prepareStatement(
                "SELECT id,key,kind,version,inputs,payload,status,created_seq,valid_from,valid_to"
                        + " FROM derivations WHERE id=?")) {
            ps.setString(1, id);
            ResultSet rs = ps.executeQuery();
            return rs.next() ? Optional.of(row(rs)) : Optional.empty();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public List<Derivation> currentAll() {
        return query("SELECT d.id,d.key,d.kind,d.version,d.inputs,d.payload,d.status,d.created_seq,d.valid_from,d.valid_to"
                + " FROM pointers p JOIN derivations d ON d.id=p.derivation_id ORDER BY d.key");
    }

    /** Derivations visible at the given event seq (valid_from <= seq < valid_to). */
    public List<Derivation> asOf(long seq) {
        return query("SELECT id,key,kind,version,inputs,payload,status,created_seq,valid_from,valid_to"
                + " FROM derivations WHERE valid_from<=" + seq
                + " AND (valid_to IS NULL OR valid_to>" + seq + ") ORDER BY key");
    }

    public List<Derivation> history(String key) {
        return query("SELECT id,key,kind,version,inputs,payload,status,created_seq,valid_from,valid_to"
                + " FROM derivations WHERE key='" + key + "' ORDER BY version");
    }

    /** All current derivations whose inputs reference the given dep ref (any version). */
    public List<Derivation> findByInput(String type, String ref) {
        List<Derivation> out = new ArrayList<>();
        for (Derivation d : currentAll()) {
            for (Dep dep : d.inputs()) {
                if (dep.type().equals(type) && dep.ref().equals(ref)) {
                    out.add(d);
                    break;
                }
            }
        }
        return out;
    }

    public List<Derivation> query(String sql) {
        List<Derivation> out = new ArrayList<>();
        try (Statement s = db.conn().createStatement(); ResultSet rs = s.executeQuery(sql)) {
            while (rs.next()) {
                out.add(row(rs));
            }
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        return out;
    }

    private Derivation row(ResultSet rs) throws Exception {
        List<Dep> deps = new ArrayList<>();
        for (JsonNode n : Json.parse(rs.getString(5))) {
            deps.add(Dep.fromJson(n));
        }
        long validTo = rs.getLong(10);
        return new Derivation(rs.getString(1), rs.getString(2), rs.getString(3), rs.getInt(4), deps,
                Json.parse(rs.getString(6)), rs.getString(7), rs.getLong(8), rs.getLong(9),
                rs.wasNull() ? null : validTo);
    }
}
