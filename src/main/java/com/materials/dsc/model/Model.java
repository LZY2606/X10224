package com.materials.dsc.model;

import java.util.List;
import java.util.Map;

/** Domain model for the DSC baseline / phase-transition reviewer.
 *  All domain quantities carry their unit or coordinate basis in the field name;
 *  raw instrument representations are preserved at import, normalization happens
 *  only in the derived layer. */
public final class Model {
    private Model() {}

    public enum Segment { HEATING, COOLING }
    public enum EventKind { GLASS_TRANSITION, CRYSTALLIZATION, MELTING }
    public enum BaselineStatus { CANDIDATE, ACCEPTED, REJECTED }
    public enum DerivationStatus { BUILDING, COMPLETE, FAILED }

    /** Raw instrument point, exactly as exported (time s, temperature C, heat flow mW). */
    public record RawPoint(double timeS, double tempC, double heatFlowMw) {}

    /** Archived instrument output. endoUpDeclared comes from the instrument header,
     *  never guessed from a file name. */
    public record InstrumentRun(
            String id, String sampleId, Segment segment, int cycleIndex,
            double scanRateKPerMin, String atmosphere, String calibrationId,
            boolean endoUpDeclared, String sourceName,
            List<RawPoint> points, String importedAt) {}

    public record MassVersion(String id, String sampleId, double massMg, int versionNo, String createdAt) {}

    public record Calibration(String id, String instrument, double offsetC, double slope, String createdAt) {}

    /** User-selected tangent, archived separately from instrument output. */
    public record Tangent(String id, String runId, String kind,
                          double x1, double y1, double x2, double y2,
                          String author, String createdAt) {}

    public record Baseline(String id, String runId, double regionStartC, double regionEndC,
                           String type, Map<String, Double> params, BaselineStatus status,
                           String supersedes, int version, String createdAt) {}

    /** A thermal event conclusion. Nullable values stay null when undecidable
     *  (e.g. empty interval); diagnostics explain why. */
    public record ThermalEvent(String id, String runId, EventKind kind,
                               double regionStartC, double regionEndC,
                               Double onsetC, Double peakC, Double endsetC,
                               Double enthalpyJPerG,
                               List<String> errorSources, List<String> diagnostics,
                               String baselineId, List<String> altBaselineIds,
                               String derivationId, int version) {}

    /** One versioned derivation node in the dependency graph. */
    public record Derivation(String id, String kind, String nodeKey, String inputHash,
                             DerivationStatus status, String payloadJson, String createdAt) {}

    /** Append-only, machine-readable judgement log. */
    public record AnalysisEvent(long seq, String id, String type, String payloadJson,
                                String actor, String createdAt) {}
}
