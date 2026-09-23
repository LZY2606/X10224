package dsc.model;

import java.util.List;

/**
 * The onset / peak / endset readback plus enthalpy and its error attribution.
 * Every value is expressed with an explicit unit or a coordinate basis.
 */
public record TransitionMetrics(
        String nodeId,
        String traceId,
        String windowId,
        String candidateId,
        String kind,
        double onset,
        double peak,
        double endset,
        String temperatureUnit,
        String temperatureBasis,
        double rawEnthalpy,
        String rawEnthalpyUnit,
        double normalizedEnthalpy,
        String normalizedEnthalpyUnit,
        double massValue,
        String massUnit,
        List<ErrorTerm> errorBudget) {

    public record ErrorTerm(String source, double contribution, String unit, String reason) {}
}
