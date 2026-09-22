package dsc.domain;

import java.util.List;
import java.util.Map;

/** Domain model. Every domain quantity carries a unit or a coordinate basis,
 *  and the raw representation is preserved alongside any normalized value. */
public final class Model {

    /** A quantity as imported: raw text is never discarded; value/unit may be null
     *  when the raw text cannot be converted (it then stays in place with a diagnostic). */
    public record Quantity(String raw, Double value, String unit, String basis) {
        public static Quantity raw(String raw, String basis) {
            return new Quantity(raw, null, null, basis);
        }
        public static Quantity of(String raw, double value, String unit, String basis) {
            return new Quantity(raw, value, unit, basis);
        }
    }

    /** Kinds of separately archived source records. */
    public enum SourceKind {
        INSTRUMENT_OUTPUT,   // raw instrument export (curve file, original representation)
        SAMPLE_MASS,         // sample mass, versioned
        SCAN_RATE,           // heating/cooling rate
        ATMOSPHERE,          // purge gas and flow
        TEMP_CALIBRATION,    // temperature calibration constants
        TANGENT_SELECTION    // user selected tangents / intervals
    }

    /** Kinds of derived artifacts. Each derivation is an independent version;
     *  the current pointer switches only after a complete success. */
    public enum DerivationKind {
        SEGMENTATION,         // split instrument output into heating/cooling segments
        NORMALIZED_CURVE,     // mass-normalized, calibrated curve (derived layer only)
        BASELINE_CANDIDATES,  // all plausible baselines per transition, differences kept
        TRANSITION_INTEGRAL   // onset/peak/endset + enthalpy with error sources
    }

    public enum DerivationStatus { COMPLETE, STALE }

    public record SourceRecord(String id, String sampleId, SourceKind kind, int version,
                               String payloadJson, String createdAt, String supersedes) {}

    public record Derivation(String id, String sampleId, DerivationKind kind, int version,
                             DerivationStatus status, Map<String, String> inputs,
                             String payloadJson, String diagnosticsJson, String createdAt) {}

    /** A machine-readable judgment event: type + basis (why). */
    public record Event(String id, long seq, String sampleId, String type,
                        String basisJson, String createdAt) {}

    public record Conflict(String id, String sampleId, String kind, int baseVersion,
                           String leftDerivation, String rightDerivation, String status,
                           String resolution, String createdAt) {}

    /** A diagnostic attached to a derivation; unconvertible values stay in place. */
    public record Diagnostic(String code, String message, Map<String, Object> context) {}

    public record Segment(String id, String direction, int pointCount,
                          double tStart, double tEnd, String tempUnit,
                          List<double[]> points) {}

    private Model() {}
}
