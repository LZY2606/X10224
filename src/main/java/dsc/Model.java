package dsc;

import java.util.List;

/** Domain records. Every domain quantity keeps its raw representation plus a
 *  normalized value; unconvertible values stay raw and get a diagnostic. */
public final class Model {
    private Model() {}

    public enum Segment { HEATING, COOLING }

    /** Endo/exo direction convention comes from instrument metadata, never filenames. */
    public enum DirectionConvention { ENDO_UP, ENDO_DOWN, UNKNOWN }

    public enum EventKind { GLASS_TRANSITION, CRYSTALLIZATION, MELTING }

    public enum BaselineStatus { CANDIDATE, ACCEPTED, REJECTED }

    public record MassVersion(String id, String sampleId, String rawValue, String rawUnit,
                              Double valueMg, String source, long seq, String supersedes) {}

    public record Calibration(String id, String instrument, String rawOffset, String rawSlope,
                              Double offsetC, Double slope, String reference, long seq) {}

    public record Scan(String id, String sampleId, Segment segment, String rawRate, String rateUnit,
                       Double rateKPerMin, String atmosphere, DirectionConvention convention,
                       String calibrationId, String sourceFile, long seq) {}

    public record ScanPoint(String scanId, int idx, String rawTime, String timeUnit,
                            String rawTemp, String tempUnit, String rawHf, String hfUnit,
                            Double timeS, Double tempC, Double heatflowMw) {}

    public record Tangent(String id, String scanId, String eventHint,
                          double x1, double y1, double x2, double y2,
                          String selectedBy, long seq) {}

    public record ThermalEvent(String id, String scanId, EventKind kind,
                               double intervalStartC, double intervalEndC,
                               String status, long seq) {}

    public record Baseline(String id, String eventId, String type, String paramsJson,
                           BaselineStatus status, String origin, long seq) {}

    /** One derived result for one (event, baseline) pair. Identity is a stable hash of inputs. */
    public record DerivedResult(String id, String eventId, String baselineId,
                                Double onsetC, Double peakC, Double endsetC,
                                Double enthalpyJg, String direction,
                                List<String> errorSources, String inputsJson, long computedIn) {}

    public record Derivation(String id, long version, boolean complete, String reason, long seq) {}

    public record Judgment(String id, String targetId, String action, String payloadJson,
                           long baseVersion, String actor, long seq, String branchOf,
                           long derivationVersion) {}

    public record Diagnostic(String id, String objectId, String severity, String message, long seq) {}
}
