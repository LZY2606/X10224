package dsc.store;

import dsc.domain.Enums;
import dsc.domain.EventRecord;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class EventDao {
    private final Repository repo;
    public EventDao(Repository repo) { this.repo = repo; }
    private Connection cx() { return repo.cx(); }

    public long insert(EventRecord e) {
        try (PreparedStatement ps = cx().prepareStatement(
                "INSERT INTO event_log(event_id,branch_id,parent_event_id,kind,subject_type,subject_id,"
              + "subject_version,actor,detail_json,note,created_at) VALUES(?,?,?,?,?,?,?,?,?,?,?)",
                Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, e.eventId); ps.setString(2, e.branchId); ps.setString(3, e.parentEventId);
            ps.setString(4, e.kind.name()); ps.setString(5, e.subjectType); ps.setString(6, e.subjectId);
            if (e.subjectVersion > 0) ps.setInt(7, e.subjectVersion); else ps.setNull(7, Types.INTEGER);
            ps.setString(8, e.actor); ps.setString(9, e.detailJson); ps.setString(10, e.note);
            ps.setLong(11, e.createdAt);
            ps.executeUpdate();
            ResultSet keys = ps.getGeneratedKeys();
            keys.next();
            e.seq = keys.getLong(1);
            return e.seq;
        } catch (SQLException ex) { throw new RuntimeException(ex); }
    }

    public List<EventRecord> all() {
        return query("SELECT * FROM event_log ORDER BY seq");
    }

    public List<EventRecord> branch(String branchId) {
        List<EventRecord> out = new ArrayList<>();
        try (PreparedStatement ps = cx().prepareStatement(
                "WITH RECURSIVE anc(id) AS ("
              + "  SELECT ? UNION SELECT parent_branch_id FROM branch JOIN anc ON branch.id=anc.id WHERE parent_branch_id IS NOT NULL"
              + ") SELECT e.* FROM event_log e WHERE e.branch_id IN (SELECT id FROM anc) ORDER BY e.seq")) {
            ps.setString(1, branchId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) out.add(map(rs));
        } catch (SQLException e) { throw new RuntimeException(e); }
        return out;
    }

    public EventRecord byId(String eventId) {
        try (PreparedStatement ps = cx().prepareStatement("SELECT * FROM event_log WHERE event_id=?")) {
            ps.setString(1, eventId);
            ResultSet rs = ps.executeQuery();
            return rs.next() ? map(rs) : null;
        } catch (SQLException e) { throw new RuntimeException(e); }
    }

    public String lastEventId(String branchId) {
        List<EventRecord> es = branch(branchId);
        return es.isEmpty() ? null : es.get(es.size() - 1).eventId;
    }

    private List<EventRecord> query(String sql) {
        List<EventRecord> out = new ArrayList<>();
        try (Statement st = cx().createStatement(); ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) out.add(map(rs));
        } catch (SQLException e) { throw new RuntimeException(e); }
        return out;
    }

    private EventRecord map(ResultSet rs) throws SQLException {
        EventRecord e = new EventRecord();
        e.seq = rs.getLong("seq"); e.eventId = rs.getString("event_id");
        e.branchId = rs.getString("branch_id"); e.parentEventId = rs.getString("parent_event_id");
        e.kind = Enums.EventKind.valueOf(rs.getString("kind"));
        e.subjectType = rs.getString("subject_type"); e.subjectId = rs.getString("subject_id");
        int sv = rs.getInt("subject_version"); e.subjectVersion = rs.wasNull() ? 0 : sv;
        e.actor = rs.getString("actor"); e.detailJson = rs.getString("detail_json");
        e.note = rs.getString("note"); e.createdAt = rs.getLong("created_at");
        return e;
    }
}
