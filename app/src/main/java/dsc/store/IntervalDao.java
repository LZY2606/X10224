package dsc.store;

import dsc.domain.Interval;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class IntervalDao {
    private final Repository repo;
    public IntervalDao(Repository repo) { this.repo = repo; }
    private Connection cx() { return repo.cx(); }

    public void insert(Interval i) {
        try (PreparedStatement ps = cx().prepareStatement(
                "INSERT INTO interval_tbl(id,version,sample_id,branch_id,segment_id,is_current,"
              + "t_start_c,t_end_c,t_start_raw,t_end_raw,raw_unit,label,created_by,created_at)"
              + " VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?)")) {
            ps.setString(1, i.id); ps.setInt(2, i.version); ps.setString(3, i.sampleId);
            ps.setString(4, i.branchId); ps.setString(5, i.segmentId);
            ps.setInt(6, i.current ? 1 : 0);
            ps.setDouble(7, i.tStartC); ps.setDouble(8, i.tEndC);
            ps.setDouble(9, i.tStartRaw); ps.setDouble(10, i.tEndRaw);
            ps.setString(11, i.rawUnit); ps.setString(12, i.label);
            ps.setString(13, i.createdBy); ps.setLong(14, i.createdAt);
            ps.executeUpdate();
        } catch (SQLException e) { throw new RuntimeException(e); }
    }

    public void clearCurrent(String id) {
        try (PreparedStatement ps = cx().prepareStatement(
                "UPDATE interval_tbl SET is_current=0 WHERE id=?")) {
            ps.setString(1, id);
            ps.executeUpdate();
        } catch (SQLException e) { throw new RuntimeException(e); }
    }

    public int nextVersion(String id) {
        try (PreparedStatement ps = cx().prepareStatement(
                "SELECT coalesce(max(version),0)+1 FROM interval_tbl WHERE id=?")) {
            ps.setString(1, id);
            ResultSet rs = ps.executeQuery(); rs.next();
            return rs.getInt(1);
        } catch (SQLException e) { throw new RuntimeException(e); }
    }

    public Interval current(String branchId, String id) {
        try (PreparedStatement ps = cx().prepareStatement(
                "SELECT * FROM interval_tbl WHERE branch_id=? AND id=? ORDER BY version DESC")) {
            ps.setString(1, branchId); ps.setString(2, id);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) if (rs.getInt("is_current") == 1) return map(rs);
            return null;
        } catch (SQLException e) { throw new RuntimeException(e); }
    }

    public Interval versionAt(String id, int version) {
        try (PreparedStatement ps = cx().prepareStatement(
                "SELECT * FROM interval_tbl WHERE id=? AND version=?")) {
            ps.setString(1, id); ps.setInt(2, version);
            ResultSet rs = ps.executeQuery();
            return rs.next() ? map(rs) : null;
        } catch (SQLException e) { throw new RuntimeException(e); }
    }

    public List<Interval> currentForBranch(String sampleId, String branchId) {
        List<Interval> out = new ArrayList<>();
        try (PreparedStatement ps = cx().prepareStatement(
                "SELECT * FROM interval_tbl WHERE sample_id=? AND branch_id=? AND is_current=1 ORDER BY t_start_c")) {
            ps.setString(1, sampleId); ps.setString(2, branchId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) out.add(map(rs));
        } catch (SQLException e) { throw new RuntimeException(e); }
        return out;
    }

    private Interval map(ResultSet rs) throws SQLException {
        Interval i = new Interval();
        i.id = rs.getString("id"); i.version = rs.getInt("version");
        i.sampleId = rs.getString("sample_id"); i.branchId = rs.getString("branch_id");
        i.segmentId = rs.getString("segment_id"); i.current = rs.getInt("is_current") == 1;
        i.tStartC = rs.getDouble("t_start_c"); i.tEndC = rs.getDouble("t_end_c");
        i.tStartRaw = rs.getDouble("t_start_raw"); i.tEndRaw = rs.getDouble("t_end_raw");
        i.rawUnit = rs.getString("raw_unit"); i.label = rs.getString("label");
        i.createdBy = rs.getString("created_by"); i.createdAt = rs.getLong("created_at");
        return i;
    }
}
