package dsc.store;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Versioned store. Every derivation produces an independent version; the
 * current pointer flips only after the write fully succeeds. A crash between
 * journal-BEGIN and journal-DONE leaves the old version visible. Two users
 * editing from the same base version get a resolvable conflict, with both
 * branches preserved.
 */
public final class VersionStore {
    public static final ObjectMapper M = new ObjectMapper();
    private final Database db;

    public VersionStore(Database db) throws SQLException {
        this.db = db;
        recover();
    }

    /** Crash recovery: journal entries stuck in BEGIN are rolled back; old pointer stays visible. */
    private void recover() throws SQLException {
        List<String> dangling = new ArrayList<>();
        try (PreparedStatement ps = db.conn().prepareStatement(
                "SELECT txid FROM write_journal WHERE state='BEGIN'")) {
            ResultSet rs = ps.executeQuery();
            while (rs.next()) dangling.add(rs.getString(1));
        }
        for (String txid : dangling) {
            // version rows of the interrupted tx carry txid in payload envelope; remove them
            try (PreparedStatement ps = db.conn().prepareStatement(
                    "DELETE FROM sources WHERE created_event=?")) { ps.setString(1, txid); ps.executeUpdate(); }
            try (PreparedStatement ps = db.conn().prepareStatement(
                    "DELETE FROM write_journal WHERE txid=?")) { ps.setString(1, txid); ps.executeUpdate(); }
        }
    }

    public static final class Conflict extends Exception {
        public final String branchId;
        public Conflict(String branchId) { super("concurrent edit conflict"); this.branchId = branchId; }
    }

    /** Begin a journaled write. Returns txid. */
    public String begin() throws SQLException {
        String txid = UUID.randomUUID().toString();
        try (PreparedStatement ps = db.conn().prepareStatement(
                "INSERT INTO write_journal(txid,state,payload) VALUES(?,?,?)")) {
            ps.setString(1, txid); ps.setString(2, "BEGIN"); ps.setString(3, "{}");
            ps.executeUpdate();
        }
        return txid;
    }

    public int nextVersion(String kind, String id) throws SQLException {
        try (PreparedStatement ps = db.conn().prepareStatement(
                "SELECT COALESCE(MAX(version),0)+1 FROM sources WHERE kind=? AND id=?")) {
            ps.setString(1, kind); ps.setString(2, id);
            ResultSet rs = ps.executeQuery();
            return rs.next() ? rs.getInt(1) : 1;
        }
    }

    /** Stage a new source version inside txid; pointer not yet moved. */
    public int stageSource(String txid, String kind, String id, JsonNode payload) throws SQLException {
        int v = nextVersion(kind, id);
        try (PreparedStatement ps = db.conn().prepareStatement(
                "INSERT INTO sources(kind,id,version,payload,created_event) VALUES(?,?,?,?,?)")) {
            ps.setString(1, kind); ps.setString(2, id); ps.setInt(3, v);
            ps.setString(4, payload.toString()); ps.setString(5, txid);
            ps.executeUpdate();
        }
        return v;
    }

    /** Atomic commit: flip pointers, mark journal DONE. Either old or complete new version is visible. */
    public void commit(String txid, String[]... pointers) throws SQLException {
        db.conn().setAutoCommit(false);
        try {
            for (String[] p : pointers) {
                try (PreparedStatement ps = db.conn().prepareStatement(
                        "INSERT INTO current_source(kind,id,version) VALUES(?,?,?) " +
                        "ON CONFLICT(kind,id) DO UPDATE SET version=excluded.version")) {
                    ps.setString(1, p[0]); ps.setString(2, p[1]); ps.setInt(3, Integer.parseInt(p[2]));
                    ps.executeUpdate();
                }
            }
            try (PreparedStatement ps = db.conn().prepareStatement(
                    "UPDATE write_journal SET state='DONE' WHERE txid=?")) {
                ps.setString(1, txid); ps.executeUpdate();
            }
            db.conn().commit();
        } catch (SQLException e) {
            db.conn().rollback();
            throw e;
        } finally {
            db.conn().setAutoCommit(true);
        }
    }

    public Optional<JsonNode> currentSource(String kind, String id) throws SQLException {
        try (PreparedStatement ps = db.conn().prepareStatement(
                "SELECT s.payload, s.version FROM sources s JOIN current_source c " +
                "ON s.kind=c.kind AND s.id=c.id AND s.version=c.version WHERE c.kind=? AND c.id=?")) {
            ps.setString(1, kind); ps.setString(2, id);
            ResultSet rs = ps.executeQuery();
            if (!rs.next()) return Optional.empty();
            try { return Optional.of(M.readTree(rs.getString(1))); }
            catch (Exception e) { throw new SQLException(e); }
        }
    }

