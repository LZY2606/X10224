package dsc.core;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/** Domain records + shared JSON helpers. All domain quantities carry a unit or coordinate basis. */
public final class Model {
    public static final ObjectMapper JSON = new ObjectMapper();

    private Model() {}

    // ---- source kinds (each archived separately) ----
    public static final String SRC_INSTRUMENT = "instrument_output";
    public static final String SRC_MASS = "sample_mass";
    public static final String SRC_RATE = "scan_rate";
    public static final String SRC_ATMOSPHERE = "atmosphere";
    public static final String SRC_CALIBRATION = "temperature_calibration";
    public static final String SRC_TANGENT = "tangent_selection";

    // ---- derived kinds ----
    public static final String DRV_NORMALIZED = "normalized_curve";
    public static final String DRV_BASELINE = "baseline_candidate";
    public static final String DRV_INTEGRATION = "integration";
    public static final String DRV_MARKS = "transition_marks";

    // ---- derived statuses ----
    public static final String ST_VALID = "valid";
    public static final String ST_STALE = "stale";
    public static final String ST_CANDIDATE = "candidate";
    public static final String ST_ADOPTED = "adopted";
    public static final String ST_REJECTED = "rejected";

    public record SourceArchive(String id, String kind, int version, String label, String unit,
                                String rawRepr, String payload, String supersedes, String createdAt) {}

    public record Segment(String id, String archiveId, String segType, int cycleIndex,
                          double rateKPerMin, String meta, int pointCount, double tMin, double tMax) {}

    public record DerivedObject(String id, String kind, String series, String segmentId, int version,
                                String status, String params, String result, String deps,
                                String explanation, long createdSeq) {}

    public record Event(long seq, String id, String kind, String payload, String createdAt) {}

    public record Pointer(String scope, String derivedId, long seq) {}

    public static ObjectNode obj() { return JSON.createObjectNode(); }

    public static JsonNode parse(String json) {
        try { return JSON.readTree(json); }
        catch (Exception e) { throw new IllegalStateException("bad json: " + e.getMessage(), e); }
    }

    public static String write(JsonNode node) {
        try { return JSON.writeValueAsString(node); }
        catch (Exception e) { throw new IllegalStateException(e); }
    }

    /** Stable map of depKind -> id, parsed from deps json. */
    public static Map<String, String> depsOf(String depsJson) {
        Map<String, String> out = new LinkedHashMap<>();
        JsonNode n = parse(depsJson);
        Iterator<Map.Entry<String, JsonNode>> it = n.fields();
        while (it.hasNext()) {
            Map.Entry<String, JsonNode> e = it.next();
            out.put(e.getKey(), e.getValue().asText());
        }
        return out;
    }
}
