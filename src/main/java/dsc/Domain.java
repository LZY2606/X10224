package dsc;

import java.util.List;
import java.util.Map;

/** Immutable domain records. Raw textual representations are always retained next to normalized values. */
public final class Domain {
    private Domain() {}

    /** A quantity as originally written plus its normalized numeric value and coordinate basis. */
    public record RQ(String raw, String unit, String basis, Double value) {}

    /** One sampled row; unconvertible fields stay as raw text with problems listed, the row is never dropped. */
    public record SP(int index, String timeRaw, String tempRaw, String flowRaw,
                     Double timeS, Double tempK, Double flowW, List<String> problems) {}

    public record Segment(String id, String role, RQ start, RQ end, List<SP> points) {}

    public record InstrumentDoc(String file, String convention, String instrument, List<Segment> segments) {}
    public record MassDoc(String file, RQ mass, String revision, String note) {}
    public record RateDoc(String file, Map<String, RQ> rates) {}
    public record AtmosphereDoc(String file, String gas, RQ flow) {}
    public record CalibrationDoc(String file, String reference, RQ tempOffset) {}
    public record TangentAnchor(String rangeId, RQ preTemp, RQ preFlow, RQ postTemp, RQ postFlow) {}
    public record TangentDoc(String file, List<TangentAnchor> anchors) {}

    /** Versioned object kinds. Stable ids never change across recompute; versions are immutable. */
    public static final class K {
        public static final String SRC = "SRC";
        public static final String RNG = "RNG";
        public static final String BAS = "BAS";
        public static final String FEA = "FEA";
        public static final String ENT = "ENT";
        public static final String NENT = "NENT";
        public static final String DEC = "DEC";
        private K() {}
    }

    public static final String CAT_INSTRUMENT = "INSTRUMENT_OUTPUT";
    public static final String CAT_MASS = "MASS";
    public static final String CAT_RATE = "RATE";
    public static final String CAT_ATMOSPHERE = "ATMOSPHERE";
    public static final String CAT_CALIBRATION = "TEMPERATURE_CALIBRATION";
    public static final String CAT_TANGENT = "USER_TANGENT";

    public record SourceObject(String category, Object doc) {}
    public record RangeObject(String rangeId, String segmentId, String origin,
                              RQ lo, RQ hi, int pointCount, boolean uniform, boolean empty,
                              List<String> overlapWith, List<String> diagnostics) {}
    public record BaselineObject(String rangeId, String method,
                                 double slopeWPerK, double interceptW, double rmsW,
                                 boolean valid, String note) {}
    public record FeatureObject(String rangeId, String method, String type, String direction,
                                Double onsetK, Double peakK, Double endsetK, Double stepW,
                                List<String> overlapWith, String ambiguity) {}
    public record ErrorTerm(String code, String label, double magnitudeJ, List<String> sourceChain) {}
    public record EnthalpyObject(String rangeId, String baselineId, String method,
                                 double energyJ, String integration, int pointCount,
                                 List<ErrorTerm> errors) {}
    public record NormEnthalpyObject(String rangeId, String enthalpyId, String massId,
                                     double jPerG, double energyJ, double massG) {}
    public record DecisionObject(String decisionId, String rangeId, String baselineId,
                                 String result, String rationale, String actor) {}

    public record Event(long seq, String id, String type, String actor, String summary,
                        List<String> affected, String judgmentKey, Boolean holds,
                        String detail, String branch, String conflictOf, long atVersion,
                        Map<String, Integer> snapshot) {}

    public record Diagnostic(String id, long eventSeq, String code, String severity,
                             String message, String objId, Integer objVer,
                             String rangeId, String segmentId) {}
}
