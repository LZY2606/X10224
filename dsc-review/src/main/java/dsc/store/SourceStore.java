package dsc.store;

import com.fasterxml.jackson.databind.node.ObjectNode;
import dsc.domain.Json;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/** Versioned direct inputs (instrument output, mass, rate, atmosphere, calibration, tangents). */
public final class SourceStore {
    private final Database db;

    public SourceStore(Database db) { this.db = db; }

    /** Insert a new version of a direct source within the caller's transaction. */
    public void putTx(String id, int version, String kind, ObjectNode content, String supersedes, long eventSeq)
            throws SQLException {
        try (PreparedStatement ps = db.cx.prepareStatement(
                "insert into sources(id,version,kind,content_json,supersedes,created_event_seq) values(?,?,?,?,?,?)")) {
            ps.setString(1, id); ps.setInt(2, version); ps.setString(3, kind);
            ps.setString(4, Json.string(content)); ps.setString(5, supersedes); ps.setLong(6, eventSeq);
            ps.executeUpdate();
        }
    }

    public int latestVersion(String id) {
        try (var ps = db.cx.prepareStatement("select coalesce(max(version),0) from sources where id=?")) {
            ps.setString(1, id);
            try (var rs = ps.executeQuery()) { return rs.next() ? rs.getInt(1) : 0; }
        } catch (SQLException e) { throw new IllegalStateException(e); }
    }

    public record Row(String id, int version, String kind, ObjectNode content, String supersedes, long eventSeq) {}

    public Row get(String id, int version) {
        try (var ps = db.cx.prepareStatement("select * from sources where id=? and version=?")) {
            ps.setString(1, id); ps.setInt(2, version);
            try (ResultSet rs = ps.executeQuery()) { return rs.next() ? read(rs) : null; }
        } catch (SQLException e) { throw new IllegalStateException(e); }
    }

    public Row latest(String id) {
        try (var ps = db.cx.prepareStatement(
                "select * from sources where id=? order by version desc limit 1")) {
            ps.setString(1, id);
            try (ResultSet rs = ps.executeQuery()) { return rs.next() ? read(rs) : null; }
        } catch (SQLException e) { throw new IllegalStateException(e); }
    }

    public java.util.List<Row> all() {
        java.util.List<Row> out = new java.util.ArrayList<>();
        try (var rs = db.cx.createStatement().executeQuery("select * from sources order by id, version")) {
            while (rs.next()) out.add(read(rs));
        } catch (SQLException e) { throw new IllegalStateException(e); }
        return out;
    }

    private Row read(ResultSet rs) throws SQLException {
        return new Row(rs.getString("id"), rs.getInt("version"), rs.getString("kind"),
                Json.parseObj(rs.getString("content_json")), rs.getString("supersedes"),
                rs.getLong("created_event_seq"));
    }
}
