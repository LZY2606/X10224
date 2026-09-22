package dsc.model;

import java.util.List;

/**
 * Domain model for the DSC baseline / phase-transition reviewer.
 *
 * Every domain quantity carries a unit (or a coordinate basis) and keeps the
 * raw representation seen at import time. Normalization happens only in the
 * derived layer; values that cannot be converted stay in place with a
 * diagnostic attached.
 */
public final class Domain {

    private Domain() {}

    public enum SegmentKind { HEATING, COOLING, ISOTHERM }

    /** Instrument-declared heat-flow sign convention. Never guessed from file names. */
    public enum SignConvention { ENDO_UP, EXO_UP }

    public enum BaselineMethod { LINEAR, SIGMOIDAL, TANGENT, SPLIT_LEFT, SPLIT_RIGHT }

    public enum CandidateStatus { CANDIDATE, ACCEPTED, REJECTED }

    public enum Direction { ENDOTHERMIC, EXOTHERMIC, UNDETERMINED }

    /** A quantity with unit and the original textual representation. */
    public record Quantity(double value, String unit, String raw) {}

    /**
     * One raw sample point. {@code heatFlowMw} is null when the raw value could
     * not be converted; the raw text and a diagnostic are kept in place.
     */
    public record DataPoint(double timeS, double tempC, Double heatFlowMw,
                            String rawHeatFlow, String diagnostic) {}

    public record Series(List<DataPoint> points, String tempUnit, String flowUnit,
                         boolean uniformSpacing, List<String> diagnostics) {}

    /** Temperature calibration, versioned: tCal = tRaw * slope + offsetC. */
    public record Calibration(String id, int version, double offsetC, double slope, String raw) {}

    /** One instrument segment (a single heating / cooling / isotherm sweep). */
    public record Segment(String id, int version, SegmentKind kind, int cycleIndex,
                          Quantity scanRate, String atmosphere, String calibrationId,
                          SignConvention signConvention, Series series) {}

    public record MassVersion(String id, int version, Quantity mass, String note) {}

    /** A user-selected tangent line, archived separately from instrument output. */
    public record Tangent(String regionId, String side, double x1, double y1, double x2, double y2) {}

    public record TangentSet(String id, int version, List<Tangent> tangents) {}

    /** An integration region on exactly one segment. */
    public record Region(String id, int version, String segmentId,
                         double tStartC, double tEndC, String purpose) {}

    /**
     * One plausible baseline for a region. Several candidates may coexist;
     * accepting one never deletes the others.
     */
    public record BaselineCandidate(String id, int version, String regionId,
                                    BaselineMethod method, List<Double> coeffs,
                                    CandidateStatus status, List<String> deps, String note) {}

    public record IntegrationResult(String id, int version, String baselineId, String regionId,
                                    Double onsetC, Double peakC, Double endsetC,
                                    Quantity enthalpy, Direction direction,
                                    List<String> errorSources, List<String> deps, String status) {}

    /** Normalized heat flow (mW/mg), canonical ENDO_UP, calibrated temperatures. */
    public record NormalizedFlow(String id, int version, String segmentId,
                                 List<Double> tempsC, List<Double> flowsMwPerMg,
                                 String unit, List<String> diagnostics, List<String> deps) {}

    /** Machine-readable judgment attached to an event. */
    public record Judgment(String rule, String decision, List<String> affected,
                           String rationale) {}
}
