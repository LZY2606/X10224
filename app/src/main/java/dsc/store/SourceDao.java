package dsc.store;

import dsc.domain.Enums;
import dsc.domain.SourceRecord;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class SourceDao {
    private final Repository repo;
    public SourceDao(Repository repo) { this.repo = repo; }
    private Connection cx() { return repo.cx(); }

    public void insert(SourceRecord s) {
        try (PreparedStatement ps = cx().prepareStatement(
                "INSERT INTO source_record(id,version,sample_id,segment_id,kind,is_current,raw_text,unit,"
              + "numeric_value,numeric_valid,payload_json,origin_ref,created_by,supersedes_id,created_at)"
              + " VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)")) {
            ps.setString(1, s.id); ps.setInt(2, s.version); ps.setString(3, s.sampleId);
            ps.setString(4, s.segmentId); ps.setString(5, s.kind.name());
            ps.setInt(6, s.current ? 1 : 0); ps.setString(7, s.rawText); ps.setString(8, s.unit);
            if (s.numericValid) ps.setDouble(9, s.numericValue); else ps.setNull(9, Types.REAL);
            ps.setInt(10, s.numericValid ? 1 : 0); ps.setString(11, s.payloadJson);
            ps.setString(12, s.originRef); ps.setString(13, s.createdBy);
            ps.setString(14, s.supersedesId); ps.setLong(15, s.createdAt);
            ps.executeUpdate();
        } catch (SQLException e) { throw new RuntimeException(e); }
    }

    public void clearCurrent(String id) {
        try (PreparedStatement ps = cx().prepareStatement(
                "UPDATE source_record SET is_current=0 WHERE id=?")) {
            ps.setString(1, id);
            ps.executeUpdate();
        } catch (SQLException e) { throw new RuntimeException(e); }
    }

    public SourceRecord current(String id) {
        List<SourceRecord> all = versions(id);
        for (SourceRecord s : all) if (s.current) return s;
        return all.isEmpty() ? null : all.get(all.size() - 1);
    }

    public List<SourceRecord> versions(String id) {
        List<SourceRecord> out = new ArrayList<>();
        try (PreparedStatement ps = cx().prepareStatement(
                "SELECT * FROM source_record WHERE id=? ORDER BY version")) {
            ps.setString(1, id);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) out.add(map(rs));
        } catch (SQLException e) { throw new RuntimeException(e); }
        return out;
    }

    public int nextVersion(String id) {
        try (PreparedStatement ps = cx().prepareStatement(
                "SELECT coalesce(max(version),0)+1 FROM source_record WHERE id=?")) {
            ps.setString(1, id);
            ResultSet rs = ps.executeQuery(); rs.next();
            return rs.getInt(1);
        } catch (SQLException e) { throw new RuntimeException(e); }
    }

    public List<SourceRecord> currentForSample(String sampleId) {
        List<SourceRecord> out = new ArrayList<>();
        try (PreparedStatement ps = cx().prepareStatement(
                "SELECT * FROM source_record WHERE sample_id=? AND is_current=1")) {
            ps.setString(1, sampleId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) out.add(map(rs));
        } catch (SQLException e) { throw new RuntimeException(e); }
        return out;
    }

    public List<SourceRecord> currentForSegment(String segmentId) {
        List<SourceRecord> out = new ArrayList<>();
        try (PreparedStatement ps = cx().prepareStatement(
                "SELECT * FROM source_record WHERE segment_id=? AND is_current=1")) {
            ps.setString(1, segmentId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) out.add(map(rs));
        } catch (SQLException e) { throw new RuntimeException(e); }
        return out;
    }

    private SourceRecord map(ResultSet rs) throws SQLException {
        SourceRecord s = new SourceRecord();
        s.id = rs.getString("id"); s.version = rs.getInt("version");
        s.sampleId = rs.getString("sample_id"); s.segmentId = rs.getString("segment_id");
        s.kind = Enums.SourceKind.valueOf(rs.getString("kind"));
        s.current = rs.getInt("is_current") == 1;
        s.rawText = rs.getString("raw_text"); s.unit = rs.getString("unit");
        s.numericValid = rs.getInt("numeric_valid") == 1;
        if (s.numericValid) s.numericValue = rs.getDouble("numeric_value");
        s.payloadJson = rs.getString("payload_json"); s.originRef = rs.getString("origin_ref");
        s.createdBy = rs.getString("created_by"); s.supersedesId = rs.getString("supersedes_id");
        s.createdAt = rs.getLong("created_at");
        return s;
    }
}
