package dsc.store;

import dsc.domain.CurvePoint;
import dsc.domain.Enums;
import dsc.domain.Segment;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class SegmentDao {
    private final Repository repo;
    public SegmentDao(Repository repo) { this.repo = repo; }
    private Connection cx() { return repo.cx(); }

    public void insertSegment(Segment s) {
        try (PreparedStatement ps = cx().prepareStatement(
                "INSERT INTO segment(id,sample_id,cycle_id,ord,role,direction,sign_convention,"
              + "t_unit,hf_unit,t_start_raw,t_end_raw,point_count,instrument_output_id,created_at)"
              + " VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?)")) {
            ps.setString(1, s.id); ps.setString(2, s.sampleId); ps.setString(3, s.cycleId);
            ps.setInt(4, s.order); ps.setString(5, s.role.name()); ps.setString(6, s.declaredDirection.name());
            ps.setString(7, s.signConvention.name()); ps.setString(8, s.tUnit); ps.setString(9, s.hfUnit);
            ps.setDouble(10, s.tStartRaw); ps.setDouble(11, s.tEndRaw);
            ps.setInt(12, s.pointCount); ps.setString(13, s.instrumentOutputId); ps.setLong(14, s.createdAt);
            ps.executeUpdate();
        } catch (SQLException e) { throw new RuntimeException(e); }
    }

    public List<Segment> listForSample(String sampleId) {
        List<Segment> out = new ArrayList<>();
        try (PreparedStatement ps = cx().prepareStatement(
                "SELECT * FROM segment WHERE sample_id=? ORDER BY ord")) {
            ps.setString(1, sampleId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) out.add(map(rs));
        } catch (SQLException e) { throw new RuntimeException(e); }
        return out;
    }

    public Segment get(String id) {
        try (PreparedStatement ps = cx().prepareStatement("SELECT * FROM segment WHERE id=?")) {
            ps.setString(1, id);
            ResultSet rs = ps.executeQuery();
            return rs.next() ? map(rs) : null;
        } catch (SQLException e) { throw new RuntimeException(e); }
    }

    public void insertPoints(String segmentId, List<CurvePoint> pts, int batchLimit) {
        String sql = "INSERT INTO curve_point(segment_id,idx,t_raw,t_raw_unit,hf_raw,hf_raw_unit,t_c,hf_wg,convertible)"
                   + " VALUES(?,?,?,?,?,?,?,?,?)";
        try (PreparedStatement ps = cx().prepareStatement(sql)) {
            int n = 0;
            for (CurvePoint p : pts) {
                ps.setString(1, segmentId); ps.setInt(2, p.idx);
                ps.setDouble(3, p.tRaw); ps.setString(4, p.tRawUnit);
                ps.setDouble(5, p.hfRaw); ps.setString(6, p.hfRawUnit);
                if (Double.isNaN(p.tC)) ps.setNull(7, Types.REAL); else ps.setDouble(7, p.tC);
                if (Double.isNaN(p.hfWg)) ps.setNull(8, Types.REAL); else ps.setDouble(8, p.hfWg);
                ps.setInt(9, p.convertible ? 1 : 0);
                ps.addBatch();
                if (++n % batchLimit == 0) ps.executeBatch();
            }
            ps.executeBatch();
        } catch (SQLException e) { throw new RuntimeException(e); }
    }

    public List<CurvePoint> points(String segmentId) {
        List<CurvePoint> out = new ArrayList<>();
        try (PreparedStatement ps = cx().prepareStatement(
                "SELECT * FROM curve_point WHERE segment_id=? ORDER BY idx")) {
            ps.setString(1, segmentId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                CurvePoint p = new CurvePoint();
                p.segmentId = segmentId; p.idx = rs.getInt("idx");
                p.tRaw = rs.getDouble("t_raw"); p.tRawUnit = rs.getString("t_raw_unit");
                p.hfRaw = rs.getDouble("hf_raw"); p.hfRawUnit = rs.getString("hf_raw_unit");
                Double t = (Double) rs.getObject("t_c");
                Double h = (Double) rs.getObject("hf_wg");
                p.tC = t == null ? Double.NaN : t;
                p.hfWg = h == null ? Double.NaN : h;
                p.convertible = rs.getInt("convertible") == 1;
                out.add(p);
            }
        } catch (SQLException e) { throw new RuntimeException(e); }
        return out;
    }

    private Segment map(ResultSet rs) throws SQLException {
        Segment s = new Segment();
        s.id = rs.getString("id"); s.sampleId = rs.getString("sample_id");
        s.cycleId = rs.getString("cycle_id"); s.order = rs.getInt("ord");
        s.role = Enums.SegmentRole.valueOf(rs.getString("role"));
        s.declaredDirection = Enums.Direction.valueOf(rs.getString("direction"));
        s.signConvention = Enums.SignConvention.valueOf(rs.getString("sign_convention"));
        s.tUnit = rs.getString("t_unit"); s.hfUnit = rs.getString("hf_unit");
        s.tStartRaw = rs.getDouble("t_start_raw"); s.tEndRaw = rs.getDouble("t_end_raw");
        s.pointCount = rs.getInt("point_count");
        s.instrumentOutputId = rs.getString("instrument_output_id");
        s.createdAt = rs.getLong("created_at");
        return s;
    }
}
