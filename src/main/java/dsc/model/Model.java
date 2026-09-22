package dsc.model;

import java.util.ArrayList;
import java.util.List;

/** Domain model. Every physical quantity carries a unit; raw import keeps the
 *  original textual representation, normalization happens only in the derived layer. */
public final class Model {

    /** A physical quantity with unit and coordinate basis (e.g. sample temperature vs furnace temperature). */
    public record Quantity(double value, String unit, String basis) {
        public Quantity(double value, String unit) { this(value, unit, ""); }
        @Override public String toString() { return value + " " + unit + (basis.isEmpty() ? "" : " [" + basis + "]"); }
    }

    /** A value that could not be converted to canonical units: kept in place, with a diagnostic. */
    public record Diagnostic(String code, String message, String location) {
        @Override public String toString() { return code + " @ " + location + ": " + message; }
    }

    /** One raw sample point exactly as imported (raw strings preserved). */
    public record RawPoint(double timeS, Quantity temperature, Quantity heatFlow, String rawLine) {}

    public enum SegmentKind { HEATING, COOLING, ISOTHERM }

    /** A monotonic temperature segment. Heating and cooling segments are never mixed. */
    public static final class Segment {
        public final String id;
        public final SegmentKind kind;
        public final List<RawPoint> points;
        public final Quantity scanRate; // signed: + heating, - cooling
        public Segment(String id, SegmentKind kind, List<RawPoint> points, Quantity scanRate) {
            this.id = id; this.kind = kind; this.points = points; this.scanRate = scanRate;
        }
        public double tStart() { return points.get(0).temperature().value(); }
        public double tEnd() { return points.get(points.size() - 1).temperature().value(); }
    }

    /** Heat-flow sign convention, declared by instrument metadata — never guessed from file names. */
    public enum SignalConvention { EXO_UP, EXO_DOWN, UNKNOWN }

    public record InstrumentHeader(
            String instrument, SignalConvention convention,
            String heatFlowUnit, String temperatureUnit, String timeUnit,
            List<Diagnostic> diagnostics) {}

    public enum TransitionType { GLASS_TRANSITION, CRYSTALLIZATION, MELTING }

    public enum BaselineMethod { STRAIGHT, TANGENT, SIGMOID }

    /** A baseline candidate. Multiple plausible baselines coexist; rejection never deletes. */
    public static final class Baseline {
        public String id;
        public BaselineMethod method;
        public double tStart, tEnd;          // interval in temperature coordinate
        public double[] coeff;               // line: {slope, intercept}; sigmoid: {y0,y1,tm,width}
        public Status status = Status.CANDIDATE;
        public List<Diagnostic> diagnostics = new ArrayList<>();
        public enum Status { CANDIDATE, ACCEPTED, REJECTED }
        public double yAt(double t) {
            return switch (method) {
                case STRAIGHT, TANGENT -> coeff[0] * t + coeff[1];
                case SIGMOID -> coeff[0] + (coeff[1] - coeff[0]) / (1.0 + Math.exp(-(t - coeff[2]) / coeff[3]));
            };
        }
    }

    /** Error-source breakdown for an enthalpy value. */
    public record ErrorSources(double massUncertainty, double calibrationUncertainty,
                               double baselineSpread, double total) {}

    /** Integration result over a transition interval. */
    public record IntegrationResult(
            TransitionType type,
            Quantity onset, Quantity peak, Quantity endset,
            Quantity enthalpy,          // normalized (J/g) when mass available, else mJ with diagnostic
            boolean normalized,
            ErrorSources errorSources,
            List<Diagnostic> diagnostics) {}
}