    public int currentVersion(String kind, String id) throws SQLException {
        try (PreparedStatement ps = db.conn().prepareStatement(
                "SELECT version FROM current_source WHERE kind=? AND id=?")) {
            ps.setString(1, kind); ps.setString(2, id);
            ResultSet rs = ps.executeQuery();
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    /** Optimistic save: two users from the same base version produce a resolvable conflict. */
    public int saveSource(String kind, String id, JsonNode payload, int expectedBaseVersion,
                          String decidedBy) throws SQLException, Conflict {
        int cur = currentVersion(kind, id);
        if (cur != expectedBaseVersion) {
            String branchId = "br-" + UUID.randomUUID().toString().substring(0, 8);
            ObjectNode opts = M.createObjectNode();
            ArrayNode arr = opts.putArray("options");
            arr.addObject().put("label", "keep-current").put("version", cur);
            arr.addObject().put("label", "accept-incoming").put("version", cur + 1);
            try (PreparedStatement ps = db.conn().prepareStatement(
                    "INSERT INTO branches(id,parent_event,options,chosen,status) VALUES(?,?,?,?,?)")) {
                ps.setString(1, branchId); ps.setString(2, decidedBy);
                ps.setString(3, opts.toString()); ps.setNull(4, java.sql.Types.VARCHAR);
                ps.setString(5, "OPEN");
                ps.executeUpdate();
            }
            // incoming edit is still staged as its own version so nothing is lost
            String txid = begin();
            int v = stageSource(txid, kind, id, payload);
            commit(txid); // staged but pointer NOT moved
            throw new Conflict(branchId);
        }
        String txid = begin();
        int v = stageSource(txid, kind, id, payload);
        commit(txid, new String[]{kind, id, String.valueOf(v)});
        return v;
    }

    public void resolveBranch(String branchId, String chosenLabel) throws SQLException {
        try (PreparedStatement ps = db.conn().prepareStatement(
                "UPDATE branches SET chosen=?, status='RESOLVED' WHERE id=?")) {
            ps.setString(1, chosenLabel); ps.setString(2, branchId); ps.executeUpdate();
        }
    }

    /** Append a machine-readable event. Returns its sequence number. */
    public long event(String type, JsonNode payload, long baseVersion) throws SQLException {
        try (PreparedStatement ps = db.conn().prepareStatement(
                "INSERT INTO events(id,ts,type,payload,base_version) VALUES(?,?,?,?,?)")) {
            ps.setString(1, "ev-" + UUID.randomUUID().toString().substring(0, 8));
            ps.setString(2, Instant.now().toString());
            ps.setString(3, type);
            ps.setString(4, payload.toString());
            ps.setLong(5, baseVersion);
            ps.executeUpdate();
        }
        try (PreparedStatement ps = db.conn().prepareStatement("SELECT MAX(seq) FROM events")) {
            ResultSet rs = ps.executeQuery();
            return rs.next() ? rs.getLong(1) : 0;
        }
    }

    /** Re-import an exported event preserving its stable identity and timestamp. */
    public void importEvent(JsonNode e) throws SQLException {
        try (PreparedStatement ps = db.conn().prepareStatement(
                "INSERT INTO events(id,ts,type,payload,base_version) VALUES(?,?,?,?,?)")) {
            ps.setString(1, e.get("id").asText());
            ps.setString(2, e.get("ts").asText());
            ps.setString(3, e.get("type").asText());
            ps.setString(4, e.get("payload").toString());
            ps.setLong(5, e.get("baseVersion").asLong());
            ps.executeUpdate();
        }
    }

    public List<JsonNode> events() throws SQLException {
        List<JsonNode> out = new ArrayList<>();
        try (PreparedStatement ps = db.conn().prepareStatement(
                "SELECT seq,id,ts,type,payload,base_version FROM events ORDER BY seq")) {
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                ObjectNode n = M.createObjectNode();
                n.put("seq", rs.getLong(1));
                n.put("id", rs.getString(2));
                n.put("ts", rs.getString(3));
                n.put("type", rs.getString(4));
                try { n.set("payload", M.readTree(rs.getString(5))); } catch (Exception ignored) {}
                n.put("baseVersion", rs.getLong(6));
                out.add(n);
            }
        }
        return out;
    }

    /** Derived artifacts are versioned too, with their dependency set recorded. */
    public void putDerived(String kind, String id, int version, JsonNode deps, String status, JsonNode payload)
            throws SQLException {
        try (PreparedStatement ps = db.conn().prepareStatement(
                "INSERT OR REPLACE INTO derived(kind,id,version,deps,status,payload) VALUES(?,?,?,?,?,?)")) {
            ps.setString(1, kind); ps.setString(2, id); ps.setInt(3, version);
            ps.setString(4, deps.toString()); ps.setString(5, status); ps.setString(6, payload.toString());
            ps.executeUpdate();
        }
    }

    public List<JsonNode> derivedAll() throws SQLException {
        List<JsonNode> out = new ArrayList<>();
        try (PreparedStatement ps = db.conn().prepareStatement(
                "SELECT kind,id,version,deps,status,payload FROM derived ORDER BY kind,id,version")) {
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                ObjectNode n = M.createObjectNode();
                n.put("kind", rs.getString(1));
                n.put("id", rs.getString(2));
                n.put("version", rs.getInt(3));
                try {
                    n.set("deps", M.readTree(rs.getString(4)));
                    n.set("payload", M.readTree(rs.getString(6)));
                } catch (Exception ignored) {}
                n.put("status", rs.getString(5));
                out.add(n);
            }
        }
        return out;
    }

    public void markDerivedStatus(String kind, String id, String status) throws SQLException {
        try (PreparedStatement ps = db.conn().prepareStatement(
                "UPDATE derived SET status=? WHERE kind=? AND id=?")) {
            ps.setString(1, status); ps.setString(2, kind); ps.setString(3, id);
            ps.executeUpdate();
        }
    }

    public List<JsonNode> branches() throws SQLException {
        List<JsonNode> out = new ArrayList<>();
        try (PreparedStatement ps = db.conn().prepareStatement(
                "SELECT id,parent_event,options,chosen,status FROM branches ORDER BY id")) {
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                ObjectNode n = M.createObjectNode();
                n.put("id", rs.getString(1));
                n.put("parentEvent", rs.getString(2));
                try { n.set("options", M.readTree(rs.getString(3))); } catch (Exception ignored) {}
                if (rs.getString(4) != null) n.put("chosen", rs.getString(4));
                n.put("status", rs.getString(5));
                out.add(n);
            }
        }
        return out;
    }
}
