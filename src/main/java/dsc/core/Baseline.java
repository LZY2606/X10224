package dsc.core;

import java.util.List;

/**
 * A baseline candidate anchored on user-selected tangents. Multiple plausible
 * baselines for the same window are all preserved; adoption is a separate
 * decision event, never an overwrite.
 */
public sealed interface Baseline {

    String id();

    String type();

    /** Baseline heat flow (mW) at the given temperature (degC). */
    double valueAt(double tempC);

    record Linear(String id, double t1, double y1, double t2, double y2) implements Baseline {
        @Override public String type() { return "LINEAR"; }

        @Override public double valueAt(double tempC) {
            if (t2 == t1) return y1;
            return y1 + (y2 - y1) * (tempC - t1) / (t2 - t1);
        }
    }

    /** Cubic Hermite spline between the two tangent anchors (sigmoidal). */
    record Sigmoidal(String id, double t1, double y1, double m1,
                     double t2, double y2, double m2) implements Baseline {
        @Override public String type() { return "SIGMOIDAL"; }

        @Override public double valueAt(double tempC) {
            double h = t2 - t1;
            if (h == 0) return y1;
            double s = (tempC - t1) / h;
            double s2 = s * s;
            double s3 = s2 * s;
            return (2 * s3 - 3 * s2 + 1) * y1
                 + (s3 - 2 * s2 + s) * h * m1
                 + (-2 * s3 + 3 * s2) * y2
                 + (s3 - s2) * h * m2;
        }
    }

    static List<Baseline> candidates(String windowId, Anchor left, Anchor right) {
        return List.of(
            new Linear(windowId + "-lin", left.t(), left.y(), right.t(), right.y()),
            new Sigmoidal(windowId + "-sig", left.t(), left.y(), left.slope(),
                          right.t(), right.y(), right.slope()));
    }

    /** Tangent anchor: temperature, curve value and local slope. */
    record Anchor(double t, double y, double slope) {}
}
