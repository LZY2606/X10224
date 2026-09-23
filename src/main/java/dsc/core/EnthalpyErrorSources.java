package dsc.core;

/**
 * Error/provenance contributions attached to a derived enthalpy. All values
 * are absolute magnitudes in J/g; {@code totalAbs} is a linear conservative
 * sum, {@code totalRss} the root-sum-square combination.
 */
public record EnthalpyErrorSources(double massJg, double rateJg,
                                   double baselineJg, double samplingJg,
                                   double calibrationJg, double polarityJg) {
    public double totalAbs() { return massJg + rateJg + baselineJg + samplingJg
            + calibrationJg + polarityJg; }
    public double totalRss() {
        double s = 0;
        for (double v : new double[]{massJg, rateJg, baselineJg, samplingJg,
                calibrationJg, polarityJg}) s += v * v;
        return Math.sqrt(s);
    }
}
