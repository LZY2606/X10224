package dsc.store;

import dsc.json.Json;
import dsc.model.BaselineCandidate;
import dsc.model.EventRecord;
import dsc.model.SamplePoint;
import dsc.model.Segment;
import dsc.model.SourceRecord;
import dsc.model.Study;
import dsc.model.Transition;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * One unit-of-work over the archive: readers for the current/as-of states
 * and writers that add immutable versions and append ledger events. Pointer
 * flips happen in the same transaction as the new rows.
 */
public class Tx {

    final Connection c;
    private final Store store;

    Tx(Connection c, Store store) {
        this.c = c;
        this.store = store;
    }

    long headSeq(String studyId) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT head_seq FROM study WHERE id=?")) {
            ps.setString(1, studyId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 0;
            }
        }
    }

    // ------------------------------------------------------------- writes

    public void upsertStudy(Study study) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO study(id,material_code,description,created_at,head_seq,base_seq) "
                        + "VALUES(?,?,?,?,?,?) ON CONFLICT(id) DO UPDATE SET "
                        + "material_code=excluded.material_code, description=excluded.description")) {
            ps.setString(1, study.id());
            ps.setString(2, study.materialCode());
            ps.setString(3, study.description());
            ps.setString(4, study.createdAt());
            ps.setLong(5, study.headSeq());
            ps.setLong(6, study.baseSeq());
            ps.executeUpdate();
        }
    }

    public void bumpHead(String studyId, long newHead) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "UPDATE study SET head_seq=? WHERE id=?")) {
            ps.setLong(1, newHead);
            ps.setString(2, studyId);
            ps.executeUpdate();
        }
    }

    public void putSource(SourceRecord s) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO source_record(id,version,study_id,type,supersedes_id,segment_id,"
                        + "unit,value,raw_text,basis,recorded_at,recorded_by,retired,created_seq)"
                        + " VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?)")) {
            ps.setString(1, s.id());
            ps.setInt(2, s.version());
            ps.setString(3, s.studyId());
            ps.setString(4, s.type().name());
            ps.setString(5, s.supersedesId());
            ps.setString(6, s.segmentId());
            ps.setString(7, s.unit());
            ps.setDouble(8, s.value());
            ps.setString(9, s.rawText());
            ps.setString(10, s.basis());
            ps.setString(11, s.recordedAt());
            ps.setString(12, s.recordedBy());
            ps.setInt(13, s.retired() ? 1 : 0);
            ps.setInt(14, s.createdSeq());
            ps.executeUpdate();
        }
        try (PreparedStatement dp = c.prepareStatement(
                "DELETE FROM raw_point WHERE source_id=? AND version=?")) {
            dp.setString(1, s.id());
            dp.setInt(2, s.version());
            dp.executeUpdate();
        }
        try (PreparedStatement pp = c.prepareStatement(
                "INSERT INTO raw_point(source_id,version,idx,temp_value,temp_unit,temp_raw,"
                        + "temp_convertible,hf_value,hf_unit,hf_raw,hf_convertible,diagnostic)"
                        + " VALUES(?,?,?,?,?,?,?,?,?,?,?,?)")) {
            for (SamplePoint p : s.points()) {
                pp.setString(1, s.id());
                pp.setInt(2, s.version());
                pp.setInt(3, p.index());
                setNullableDouble(pp, 4, p.temperature().convertible()
                        ? p.temperature().value() : Double.NaN);
                pp.setString(5, p.temperature().unit());
                pp.setString(6, p.temperature().raw());
                pp.setInt(7, p.temperature().convertible() ? 1 : 0);
                setNullableDouble(pp, 8, p.heatFlow().convertible()
                        ? p.heatFlow().value() : Double.NaN);
                pp.setString(9, p.heatFlow().unit());
                pp.setString(10, p.heatFlow().raw());
                pp.setInt(11, p.heatFlow().convertible() ? 1 : 0);
                pp.setString(12, p.diagnostic());
                pp.addBatch();
            }
            pp.executeBatch();
        }
    }

    private static void setNullableDouble(PreparedStatement ps, int idx, double v)
            throws SQLException {
        if (Double.isNaN(v)) ps.setObject(idx, null);
        else ps.setDouble(idx, v);
    }

    public void putSegment(Segment seg) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO segment(id,study_id,ord,cycle,direction,direction_basis,"
                        + "trace_source_id,rate_source_id,atmosphere_source_id,"
                        + "calibration_source_id,t_start_k,t_end_k,point_count,"
                        + "reversed_signal,non_uniform,created_seq) "
                        + "VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?) "
                        + "ON CONFLICT(id) DO UPDATE SET direction=excluded.direction,"
                        + "direction_basis=excluded.direction_basis,"
                        + "calibration_source_id=excluded.calibration_source_id,"
                        + "reversed_signal=excluded.reversed_signal,"
                        + "non_uniform=excluded.non_uniform")) {
            ps.setString(1, seg.id());
            ps.setString(2, seg.studyId());
            ps.setInt(3, seg.order());
            ps.setString(4, seg.cycle());
            ps.setString(5, seg.direction().name());
            ps.setString(6, seg.directionBasis());
            ps.setString(7, seg.traceSourceId());
            ps.setString(8, seg.rateSourceId());
            ps.setString(9, seg.atmosphereSourceId());
            ps.setString(10, seg.calibrationSourceId());
            ps.setDouble(11, seg.tStartK());
            ps.setDouble(12, seg.tEndK());
            ps.setInt(13, seg.pointCount());
            ps.setInt(14, seg.reversedSignal() ? 1 : 0);
            ps.setInt(15, seg.nonUniform() ? 1 : 0);
            ps.setInt(16, headSeq(seg.studyId()));
            ps.executeUpdate();
        }
        try (PreparedStatement dp = c.prepareStatement(
                "DELETE FROM segment_diag WHERE segment_id=?")) {
            dp.setString(1, seg.id());
            dp.executeUpdate();
        }
        try (PreparedStatement ip = c.prepareStatement(
                "INSERT INTO segment_diag(segment_id,idx,text) VALUES(?,?,?)")) {
            int i = 0;
            for (String d : seg.diagnostics()) {
                ip.setString(1, seg.id());
                ip.setInt(2, i++);
                ip.setString(3, d);
                ip.addBatch();
            }
            ip.executeBatch();
        }
    }

    public void putBaseline(BaselineCandidate b) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO baseline_candidate(id,version,study_id,segment_id,method,"
                        + "left_t_k,right_t_k,left_hf_w,right_hf_w,slope_w_per_k,intercept_w,"
                        + "status,decided_by,depends_on,created_seq,decided_seq) "
                        + "VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)")) {
            ps.setString(1, b.id());
            ps.setInt(2, b.version());
            ps.setString(3, b.studyId());
            ps.setString(4, b.segmentId());
            ps.setString(5, b.method().name());
            ps.setDouble(6, b.leftTK());
            ps.setDouble(7, b.rightTK());
            ps.setDouble(8, b.leftHF());
            ps.setDouble(9, b.rightHF());
            ps.setDouble(10, b.slopeWPerK());
            ps.setDouble(11, b.interceptW());
            ps.setString(12, b.status().name());
            ps.setString(13, b.decidedBy());
            ps.setString(14, Json.write(b.dependsOn()));
            ps.setInt(15, b.createdSeq());
            ps.setInt(16, b.decidedSeq());
            ps.executeUpdate();
        }
        try (PreparedStatement dp = c.prepareStatement(
                "DELETE FROM baseline_diag WHERE baseline_id=? AND version=?")) {
            dp.setString(1, b.id());
            dp.setInt(2, b.version());
            dp.executeUpdate();
        }
        try (PreparedStatement ip = c.prepareStatement(
                "INSERT INTO baseline_diag(baseline_id,version,idx,text) VALUES(?,?,?,?)")) {
            int i = 0;
            for (String d : b.diagnostics()) {
                ip.setString(1, b.id());
                ip.setInt(2, b.version());
                ip.setInt(3, i++);
                ip.setString(4, d);
                ip.addBatch();
            }
            ip.executeBatch();
        }
    }

    public void putTransition(Transition t) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO transition(id,version,study_id,segment_id,baseline_id,kind,"
                        + "onset_t_k,peak_t_k,endset_t_k,enthalpy_jg,enthalpy_error_jg,"
                        + "depends_on,stale,created_seq,stale_seq) "
                        + "VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)")) {
            ps.setString(1, t.id());
            ps.setInt(2, t.version());
            ps.setString(3, t.studyId());
            ps.setString(4, t.segmentId());
            ps.setString(5, t.baselineId());
            ps.setString(6, t.kind().name());
            ps.setDouble(7, t.onsetTK());
            ps.setDouble(8, t.peakTK());
            ps.setDouble(9, t.endsetTK());
            setNullableDouble(ps, 10, t.enthalpyJg());
            setNullableDouble(ps, 11, t.enthalpyErrorJg());
            ps.setString(12, Json.write(t.dependsOn()));
            ps.setInt(13, t.stale() ? 1 : 0);
            ps.setInt(14, t.createdSeq());
            ps.setInt(15, t.staleSeq());
            ps.executeUpdate();
        }
        try (PreparedStatement dp = c.prepareStatement(
                "DELETE FROM transition_error WHERE transition_id=? AND version=?")) {
            dp.setString(1, t.id());
            dp.setInt(2, t.version());
            dp.executeUpdate();
        }
        try (PreparedStatement ip = c.prepareStatement(
                "INSERT INTO transition_error(transition_id,version,idx,code,description,"
                        + "contribution_jg,source_id) VALUES(?,?,?,?,?,?,?)")) {
            int i = 0;
            for (Transition.ErrorSource e : t.errorSources()) {
                ip.setString(1, t.id());
                ip.setInt(2, t.version());
                ip.setInt(3, i++);
                ip.setString(4, e.code());
                ip.setString(5, e.description());
                ip.setDouble(6, e.contributionJg());
                ip.setString(7, e.sourceId());
                ip.addBatch();
            }
            ip.executeBatch();
        }
    }

    public long appendEvent(String studyId, String type, String actor, String subjectId,
                            String ruleCode, String rationale, List<String> affectedIds,
                            Map<String, Object> payload) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO event_log(study_id,type,actor,subject_id,rule_code,rationale,"
                        + "affected_ids,payload,at) VALUES(?,?,?,?,?,?,?,?,?)",
                Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, studyId);
            ps.setString(2, type);
            ps.setString(3, actor);
            ps.setString(4, subjectId);
            ps.setString(5, ruleCode);
            ps.setString(6, rationale);
            ps.setString(7, Json.write(affectedIds == null ? List.of() : affectedIds));
            ps.setString(8, payload == null ? null : Json.write(payload));
            ps.setString(9, Instant.now().toString());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                long seq = keys.getLong(1);
                bumpHead(studyId, seq);
                return seq;
            }
        }
    }

    public void setPointer(String studyId, String kind, String logicalId, int version)
            throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO current_pointer(study_id,kind,logical_id,version) VALUES(?,?,?,?)"
                        + " ON CONFLICT(study_id,kind,logical_id) DO UPDATE SET "
                        + "version=excluded.version")) {
            ps.setString(1, studyId);
            ps.setString(2, kind);
            ps.setString(3, logicalId);
            ps.setInt(4, version);
            ps.executeUpdate();
        }
    }

    public int pointer(String studyId, String kind, String logicalId) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT version FROM current_pointer WHERE study_id=? AND kind=? AND logical_id=?")) {
            ps.setString(1, studyId);
            ps.setString(2, kind);
            ps.setString(3, logicalId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : -1;
            }
        }
    }

    public void addDepEdge(String studyId, String nodeId, String dependencyId, String kind)
            throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT OR IGNORE INTO dep_edge(study_id,node_id,dependency_id,node_kind)"
                        + " VALUES(?,?,?,?)")) {
            ps.setString(1, studyId);
            ps.setString(2, nodeId);
            ps.setString(3, dependencyId);
            ps.setString(4, kind);
            ps.executeUpdate();
        }
    }

    public Map<String, List<String>> loadDepGraph(String studyId) throws SQLException {
        Map<String, List<String>> graph = new LinkedHashMap<>();
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT node_id,dependency_id FROM dep_edge WHERE study_id=?")) {
            ps.setString(1, studyId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    graph.computeIfAbsent(rs.getString(1), k -> new ArrayList<>())
                            .add(rs.getString(2));
                }
            }
        }
        return graph;
    }

    // ------------------------------------------------------------- readers

    public List<SourceRecord> currentSources(String studyId) throws SQLException {
        List<SourceRecord> out = new ArrayList<>();
        String sql = "SELECT * FROM source_record sr WHERE study_id=? AND version=("
                + "SELECT COALESCE(MAX(version),0) FROM source_record WHERE id=sr.id)"
                + " ORDER BY id";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, studyId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) out.add(Store.mapSource(rs, c));
            }
        }
        return out;
    }

    /** Version of a source that was current at a given ledger seq. */
    public SourceRecord sourceAsOf(String sourceId, long asOfSeq) throws SQLException {
        String sql = "SELECT * FROM source_record WHERE id=? AND created_seq<=? "
                + "ORDER BY version DESC LIMIT 1";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, sourceId);
            ps.setLong(2, asOfSeq);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Store.mapSource(rs, c) : null;
            }
        }
    }

    public SourceRecord sourceVersion(String sourceId, int version) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT * FROM source_record WHERE id=? AND version=?")) {
            ps.setString(1, sourceId);
            ps.setInt(2, version);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Store.mapSource(rs, c) : null;
            }
        }
    }

    public int latestSourceVersion(String sourceId) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT COALESCE(MAX(version),0) FROM source_record WHERE id=?")) {
            ps.setString(1, sourceId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    public List<Segment> segments(String studyId) throws SQLException {
        List<Segment> out = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT * FROM segment WHERE study_id=? ORDER BY ord")) {
            ps.setString(1, studyId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) out.add(Store.mapSegment(rs, c));
            }
        }
        return out;
    }

    /** Current version of every baseline logical id. */
    public List<BaselineCandidate> currentBaselines(String studyId) throws SQLException {
        List<BaselineCandidate> out = new ArrayList<>();
        String sql = "SELECT * FROM baseline_candidate bc WHERE study_id=? AND version=("
                + "SELECT COALESCE(MAX(version),0) FROM baseline_candidate WHERE id=bc.id)"
                + " ORDER BY segment_id,id";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, studyId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) out.add(Store.mapBaseline(rs, c));
            }
        }
        return out;
    }

    public List<BaselineCandidate> baselineVersions(String baselineId) throws SQLException {
        List<BaselineCandidate> out = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT * FROM baseline_candidate WHERE id=? ORDER BY version")) {
            ps.setString(1, baselineId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) out.add(Store.mapBaseline(rs, c));
            }
        }
        return out;
    }

    public List<Transition> currentTransitions(String studyId) throws SQLException {
        List<Transition> out = new ArrayList<>();
        String sql = "SELECT * FROM transition tr WHERE study_id=? AND version=("
                + "SELECT COALESCE(MAX(version),0) FROM transition WHERE id=tr.id)"
                + " ORDER BY segment_id,id";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, studyId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) out.add(Store.mapTransition(rs, c));
            }
        }
        return out;
    }

    public List<Transition> transitionVersions(String transitionId) throws SQLException {
        List<Transition> out = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT * FROM transition WHERE id=? ORDER BY version")) {
            ps.setString(1, transitionId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) out.add(Store.mapTransition(rs, c));
            }
        }
        return out;
    }
}
