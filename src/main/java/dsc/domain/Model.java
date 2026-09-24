package dsc.domain;

import java.util.List;
import java.util.Map;

/** Core domain vocabulary for the DSC baseline / phase-transition reviewer. */
public final class Model {
    private Model() {}

    /** Source kinds, each archived separately with its own version line. */
    public enum SourceKind {
        INSTRUMENT_OUTPUT,   // raw instrument export (CSV text kept verbatim)
        SAMPLE_MASS,         // mg, with uncertainty
        SCAN_RATE,           // K/min
        ATMOSPHERE,          // gas + flow
        TEMP_CALIBRATION,    // affine correction T_corr = a*T + b
        TANGENT_SELECTION    // user-chosen interval + tangent anchors
    }

    public enum SegmentKind { HEATING, COOLING }

    /** Heat-flow sign convention. Never guessed from file names. */
    public enum Direction { ENDO_UP, EXO_UP, UNKNOWN }

    public enum DerivationKind { NORMALIZED_SERIES, BASELINE_CANDIDATES, INTEGRATION }

    public enum DerivationStatus { BUILDING, COMPLETE, STALE }

    /** One dependency edge: a derivation input pinned to an exact version. */
    public record InputRef(String kind, String key, String id, long version) {}

    /** A point of the normalized series. Unconvertible values stay as NaN with diagnostics. */
    public record SeriesPoint(double tempC, double timeS, double heatFlowMw, double normMwPerMg) {}

    public record BaselineCandidate(
            String candidateId,   // stable identity, e.g. "cand-linear"
            String kind,          // linear | tangent | step
            Map<String, Double> params,
            String rationale) {}

    public record ErrorBudget(
            double baselineSpreadJg,
            double noiseJg,
            double massJg,
            double calibrationJg) {}

    public record IntegralResult(
            String candidateId,
            double onsetC, double peakC, double endsetC,
            double enthalpyJg,
            String thermalSense,   // ENDOTHERMIC | EXOTHERMIC | AMBIGUOUS
            ErrorBudget error) {}

    public record Diagnostic(String code, String message, Map<String, Object> context) {}

    public record Segment(String id, String sampleId, SegmentKind kind, int cycleIndex, int orderInCycle) {}

    public record Interval(String key, double fromC, double toC) {
        public List<Double> range() { return List.of(Math.min(fromC, toC), Math.max(fromC, toC)); }
    }
}
