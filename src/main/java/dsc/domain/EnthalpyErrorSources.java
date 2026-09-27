package dsc.domain;

import java.util.List;

/**
 * Error budget attached to a specific enthalpy value. Relative terms are
 * dimensionless fractions; absolute terms are in J/g. Temperature calibration
 * uncertainty is in K and propagates through onset/peak temperatures.
 */
public record EnthalpyErrorSources(
        double massRelativeFraction,
        double rateRelativeFraction,
        double calibrationUncertaintyK,
        double baselineSpreadJG,
        double samplingUncertaintyJG,
        List<String> notes) {

    /** Conservative combined uncertainty (J/g) for a reported specific enthalpy. */
    public double combinedJG(double specificEnthalpyAbsJG) {
        return specificEnthalpyAbsJG * (massRelativeFraction + rateRelativeFraction)
                + baselineSpreadJG + samplingUncertaintyJG;
    }
}
