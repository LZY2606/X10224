package dsc.domain;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.List;

/**
 * One archived instrument segment (a heating or cooling DSC run).
 *
 * <p>Direction is never inferred from the file name: it is computed from the measured temperature
 * sequence. The instrument's sign convention is read from instrument metadata, not from the
 * exo/endo guess of a file name.
 */
public final class Segment {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final String segmentId;
    private final String label;
    private final String filename;
    private final int cycleIndex;
    private final String heatFlowConvention; // "exo_up" or "endo_up"
    private final String temperatureUnit;
    private final String heatFlowUnit;
    private final List<SamplePoint> points;
    private final String supersedes;
    private final List<String> diagnostics = new ArrayList<>();

    public Segment(String segmentId, String label, String filename, int cycleIndex,
                   String heatFlowConvention, String temperatureUnit, String heatFlowUnit,
                   List<SamplePoint> points, String supersedes, List<String> diagnostics) {
        this.segmentId = segmentId;
        this.label = label;
        this.filename = filename;
        this.cycleIndex = cycleIndex;
        this.heatFlowConvention = heatFlowConvention;
        this.temperatureUnit = temperatureUnit;
        this.heatFlowUnit = heatFlowUnit;
        this.points = points;
        this.supersedes = supersedes;
        if (diagnostics != null) {
            this.diagnostics.addAll(diagnostics);
        }
    }

    public String segmentId() {
        return segmentId;
    }

    public String label() {
        return label;
    }

    public String filename() {
        return filename;
    }

    public int cycleIndex() {
        return cycleIndex;
    }

    public String heatFlowConvention() {
        return heatFlowConvention;
    }

    public String temperatureUnit() {
        return temperatureUnit;
    }

    public String heatFlowUnit() {
        return heatFlowUnit;
    }

    public List<SamplePoint> points() {
        return points;
    }

    public String supersedes() {
        return supersedes;
    }

    public List<String> diagnostics() {
        return diagnostics;
    }

    public List<SamplePoint> usablePoints() {
        return points.stream().filter(SamplePoint::usable).toList();
    }

    /** Observed temperature direction ("heating" / "cooling") from usable measurements. */
    public String observedDirection() {
        List<SamplePoint> usable = usablePoints();
        if (usable.size() < 2) {
            return "unknown";
        }
        double first = usable.get(0).temperatureC();
        double last = usable.get(usable.size() - 1).temperatureC();
        if (Math.abs(last - first) < 1e-9) {
            return "isothermal";
        }
        return last > first ? "heating" : "cooling";
    }

    /** Heat flow in the endotherm-positive coordinate base (mW). */
    public double endoPositiveMw(SamplePoint point) {
        double signed = point.heatFlowMw();
        return "exo_up".equals(heatFlowConvention) ? -signed : signed;
    }

    /** True when the file name hints at a direction that contradicts the measured direction. */
    public boolean filenameDirectionMismatch() {
        String name = filename == null ? "" : filename.toLowerCase();
        String observed = observedDirection();
        if ("heating".equals(observed)) {
            return name.contains("cool") || name.contains("cooling") || name.contains("降温") || name.contains("冷却");
        }
        if ("cooling".equals(observed)) {
            return name.contains("heat") || name.contains("heating") || name.contains("升温") || name.contains("加热");
        }
        return false;
    }

    public ObjectNode toJson() {
        ObjectNode node = MAPPER.createObjectNode();
        node.put("segmentId", segmentId);
        node.put("label", label);
        node.put("filename", filename);
        node.put("cycleIndex", cycleIndex);
        node.put("heatFlowConvention", heatFlowConvention);
        node.put("temperatureUnit", temperatureUnit);
        node.put("heatFlowUnit", heatFlowUnit);
        if (supersedes != null) {
            node.put("supersedes", supersedes);
        }
        ArrayNode raw = node.putArray("points");
        for (SamplePoint p : points) {
            ObjectNode pn = raw.addObject();
            pn.put("index", p.index());
            pn.put("rawTime", p.rawTime());
            pn.put("rawTemperature", p.rawTemperature());
            pn.put("rawHeatFlow", p.rawHeatFlow());
        }
        ArrayNode diag = node.putArray("diagnostics");
        diagnostics.forEach(diag::add);
        return node;
    }

    public static Segment fromJson(JsonNode node) {
        List<SamplePoint> points = new ArrayList<>();
        String tUnit = node.get("temperatureUnit").asText();
        String qUnit = node.get("heatFlowUnit").asText();
        for (JsonNode p : node.get("points")) {
            points.add(SamplePoint.parse(p.get("index").asInt(),
                    p.get("rawTime").asText(), p.get("rawTemperature").asText(),
                    p.get("rawHeatFlow").asText(), tUnit, qUnit));
        }
        List<String> diag = new ArrayList<>();
        node.path("diagnostics").forEach(d -> diag.add(d.asText()));
        return new Segment(node.get("segmentId").asText(), node.get("label").asText(),
                node.path("filename").asText(""), node.path("cycleIndex").asInt(0),
                node.get("heatFlowConvention").asText(), tUnit, qUnit, points,
                node.hasNonNull("supersedes") ? node.get("supersedes").asText() : null, diag);
    }
}
