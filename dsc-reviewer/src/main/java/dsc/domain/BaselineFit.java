package dsc.domain;

import java.util.List;

/**
 * A fully recomputable baseline candidate. Every candidate is kept — competing
 * legal baselines are not collapsed into one answer. Coefficients live in
 * canonical units (heat flow mW vs temperature K): y = a + b*T.
 */
public record BaselineFit(
        BaselineModel model,
        Line left,
        Line right,
        Double stepTemperatureK,
        int leftPointCount,
        int rightPointCount,
        double rmseMW,
        List<Diagnostic> diagnostics
) {
    /** Baseline heat flow (mW) at temperature T (K). */
    public double yAt(double tK) {
        return switch (model) {
            case LINEAR_TANGENT -> left.y(tK);
            case STEP_TANGENT -> tK < stepTemperatureK ? left.y(tK) : right.y(tK);
        };
    }

    /** Step height of the heat capacity jump at Tg (mW); null for a single-line baseline. */
    public Double stepHeightMW() {
        if (model != BaselineModel.STEP_TANGENT || stepTemperatureK == null) return null;
        return right.y(stepTemperatureK) - left.y(stepTemperatureK);
    }

    public record Line(double a, double b) {
        public double y(double tK) {
            return a + b * tK;
        }
    }
}
