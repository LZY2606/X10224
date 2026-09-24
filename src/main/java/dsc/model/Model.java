package dsc.model;

import java.util.List;

/** Domain model for the DSC baseline / phase-transition reviewer. */
public final class Model {
    private Model() {}

    /** The six separately-archived source categories. */
    public enum SourceKind {
        INSTRUMENT_OUTPUT, SAMPLE_MASS, SCAN_RATE, ATMOSPHERE, TEMP_CALIBRATION, USER_TANGENT
    }

    /** Segment direction is derived from the sign of dT/dt in the data, never from file names. */
    public enum Direction { HEATING, COOLING }

    /** Instrument sign convention, declared inside the instrument output header. */
    public enum SignConvention { EXO_UP, ENDO_UP }

    public enum ThermalEventKind { GLASS_TRANSITION, CRYSTALLIZATION, MELTING }

    public enum BaselineType { LINEAR, LEFT_TANGENT, RIGHT_TANGENT, SIGMOIDAL }

    /** Unselected candidates are retained, never overwritten. */
    public enum CandidateStatus { CANDIDATE, ACCEPTED, REJECTED, SUPERSEDED }

    public enum DerivationStatus { BUILDING, COMPLETE, STALE, EMPTY }

    /** A versioned, immutable source record. Raw text is preserved as imported. */
    public record SourceRecord(String id, SourceKind kind, int version, String raw,
                               String unit, String meta, long createdAt) {}

    /** One constant-ramp segment parsed from an instrument output. */
    public record Segment(String id, String outputId, int outputVersion, Direction direction,
                          double[] tempC, double[] heatFlowMw, SignConvention sign,
                          String calibrationId, int calibrationVersion) {}

    /** A user-defined thermal event region on one segment. */
    public record ThermalEvent(String id, String segmentId, ThermalEventKind kind,
                               double tStart, double tEnd) {}

    /** One plausible baseline. Multiple candidates coexist. */
    public record BaselineCandidate(String id, String eventId, BaselineType type,
                                    CandidateStatus status, double slope, double intercept,
                                    String params) {}

    /** A versioned derivation node in the dependency graph. */
    public record Derivation(String id, int version, String kind, List<String> inputs,
                             DerivationStatus status, String result, long createdAt) {}

    /** Machine-readable review event. */
    public record LogEvent(long seq, String id, String type, String actor,
                           String payload, Long parent, long createdAt) {}

    /** A resolvable conflict between two edits based on the same version. */
    public record Conflict(String id, String subject, long baseSeq, String branchA,
                           String branchB, String status, String resolution, long createdAt) {}
}
