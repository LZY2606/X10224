package dsc.core;

/**
 * A segment prepared for derivation: temperature calibrated into Kelvin and heat
 * flow converted to the canonical convention (positive = endothermic, in Watt).
 * The original {@link RawSeries} remains available in the source archive.
 */
public record WorkingCurve(
        RawSeries raw,
        double[] tK,
        double[] hfCanonicalW,
        boolean signFlipped,
        double calibrationOffsetK,
        double calibrationSlope
) {

    public int length() {
        return tK.length;
    }

    /** Canonical calibrated temperature at index i. */
    public double t(int i) {
        return calibrationSlope * tK[i] + calibrationOffsetK;
    }

    /** Canonical heat flow (positive = endothermic) at index i, W. */
    public double hf(int i) {
        return hfCanonicalW[i];
    }
}
