package dsc.engine;

import dsc.model.Integration;
import dsc.model.ProjectState;

public final class IntegrationStatus {
    public static final String VALID = "valid";
    public static final String STALE = "stale";

    private IntegrationStatus() {
    }

    public static String of(ProjectState state, Integration integ) {
        var mass = state.currentMass();
        if (mass == null || mass.version() != integ.massVersion()) {
            return STALE;
        }
        var bc = state.baselines.get(integ.candidateId());
        if (bc != null && dsc.model.BaselineCandidate.SUPERSEDED.equals(bc.status())) {
            return STALE;
        }
        var cal = state.currentCalibration();
        if (cal != null && !cal.id().equals(integ.calibrationVersionId())) {
            return STALE;
        }
        return VALID;
    }
}
