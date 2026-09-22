package dsc.store;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Persisted recomputation results. Each derivation is an independent version.
 * A row is written with complete=0 first and flipped to 1 in the same
 * transaction as the cursor switch, so after restart readers see only fully
 * successful versions.
 */
public class DerivedStore {

    private final Connection conn;

    public DerivedStore(Connection conn) {
        this.conn = conn;
    }

    public void beginWrite(String branch, String derivedId, String nodeId,
                           String inputsHash, String rulesVersion,
                           String causedByEventId, String adoptedId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO derived_versions(derived_id, branch, node_id, inputs_hash, "
                        + "rules_version, status, complete, json, created_at_millis, "
                        + "caused_by_event_id, adopted_id) "
                        + "VALUES(?,?,?,?,?,?,?,?,?,?,?)")) {
            ps.setString(1, derivedId);
            ps.setString(2, branch);
            ps.setString(3, nodeId);
            ps.setString(4, inputsHash);
            ps.setString(5, rulesVersion);
            ps.setString(6, "ok");
            ps.setInt(7, 0);
            ps.setString(8, null);
            ps.setLong(9, System.currentTimeMillis());
            ps.setString(10, causedByEventId);
            ps.setString(11, adoptedId);
            ps.executeUpdate();
        }
    }

    public void completeWrite(String branch, String derivedId,
                              Map<String, Object> json) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "UPDATE derived_versions SET complete=1, status='ok', json=? "
                        + "WHERE derived_id=? AND branch=?")) {
            ps.setString(1, Json.write(json));
            ps.setString(2, derivedId);
            ps.setString(3, branch);
            ps.executeUpdate();
        }
    }

    public List<Map<String, Object>> versions(String branch, String nodeId) {
        List<Map<String, Object>> out = new ArrayList<>();
        String sql = "SELECT derived_id, inputs_hash, rules_version, complete, created_at_millis, "
                + "caused_by_event_id, adopted_id FROM derived_versions "
                + "WHERE branch=? " + (nodeId == null ? "" : "AND node_id=? ")
                + "ORDER BY created_at_millis";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, branch);
            if (nodeId != null) {
                ps.setString(2, nodeId);
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    java.util.LinkedHashMap<String, Object> m = new java.util.LinkedHashMap<>();
                    m.put("derivedId", rs.getString("derived_id"));
                    m.put("inputsHash", rs.getString("inputs_hash"));
                    m.put("rulesVersion", rs.getString("rules_version"));
                    m.put("complete", rs.getInt("complete") == 1);
                    m.put("createdAtMillis", rs.getLong("created_at_millis"));
                    m.put("causedByEventId", rs.getString("caused_by_event_id"));
                    m.put("adoptedId", rs.getString("adopted_id"));
                    out.add(m);
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        return out;
    }

    public String jsonOf(String branch, String derivedId) {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT json FROM derived_versions WHERE branch=? AND derived_id=? AND complete=1")) {
            ps.setString(1, branch);
            ps.setString(2, derivedId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString("json") : null;
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }
}
