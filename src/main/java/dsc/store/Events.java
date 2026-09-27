package dsc.store;

import dsc.model.Branch;
import dsc.model.Event;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

final class Events {

    private Events() {
    }

    static Event append(Connection c, Event event) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO events(event_id,project_id,parent_event_id,branch_root,type,"
                        + "actor,created_at_ms,expected_base_seq,payload_json) "
                        + "VALUES(?,?,?,?,?,?,?,?,?)",
                Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, event.eventId());
            ps.setString(2, event.projectId());
            ps.setString(3, event.parentEventId());
            ps.setString(4, event.branchRoot());
            ps.setString(5, event.type());
            ps.setString(6, event.actor());
            ps.setLong(7, event.createdAtMs());
            ps.setLong(8, event.expectedBaseSeq());
            ps.setString(9, event.payloadJson());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                long seq = keys.next() ? keys.getLong(1) : event.seq();
                return new Event(seq, event.eventId(), event.projectId(), event.parentEventId(),
                        event.branchRoot(), event.type(), event.actor(), event.createdAtMs(),
                        event.expectedBaseSeq(), event.payloadJson());
            }
        }
    }

    static Event byId(Connection c, String eventId) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT seq,event_id,project_id,parent_event_id,branch_root,type,actor,"
                        + "created_at_ms,expected_base_seq,payload_json FROM events WHERE event_id=?")) {
            ps.setString(1, eventId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? read(rs) : null;
            }
        }
    }

    static Event bySeq(Connection c, String projectId, long seq) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT seq,event_id,project_id,parent_event_id,branch_root,type,actor,"
                        + "created_at_ms,expected_base_seq,payload_json FROM events "
                        + "WHERE project_id=? AND seq=?")) {
            ps.setString(1, projectId);
            ps.setLong(2, seq);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? read(rs) : null;
            }
        }
    }

    static List<Event> forProject(Connection c, String projectId) throws SQLException {
        List<Event> out = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT seq,event_id,project_id,parent_event_id,branch_root,type,actor,"
                        + "created_at_ms,expected_base_seq,payload_json FROM events "
                        + "WHERE project_id=? ORDER BY seq")) {
            ps.setString(1, projectId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(read(rs));
                }
            }
        }
        return out;
    }

    static long count(Connection c, String projectId) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT COUNT(*) FROM events WHERE project_id=?")) {
            ps.setString(1, projectId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 0;
            }
        }
    }

    private static Event read(ResultSet rs) throws SQLException {
        return new Event(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4),
                rs.getString(5), rs.getString(6), rs.getString(7), rs.getLong(8),
                rs.getLong(9), rs.getString(10));
    }

    static void upsertBranch(Connection c, Branch branch) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO branches(branch_root,project_id,label,tip_event_id,tip_seq,status,"
                        + "forked_from_event_id) VALUES(?,?,?,?,?,?,?) "
                        + "ON CONFLICT(branch_root) DO UPDATE SET label=excluded.label,"
                        + "tip_event_id=excluded.tip_event_id,tip_seq=excluded.tip_seq,"
                        + "status=excluded.status,forked_from_event_id=excluded.forked_from_event_id")) {
            ps.setString(1, branch.branchRoot());
            ps.setString(2, branch.projectId());
            ps.setString(3, branch.label());
            ps.setString(4, branch.tipEventId());
            ps.setLong(5, branch.tipSeq());
            ps.setString(6, branch.status());
            ps.setString(7, branch.forkedFromEventId());
            ps.executeUpdate();
        }
    }

    static List<Branch> branches(Connection c, String projectId) throws SQLException {
        List<Branch> out = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT branch_root,project_id,label,tip_event_id,tip_seq,status,forked_from_event_id "
                        + "FROM branches WHERE project_id=? ORDER BY branch_root")) {
            ps.setString(1, projectId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(new Branch(rs.getString(1), rs.getString(2), rs.getString(3),
                            rs.getString(4), rs.getLong(5), rs.getString(6), rs.getString(7)));
                }
            }
        }
        return out;
    }

    static void setPointer(Connection c, String projectId, Branch branch) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO pointers(project_id,branch_root,tip_event_id,tip_seq) VALUES(?,?,?,?) "
                        + "ON CONFLICT(project_id) DO UPDATE SET branch_root=excluded.branch_root,"
                        + "tip_event_id=excluded.tip_event_id,tip_seq=excluded.tip_seq")) {
            ps.setString(1, projectId);
            ps.setString(2, branch.branchRoot());
            ps.setString(3, branch.tipEventId());
            ps.setLong(4, branch.tipSeq());
            ps.executeUpdate();
        }
    }

    static Branch pointer(Connection c, String projectId) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT b.branch_root,b.project_id,b.label,b.tip_event_id,b.tip_seq,b.status,"
                        + "b.forked_from_event_id FROM pointers p JOIN branches b "
                        + "ON b.branch_root=p.branch_root AND b.project_id=p.project_id "
                        + "WHERE p.project_id=?")) {
            ps.setString(1, projectId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                return new Branch(rs.getString(1), rs.getString(2), rs.getString(3),
                        rs.getString(4), rs.getLong(5), rs.getString(6), rs.getString(7));
            }
        }
    }
}
