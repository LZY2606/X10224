package dsc.store;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dsc.domain.Json;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Content-addressed derived versions + current pointers.
 * A derivation write and its pointer flip share one transaction (atomic switch).
 */
public final class DerivedStore {
    private final Database db;

    public DerivedStore(Database db) { this.db = db; }

    public record Row(String id, String kind, String segmentId, ObjectNode content,
                      String inputHash, String ruleVersion, JsonNode depHashes, long eventSeq, String supersededBy) {}

    /** Insert derived row (idempotent by id) inside an existing transaction. */
    public void putTx(String id, String kind, String segmentId, ObjectNode content,
                      String inputHash, String ruleVersion, String depHashesJson, long eventSeq) throws SQLException {
        try (PreparedStatement ps = db.cx.prepareStatement(
                "insert or ignore into derived(id,kind,segment_id,content_json,input_hash,rule_version,dep_hashes_json,created_event_seq) " +
                "values(?,?,?,?,?,?,?,?)")) {
            ps.setString(1, id); ps.setString(2, kind); ps.setString(3, segmentId);
            ps.setString(4, Json.string(content)); ps.setString(5, inputHash);
            ps.setString(6, ruleVersion); ps.setString(7, depHashesJson); ps.setLong(8, eventSeq);
            ps.executeUpdate();
        }
    }

    /** Flip the current pointer for a scope+kind inside the same transaction. */
    public void setCurrentTx(String scope, String kind, String derivedId, long eventSeq, Long historySeq) throws SQLException {
        try (PreparedStatement ps = db.cx.prepareStatement(
                "insert into current_ptr(scope,kind,derived_id,updated_event_seq,history_seq) values(?,?,?,?,?) " +
                "on conflict(scope,kind) do update set derived_id=excluded.derived_id, " +
                "updated_event_seq=excluded.updated_event_seq, history_seq=excluded.history_seq")) {
            ps.setString(1, scope); ps.setString(2, kind); ps.setString(3, derivedId);
            ps.setLong(4, eventSeq);
            if (historySeq == null) ps.setNull(5, java.sql.Types.INTEGER); else ps.setLong(5, historySeq);
            ps.executeUpdate();
        }
    }

    public void setHistorySeqTx(String scope, String kind, Long historySeq) throws SQLException {
        try (PreparedStatement ps = db.cx.prepareStatement(
                "update current_ptr set history_seq=? where scope=? and kind=?")) {
            if (historySeq == null) ps.setNull(1, java.sql.Types.INTEGER); else ps.setLong(1, historySeq);
            ps.setString(2, scope); ps.setString(3, kind);
            ps.executeUpdate();
        }
    }

    public String currentId(String scope, String kind) {
        try (var ps = db.cx.prepareStatement(
                "select derived_id from current_ptr where scope=? and kind=?")) {
            ps.setString(1, scope); ps.setString(2, kind);
            try (var rs = ps.executeQuery()) { return rs.next() ? rs.getString(1) : null; }
        } catch (SQLException e) { throw new IllegalStateException(e); }
    }

    public Long historySeq(String scope, String kind) {
        try (var ps = db.cx.prepareStatement(
                "select history_seq from current_ptr where scope=? and kind=?")) {
            ps.setString(1, scope); ps.setString(2, kind);
            try (var rs = ps.executeQuery()) {
                if (rs.next()) { long v = rs.getLong(1); return rs.wasNull() ? null : v; }
                return null;
            }
        } catch (SQLException e) { throw new IllegalStateException(e); }
    }

    public Row get(String id) {
        try (var ps = db.cx.prepareStatement("select * from derived where id=?")) {
            ps.setString(1, id);
            try (ResultSet rs = ps.executeQuery()) { return rs.next() ? read(rs) : null; }
        } catch (SQLException e) { throw new IllegalStateException(e); }
    }

    public java.util.List<Row> all() {
        java.util.List<Row> out = new java.util.ArrayList<>();
        try (var rs = db.cx.createStatement().executeQuery("select * from derived order by created_event_seq, id")) {
            while (rs.next()) out.add(read(rs));
        } catch (SQLException e) { throw new IllegalStateException(e); }
        return out;
    }

    public void markSupersededTx(String id, String byId) throws SQLException {
        try (var ps = db.cx.prepareStatement("update derived set superseded_by=? where id=?")) {
            ps.setString(1, byId); ps.setString(2, id); ps.executeUpdate();
        }
    }

    private Row read(ResultSet rs) throws SQLException {
        return new Row(rs.getString("id"), rs.getString("kind"), rs.getString("segment_id"),
                Json.parseObj(rs.getString("content_json")), rs.getString("input_hash"),
                rs.getString("rule_version"), Json.parse(rs.getString("dep_hashes_json")),
                rs.getLong("created_event_seq"), rs.getString("superseded_by"));
    }
}
