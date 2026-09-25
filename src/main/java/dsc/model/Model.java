package dsc.model;

import java.util.List;
import java.util.Map;

/** Domain model. Every artifact carries a stable id; quantities carry unit and coordinate basis. */
public final class Model {
    private Model() {}

    public enum Direction { HEATING, COOLING, ISOTHERMAL }

    public enum Status { CANDIDATE, ACCEPTED, REJECTED, STALE, DIAGNOSTIC }

    /** A physical quantity: value + unit + coordinate basis (e.g. temperature basis "C" or "K"). */
    public record Quantity(double value, String unit, String basis) {
        public String canonical() { return value + " " + unit + (basis.isEmpty() ? "" : " (" + basis + ")"); }
    }

    /** Raw point exactly as imported; units live on the owning series, never normalized here. */
    public record RawPoint(double time, double temperature, double heatFlow) {}

    /** Instrument output as archived: header fields and points in original units. */
    public record RawSeries(
            String id,
            String sourceName,
            Map<String, String> header,
            String tempUnit,
            String heatFlowUnit,
            String timeUnit,
            List<RawPoint> points) {}

    /** One monotonic scan segment; heating and cooling are never mixed. */
    public record Segment(
            String id,
            String seriesId,
            Direction direction,
            int cycleIndex,
            int startIndex,
            int endIndex,
            double rateKPerMin) {}

    /** Sample mass, versioned; revising mass invalidates every normalized derivative. */
    public record MassVersion(String id, String sampleId, Quantity mass, Double uncertainty, int revision) {}

    /** Temperature calibration against a reference material. */
    public record Calibration(String id, String referenceMaterial, double offsetC, double slope, String validForSeriesId) {}

    /** User-selected tangent, archived separately from instrument output. */
    public record Tangent(String id, String segmentId, String side, double anchorTemp, double slope, String author) {}

    /** A plausible baseline. Multiple reasonable baselines coexist; none is silently dropped. */
    public record BaselineCandidate(
            String id,
            String segmentId,
            String type,
            double t1,
            double t2,
            List<Double> coeff,
            Status status,
            int version) {}

    public record ErrorSource(String kind, String detail, double magnitudeJPerG) {}

    /** Onset/peak/endset + enthalpy for one transition under one baseline. */
    public record TransitionResult(
            String id,
            String baselineId,
            String segmentId,
            double onsetC,
            double peakC,
            double endsetC,
            double enthalpyJ,
            Double enthalpyJPerG,
            String endoExo,
            List<ErrorSource> errorSources,
            Status status) {}

    /** A value that could not be converted stays in place with a diagnostic attached. */
    public record Diagnostic(String id, String severity, String code, String message, String refId) {}

    /** Machine-readable judgement event. */
    public record Event(String id, long seq, String versionId, String kind, Map<String, Object> body) {}
}
