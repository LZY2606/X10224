package dsc.store;

import com.fasterxml.jackson.databind.node.ObjectNode;
import dsc.domain.Json;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/** Versioned user-drawn temperature intervals (the draggable selections on the curve page). */
public final class IntervalStore {
    private final Database db;
    public IntervalStore(Database db) { this.db = db; }

    public void putTx(String id, int version, String segmentId, ObjectNode content, long eventSeq) throws SQLException {
        try (PreparedStatement ps = db.cx.prepareStatement(
                "insert into intervals(id,version,segment_id,content_json,created_event_seq) values(?,?,?,?,?)")) {
            ps.setString(1, id); ps.setInt(2, version); ps.setString(3, segmentId);
            ps.setString(4, Json.string(content)); ps.setLong(5, eventSeq);
            ps.executeUpdate();
        }
    }

    public int latestVersion(String id) {
        try (var ps = db.cx.prepareStatement("select coalesce(max(version),0) from intervals where id=?")) {
            ps.setString(1, id);
            try (var rs = ps.executeQuery()) { return rs.next() ? rs.getInt(1) : 0; }
        } catch (SQLException e) { throw new IllegalStateException(e); }
    }

    public record Row(String id, int version, String segmentId, ObjectNode content, long eventSeq) {}

    public Row get(String id, int version) {
        try (var ps = db.cx.prepareStatement("select * from intervals where id=? and version=?")) {
            ps.setString(1, id); ps.setInt(2, version);
            try (ResultSet rs = ps.executeQuery()) { return rs.next() ? read(rs) : null; }
        } catch (SQLException e) { throw new IllegalStateException(e); }
    }

    public List<Row> all() {
        List<Row> out = new ArrayList<>();
        try (var rs = db.cx.createStatement().executeQuery(
                "select * from intervals order by segment_id, id, version")) {
            while (rs.next()) out.add(read(rs));
        } catch (SQLException e) { throw new IllegalStateException(e); }
        return out;
    }

    private Row read(ResultSet rs) throws SQLException {
        return new Row(rs.getString("id"), rs.getInt("version"), rs.getString("segment_id"),
                Json.parseObj(rs.getString("content_json")), rs.getLong("created_event_seq"));
    }
}
