package dsc.model;

import java.util.Map;

public record Integration(
        String integrationId,
        String intervalId,
        String candidateId,
        String massVersionId,
        int massVersion,
        String calibrationVersionId,
        double energyJ,
        double specificEnergyJPerG,
        double standardUncertaintyJPerG,
        Map<String, Double> uncertaintyBudgetJPerG,
        String status,
        String producedAtEvent
) {
    public static final String CURRENT = "current";
    public static final String STALE = "stale";
}
