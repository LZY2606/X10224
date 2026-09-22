package dsc.service;

import dsc.model.TraceImport;

/** Command DTOs accepted by the application layer. */
public final class Commands {

    private Commands() {}

    public record OpenHistory(String historyId, String name, String actor) {}

    public record ImportTrace(String actor, String comment, TraceImport trace, String supersedesSegment) {}

    public record RecordMass(String actor, String comment, String raw, String unit, int version,
                             boolean revision, String reason) {}

    public record RecordAtmosphere(String actor, String gas, String flowRaw, String flowUnit, String note) {}

    public record RecordCalibration(String actor, String standard, double offsetCelsius, String note) {}

    public record DeclareRate(String actor, String segmentId, double value, String unit) {}

    public record DefineRegion(String actor, String segmentId, String kind, double tLo, double tHi,
                               String label) {}

    public record SelectTangent(String actor, String regionId, double leftLo, double leftHi,
                                double rightLo, double rightHi, String mode, String note) {}

    public record AcceptCandidate(String actor, String regionId, String baselineId, String comment) {}

    public record RejectCandidate(String actor, String candidateId, String reason) {}

    public record AdjustParameter(String actor, String regionId, String tangentId,
                                  double leftLo, double leftHi, double rightLo, double rightHi,
                                  String note) {}

    public record AmendSource(String actor, String segmentId, TraceImport replacement, String reason) {}
}
