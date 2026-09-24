package dsc.engine;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dsc.domain.*;

import java.util.*;

/**
 * Materialized state of one branch head, rebuilt by replaying events in their
 * branch ancestry order. Purely derived information (normalized curves,
 * baselines, events) is attached by {@link Engine}; State itself only holds
 * the facts supplied by source and review events.
 */
public class State {
    public static final ObjectMapper M = new ObjectMapper();

    public final LinkedHashMap<String, ObjectNode> samples = new LinkedHashMap<>();
    public final LinkedHashMap<String, ObjectNode> curves = new LinkedHashMap<>();
    public final LinkedHashMap<String, ObjectNode> masses = new LinkedHashMap<>();
    public final LinkedHashMap<String, ObjectNode> calibrations = new LinkedHashMap<>();
    public final LinkedHashMap<String, ObjectNode> atmospheres = new LinkedHashMap<>();
    public final LinkedHashMap<String, ObjectNode> picks = new LinkedHashMap<>();
    public final LinkedHashMap<String, ObjectNode> intervals = new LinkedHashMap<>();
    public final LinkedHashMap<String, ObjectNode> patches = new LinkedHashMap<>();

    /** derivation key -> latest Derivation (content-addressed row). */
    public final LinkedHashMap<String, Derivation> current = new LinkedHashMap<>();
    /** every derivation version ever produced, keyed by its stable id. */
    public final LinkedHashMap<String, Derivation> allDerivations = new LinkedHashMap<>();

    public String currentMass(String sampleId) {
        String best = null; int v = -1;
        for (ObjectNode n : masses.values()) {
            if (n.path("sampleId").asText().equals(sampleId) && n.path("version").asInt() > v) {
                v = n.path("version").asInt(); best = n.path("id").asText();
            }
        }
        return best;
    }

    public String currentCalibration(String sampleId) {
        String best = null; int v = -1;
        for (ObjectNode n : calibrations.values()) {
            if (n.path("sampleId").asText().equals(sampleId) && n.path("version").asInt() > v) {
                v = n.path("version").asInt(); best = n.path("id").asText();
            }
        }
        return best;
    }

    public List<ObjectNode> curvesOf(String sampleId) {
        List<ObjectNode> r = new ArrayList<>();
        for (ObjectNode c : curves.values()) {
            if (c.path("sampleId").asText().equals(sampleId)) r.add(c);
        }
        r.sort(Comparator.comparingInt(c -> c.path("sequenceNo").asInt()));
        return r;
    }

    public ObjectNode mass(String id) { return masses.get(id); }
    public ObjectNode calibration(String id) { return calibrations.get(id); }
    public ObjectNode curve(String id) { return curves.get(id); }

    /** Replay source/review events (branch ancestry already ordered by the caller). */
    public void applyFacts(Event e) {
        ObjectNode p = (ObjectNode) e.payload();
        switch (e.type()) {
            case Event.SAMPLE_CREATED -> samples.put(p.path("id").asText(), p);
            case Event.CURVE_INGESTED -> curves.put(p.path("id").asText(), p);
            case Event.MASS_SET -> masses.put(p.path("id").asText(), p);
            case Event.CALIBRATION_SET -> calibrations.put(p.path("id").asText(), p);
            case Event.ATMOSPHERE_RECORDED -> atmospheres.put(p.path("id").asText(), p);
            case Event.TANGENT_PICKED -> picks.put(p.path("id").asText(), p);
            case Event.SOURCE_PATCHED -> {
                patches.put(p.path("sourceId").asText(), p);
                ObjectNode c = curves.get(p.path("sourceId").asText());
                if (c != null) {
                    c.set("points", p.path("points"));
                    c.set("gaps", p.path("gaps"));
                    c.put("curveVersion", p.path("curveVersion").asInt(c.path("curveVersion").asInt(1)));
                    c.put("pointsHash", p.path("pointsHash").asText(c.path("pointsHash").asText("")));
                }
            }
            case Event.INTERVAL_DRAWN, Event.INTERVAL_REFIT ->
                    intervals.put(p.path("id").asText(), p);
            case Event.BASELINE_ADOPTED, Event.BASELINE_REJECTED,
                 Event.FEATURE_ACCEPTED, Event.FEATURE_REJECTED -> {
                ObjectNode tgt = intervals.get(p.path("intervalId").asText());
                if (tgt != null) {
                    String field = switch (e.type()) {
                        case Event.BASELINE_ADOPTED -> "adoptedBaseline";
                        case Event.BASELINE_REJECTED -> "rejectedBaseline";
                        case Event.FEATURE_ACCEPTED -> "acceptedFeature";
                        default -> "rejectedFeature";
                    };
                    tgt.put(field, p.path("candidateId").asText());
                    tgt.put(field + "At", e.seq());
                }
            }
            default -> { /* branch/derived/judgment events do not mutate facts */ }
        }
    }

    public ArrayNode array() { return M.createArrayNode(); }
    public ObjectNode obj() { return M.createObjectNode(); }
}
