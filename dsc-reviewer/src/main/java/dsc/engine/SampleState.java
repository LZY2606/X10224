package dsc.engine;

import dsc.model.Quant;
import dsc.model.RawPoint;
import dsc.model.SegmentDef;
import dsc.model.SourceMeta;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Folded view of the event log at one seq. Only direct facts live here; all computed quantities
 * are produced by {@link IncrementalEngine} and cached as derivation nodes.
 */
public final class SampleState {
    public String caseId;
    public String title;
    public long seq;

    public final List<SourceMeta> sources = new ArrayList<>();
    public final List<MassVersion> massVersions = new ArrayList<>();
    public final List<CalibrationVersion> calibrationVersions = new ArrayList<>();
    public final List<AtmosphereRecord> atmosphereVersions = new ArrayList<>();

    /** segment id -> ordered immutable versions (import, then patches) */
    public final Map<String, List<SegmentVersion>> segments = new LinkedHashMap<>();
    /** segment id -> ordered rate revisions */
    public final Map<String, List<RateVersion>> rates = new LinkedHashMap<>();
    /** interval id -> ordered drag versions */
    public final Map<String, List<IntervalVersion>> intervals = new LinkedHashMap<>();
    /** interval id -> side -> tangent versions */
    public final Map<String, Map<String, List<TangentVersion>>> tangents = new LinkedHashMap<>();
    /** interval id -> method -> latest verdict */
    public final Map<String, Map<String, BaselineVerdict>> decisions = new LinkedHashMap<>();
    /** conflict id -> resolution */
    public final Map<String, ConflictResolution> conflictResolutions = new LinkedHashMap<>();
    public final List<String> notes = new ArrayList<>();

    public SegmentVersion currentSegment(String id) {
        List<SegmentVersion> versions = segments.get(id);
        return versions == null || versions.isEmpty() ? null : versions.get(versions.size() - 1);
    }

    public MassVersion currentMass() {
        return massVersions.isEmpty() ? null : massVersions.get(massVersions.size() - 1);
    }

    public CalibrationVersion currentCalibration() {
        return calibrationVersions.isEmpty() ? null
                : calibrationVersions.get(calibrationVersions.size() - 1);
    }

    public RateVersion currentRate(String segmentId) {
        List<RateVersion> list = rates.get(segmentId);
        if (list != null && !list.isEmpty()) return list.get(list.size() - 1);
        SegmentVersion seg = currentSegment(segmentId);
        return seg == null ? null
                : new RateVersion(seg.eventId, 0, seg.def.declaredRate(), "DECLARED");
    }

    public IntervalVersion currentInterval(String id) {
        List<IntervalVersion> list = intervals.get(id);
        return list == null || list.isEmpty() ? null : list.get(list.size() - 1);
    }

    public TangentVersion currentTangent(String intervalId, String side) {
        Map<String, List<TangentVersion>> bySide = tangents.get(intervalId);
        if (bySide == null) return null;
        List<TangentVersion> list = bySide.get(side);
        return list == null || list.isEmpty() ? null : list.get(list.size() - 1);
    }

    public record MassVersion(String eventId, int version, Quant mass,
                              Quant balanceUncertainty, String sourceId) {
    }

    public record CalibrationVersion(String eventId, int version, Quant offset,
                                     Quant uncertainty, String sourceId) {
    }

    public record AtmosphereRecord(String eventId, String gas, Quant flow, String sourceId) {
    }

    public record SegmentVersion(String eventId, String kind, int version,
                                 String parentEventId, SegmentDef def, String reason) {
    }

    public record RateVersion(String eventId, int version, Quant rate, String origin) {
    }

    public record IntervalVersion(String eventId, String intervalId, String segmentId,
                                  double t0, double t1, String kind) {
    }

    public record TangentVersion(String eventId, String intervalId, String side,
                                 double anchorT0, double anchorT1) {
    }

    public record BaselineVerdict(String eventId, String method, String verdict, String actor) {
    }

    public record ConflictResolution(String eventId, String conflictId,
                                     String resolution, String keptEventId, String actor) {
    }

    /** Convenience used by patches: copy current raw rows. */
    public static List<RawPoint> copyRows(List<RawPoint> rows) {
        return new ArrayList<>(rows);
    }
}
