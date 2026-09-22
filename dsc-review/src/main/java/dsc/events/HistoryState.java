package dsc.events;

import dsc.derive.BaselineResult;
import dsc.derive.FeatureResult;
import dsc.model.*;

import java.util.*;

/**
 * Reduced view of one sample history at a given seq. Every map keeps *all*
 * versions: a superseded derivation is retained for audit, and a separate
 * "current" view records which version the reviewer currently accepts.
 */
public final class HistoryState {

    public static final class TraceRef {
        public String segmentId;
        public String sourceFile;
        public int cycle;
        public SignalConvention convention = SignalConvention.UNKNOWN;
        public ScanSegment segment;
        public long importedSeq;
        public String rawDescriptor;
        public List<String> parseDiagnostics = new ArrayList<>();
        public String supersededBy;
    }

    public static final class RegionRef {
        public Region region;
        public long definedSeq;
        public String supersededBy;
    }

    public static final class TangentRef {
        public TangentSelection tangent;
        public long selectedSeq;
    }

    public final String historyId;
    public String name;
    public long headSeq;

    public final Map<String, TraceRef> traces = new LinkedHashMap<>();
    public final List<String> traceOrder = new ArrayList<>();
    public final Map<String, MassVersion> masses = new LinkedHashMap<>();
    public String currentMassId;
    public final Map<String, Atmosphere> atmospheres = new LinkedHashMap<>();
    public final Map<String, Calibration> calibrations = new LinkedHashMap<>();
    public String currentCalibrationId;
    public final Map<String, QuantityRef> rates = new LinkedHashMap<>();
    public final Map<String, RegionRef> regions = new LinkedHashMap<>();
    public final Map<String, TangentRef> tangents = new ArrayListMap();
    public final Map<String, BaselineResult> baselines = new LinkedHashMap<>();
    public final Map<String, FeatureResult> features = new LinkedHashMap<>();
    public final List<Event> events = new ArrayList<>();
    /** slot key regionId -> accepted baseline id (multiple baselines otherwise coexist). */
    public final Map<String, String> acceptedBaseline = new LinkedHashMap<>();
    /** rejected derivations are kept too: id -> rejection comment. */
    public final Map<String, String> rejected = new LinkedHashMap<>();

    public HistoryState(String historyId) { this.historyId = historyId; }

    public ScanSegment segment(String id) {
        TraceRef r = traces.get(id);
        return r == null ? null : r.segment;
    }

    public MassVersion currentMass() { return masses.get(currentMassId); }
    public Calibration currentCalibration() { return calibrations.get(currentCalibrationId); }

    public List<BaselineResult> baselinesForRegion(String regionId) {
        List<BaselineResult> out = new ArrayList<>();
        for (BaselineResult b : baselines.values()) {
            if (regionId.equals(b.regionId()) && !rejected.containsKey(b.id())) out.add(b);
        }
        return out;
    }

    public List<FeatureResult> featuresForRegion(String regionId) {
        List<FeatureResult> out = new ArrayList<>();
        for (FeatureResult f : features.values()) {
            if (regionId.equals(f.regionId()) && !rejected.containsKey(f.id())) out.add(f);
        }
        return out;
    }

    private static final class ArrayListMap extends LinkedHashMap<String, TangentRef> {}

    /** Named quantity reference used for declared scan rates. */
    public record QuantityRef(String id, dsc.core.Quantity value, String raw, long seq) {}
}
