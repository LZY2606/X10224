package dsc;

import java.util.*;

/** Domain model. Every quantity carries a unit or coordinate basis. */
public final class Model {
    private Model() {}

    /** Raw instrument point: values kept exactly as imported, with unit basis. */
    public record Point(double timeS, double tempC, double heatFlowMw) {}

    public enum Direction { HEATING, COOLING, UNKNOWN }

    /** Heat-flow sign convention. NEVER inferred from file names. */
    public enum Convention {
        EXO_UP, ENDO_UP, UNKNOWN;
        public static Convention fromMetadata(String v) {
            if (v == null) return UNKNOWN;
            return switch (v.trim().toLowerCase(Locale.ROOT)) {
                case "exo-up", "exo_up", "exoup" -> EXO_UP;
                case "endo-up", "endo_up", "endoup" -> ENDO_UP;
                default -> UNKNOWN;
            };
        }
    }

    /** A contiguous scan segment (one heating or cooling ramp of one cycle). */
    public record Segment(String id, String sampleId, int seq, Direction direction,
                          double rateKPerMin, String atmosphere, List<Point> points) {
        public double tStart() { return points.isEmpty() ? 0 : points.get(0).tempC(); }
        public double tEnd() { return points.isEmpty() ? 0 : points.get(points.size() - 1).tempC(); }
    }

    /** User-selected interval on one segment, in temperature coordinates (degC). */
    public record Interval(String segmentId, double tLow, double tHigh) {
        public Interval normalized() { return tLow <= tHigh ? this : new Interval(segmentId, tHigh, tLow); }
    }

    @FunctionalInterface
    public interface BaselineFn { double valueAt(double tempC); }

    public record BaselineCandidate(String name, BaselineFn fn, String rationale) {}

    /** onset/peak/endset in degC; enthalpy with explicit unit and error sources. */
    public record EventResult(double onsetC, double peakC, double endsetC,
                              double enthalpyJPerG, String enthalpyUnit,
                              List<String> errorSources, List<String> diagnostics) {}

    /** Quantity that failed unit conversion: stays at raw position with a diagnostic. */
    public record Unconverted(String field, String rawValue, String unit, String reason) {}
}
