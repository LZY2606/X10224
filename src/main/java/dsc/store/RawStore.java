package dsc.store;

import dsc.domain.Direction;
import dsc.domain.Qty;
import dsc.domain.RawPoint;
import dsc.domain.Segment;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Side-table persistence of raw instrument output attached to import events. */
public final class RawStore {

    private RawStore() {
    }

    public static void writeSegment(Connection conn, String eventId, Segment segment,
                                    String exoConvention) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO raw_segments(event_id,segment_id,label,source_file,cycle_index,"
                        + "declared_direction,exo_convention) VALUES(?,?,?,?,?,?,?)")) {
            ps.setString(1, eventId);
            ps.setString(2, segment.id());
            ps.setString(3, segment.label());
            ps.setString(4, segment.sourceFile());
            ps.setInt(5, segment.cycleIndex());
            ps.setString(6, segment.declaredDirection() == null ? null : segment.declaredDirection().code());
            ps.setString(7, exoConvention);
            ps.executeUpdate();
        }
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO raw_points(event_id,segment_id,idx,time_val,time_unit,"
                        + "temp_val,temp_unit,flow_val,flow_unit,valid,diagnostic) "
                        + "VALUES(?,?,?,?,?,?,?,?,?,?,?)")) {
            for (RawPoint p : segment.points()) {
                ps.setString(1, eventId);
                ps.setString(2, segment.id());
                ps.setInt(3, p.index());
                putQty(ps, 4, p.time());
                putQty(ps, 6, p.temperature());
                putQty(ps, 8, p.heatFlow());
                ps.setInt(10, p.valid() ? 1 : 0);
                ps.setString(11, p.diagnostic());
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    private static void putQty(PreparedStatement ps, int valIndex, Qty q) throws SQLException {
        if (q == null) {
            ps.setObject(valIndex, null);
            ps.setObject(valIndex + 1, null);
        } else {
            ps.setDouble(valIndex, q.originalValue());
            ps.setString(valIndex + 1, q.originalUnit());
        }
    }

    public record Loaded(Map<Segment, String> segments, List<String> diagnostics) {
    }

    public static Map<Segment, String> loadForEvents(Connection conn, List<String> eventIds)
            throws SQLException {
        Map<String, SegmentMeta> metas = new LinkedHashMap<>();
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT event_id,segment_id,label,source_file,cycle_index,declared_direction,"
                        + "exo_convention FROM raw_segments WHERE event_id=? ORDER BY rowid")) {
            for (String eventId : eventIds) {
                ps.setString(1, eventId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        String segId = rs.getString("segment_id");
                        metas.put(eventId + "|" + segId, new SegmentMeta(eventId, segId,
                                rs.getString("label"), rs.getString("source_file"),
                                rs.getInt("cycle_index"), rs.getString("declared_direction"),
                                rs.getString("exo_convention")));
                    }
                }
            }
        }
        Map<Segment, String> out = new LinkedHashMap<>();
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT idx,time_val,time_unit,temp_val,temp_unit,flow_val,flow_unit,valid,diagnostic "
                        + "FROM raw_points WHERE event_id=? AND segment_id=? ORDER BY idx")) {
            for (SegmentMeta m : metas.values()) {
                List<RawPoint> points = new ArrayList<>();
                ps.setString(1, m.eventId);
                ps.setString(2, m.segmentId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        Qty time = readQty(rs, "time_val", "time_unit");
                        Qty temp = readQty(rs, "temp_val", "temp_unit");
                        Qty flow = readQty(rs, "flow_val", "flow_unit");
                        points.add(new RawPoint(rs.getInt("idx"), time, temp, flow,
                                rs.getInt("valid") == 1, rs.getString("diagnostic")));
                    }
                }
                Direction declared = Direction.fromCode(m.declaredDirection);
                Segment seg = new Segment(m.segmentId, m.label, m.sourceFile,
                        m.cycleIndex, declared, points);
                out.put(seg, m.exoConvention);
            }
        }
        return out;
    }

    private static Qty readQty(ResultSet rs, String valCol, String unitCol) throws SQLException {
        double v = rs.getDouble(valCol);
        if (rs.wasNull()) {
            return Qty.of(Double.NaN, "");
        }
        String unit = rs.getString(unitCol);
        return Qty.of(v, unit);
    }

    private record SegmentMeta(String eventId, String segmentId, String label, String sourceFile,
                               int cycleIndex, String declaredDirection, String exoConvention) {
    }
}
