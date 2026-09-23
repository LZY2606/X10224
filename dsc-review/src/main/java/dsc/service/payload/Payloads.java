package dsc.service.payload;

/** Typed payloads carried by journal events. */
public final class Payloads {
    private Payloads() {}

    public record ImportTrace(String sourceId, String sequenceId, String contentCsv,
                              String calibrationId) {}
    public record MassEdit(String massId, double value, String unit, double uncertaintyMg,
                           String note, int revision) {}
    public record RateEdit(String rateId, String sequenceId, double value, String unit,
                           double uncertaintyKPerMin, int revision) {}
    public record AtmosphereEdit(String atmosphereId, String sequenceId, String gas,
                                 double flowMlPerMin, int revision) {}
    public record CalibrationEdit(String calibrationId, double slope, double offsetC,
                                  double uncertaintyC, String basis, int revision) {}
    public record TangentSelect(String tangentId, String sequenceId, String intervalId,
                                double leftStartC, double leftEndC, double rightStartC,
                                double rightEndC, String type, String note, int revision) {}
    public record IntervalSet(String intervalId, String sequenceId, String legId,
                              double startC, double endC, String transitionKind) {}
    public record ComputeBaselines(String intervalId, String tangentId) {}
    public record AdoptBaseline(String candidateId, String intervalId) {}
    public record RejectBaseline(String candidateId, String reason) {}
    public record AdjustParams(String tangentId, String intervalId, double leftStartC,
                               double leftEndC, double rightStartC, double rightEndC,
                               String type, int revision) {}
    public record ClassifyTransition(String transitionId, String kind) {}
    public record ContinueBranch(String fromSeq, String newBranchId, String reason) {}
    public record ResolveConflict(String winningEventSeq, String losingEventSeq, String resolution) {}
}
