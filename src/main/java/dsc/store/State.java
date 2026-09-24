package dsc.store;

import java.util.*;

/** 事件投影出的某一时刻完整状态。stateAt(seq) 即时间旅行视图。 */
public final class State {
    public final Map<String, Models.Sample> samples = new LinkedHashMap<>();
    public final Map<String, Models.Segment> segments = new LinkedHashMap<>();
    public final Map<String, List<Models.MassVersion>> masses = new LinkedHashMap<>();
    public final Map<String, Models.Calibration> calibrations = new LinkedHashMap<>();
    public final Map<String, Models.Tangent> tangents = new LinkedHashMap<>();
    public final Map<String, Models.BaselineCandidate> baselines = new LinkedHashMap<>();
    public final Map<String, String> publishedAnalysis = new LinkedHashMap<>(); // subject -> derivedId
    public final Map<String, String> invalidated = new LinkedHashMap<>();       // subject -> reason
    public final List<Models.Diagnostic> diagnostics = new ArrayList<>();
    public final Map<String, Models.ConflictRec> conflicts = new LinkedHashMap<>();
    public final List<Models.Decision> decisions = new ArrayList<>();
    public final List<String> loadedCasePacks = new ArrayList<>();

    public Models.MassVersion currentMass(String sampleId) {
        List<Models.MassVersion> list = masses.get(sampleId);
        return (list == null || list.isEmpty()) ? null : list.get(list.size() - 1);
    }

    public List<Models.BaselineCandidate> baselinesFor(String segmentId, String kind) {
        List<Models.BaselineCandidate> out = new ArrayList<>();
        for (Models.BaselineCandidate b : baselines.values()) {
            if (b.segmentId().equals(segmentId) && b.kind().name().equals(kind)) out.add(b);
        }
        return out;
    }
}
