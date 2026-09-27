package dsc.store;

import dsc.model.Derivation;
import dsc.model.Interval;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

final class Derivations {

    private Derivations() {
    }

    static void insert(Connection c, Derivation d, String contentHash) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO derivations(derivation_id,project_id,kind,interval_id,status,"
                        + "rule_version,inputs_json,payload_json,error_code,producing_event_id,"
                        + "producing_seq,branch_root,actor,created_at_ms,content_hash) "
                        + "VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)")) {
            ps.setString(1, d.derivationId());
            ps.setString(2, d.projectId());
            ps.setString(3, d.kind());
            ps.setString(4, d.intervalId());
            ps.setString(5, d.status());
            ps.setString(6, d.ruleVersion());
            ps.setString(7, d.inputsJson());
            ps.setString(8, d.payloadJson());
            ps.setString(9, d.errorCode());
            ps.setString(10, d.producingEventId());
            ps.setLong(11, d.producingSeq());
            ps.setString(12, d.branchRoot());
            ps.setString(13, d.actor());
            ps.setLong(14, d.createdAtMs());
            ps.setString(15, contentHash);
            ps.executeUpdate();
        }
    }

    static List<Derivation> forProject(Connection c, String projectId) throws SQLException {
        List<Derivation> out = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT derivation_id,project_id,kind,interval_id,status,rule_version,"
                        + "inputs_json,payload_json,error_code,producing_event_id,producing_seq,"
                        + "branch_root,actor,created_at_ms FROM derivations "
                        + "WHERE project_id=? ORDER BY producing_seq, derivation_id")) {
            ps.setString(1, projectId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(read(rs));
                }
            }
        }
        return out;
    }

    static Derivation byId(Connection c, String derivationId) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT derivation_id,project_id,kind,interval_id,status,rule_version,"
                        + "inputs_json,payload_json,error_code,producing_event_id,producing_seq,"
                        + "branch_root,actor,created_at_ms FROM derivations WHERE derivation_id=?")) {
            ps.setString(1, derivationId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? read(rs) : null;
            }
        }
    }

    private static Derivation read(ResultSet rs) throws SQLException {
        return new Derivation(rs.getString(1), rs.getString(2), rs.getString(3),
                rs.getString(4), rs.getString(5), rs.getString(6), rs.getString(7),
                rs.getString(8), rs.getString(9), rs.getString(10), rs.getLong(11),
                rs.getString(12), rs.getString(13), rs.getLong(14));
    }

    static void upsertInterval(Connection c, Interval interval) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO intervals(interval_id,project_id,segment_id,label,start_c,end_c,created_seq) "
                        + "VALUES(?,?,?,?,?,?,?) ON CONFLICT(interval_id) DO UPDATE SET "
                        + "segment_id=excluded.segment_id,label=excluded.label,"
                        + "start_c=excluded.start_c,end_c=excluded.end_c,created_seq=excluded.created_seq")) {
            ps.setString(1, interval.intervalId());
            ps.setString(2, interval.projectId());
            ps.setString(3, interval.segmentId());
            ps.setString(4, interval.label());
            ps.setString(5, interval.startC());
            ps.setString(6, interval.endC());
            ps.setLong(7, interval.createdSeq());
            ps.executeUpdate();
        }
    }

    static List<Interval> intervals(Connection c, String projectId) throws SQLException {
        List<Interval> out = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT interval_id,project_id,segment_id,label,start_c,end_c,created_seq "
                        + "FROM intervals WHERE project_id=? ORDER BY created_seq, interval_id")) {
            ps.setString(1, projectId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(new Interval(rs.getString(1), rs.getString(2), rs.getString(3),
                            rs.getString(4), rs.getDouble(5), rs.getDouble(6), rs.getLong(7)));
                }
            }
        }
        return out;
    }

    static int countAll(Connection c) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT COUNT(*) FROM derivations");
             ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }
}
