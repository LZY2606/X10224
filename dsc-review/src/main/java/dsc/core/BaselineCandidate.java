package dsc.core;

import java.util.List;
import java.util.Locale;

/**
 * One defensible baseline for an event window. Several candidates are computed
 * side by side and retained together; the reviewer may adopt at most one, but
 * rejected candidates are never deleted and the numerical difference between
 * candidates is part of the enthalpy uncertainty budget.
 */
public final class BaselineCandidate {

    public enum Method {
        LINEAR_CHORD("linear_chord", "Linear chord between window ends"),
        TWO_TANGENT("two_tangent", "Two-tangent construction"),
        TANGENT_STEP("tangent_step", "Pre/post tangent step (glass transition)");

        public final String code;
        public final String label;
        Method(String code, String label) { this.code = code; this.label = label; }
    }

    private final String stableId;
    private final Method method;
    private final double preStartC, preEndC, postStartC, postEndC;
    private final double preIntercept, preSlope;
    private final double postIntercept, postSlope;
    private final double rmsResidualWg;
    private final double[] tempC;
    private final double[] baselineWg;

    public BaselineCandidate(String stableId, Method method,
                             double preStartC, double preEndC, double postStartC, double postEndC,
                             double preIntercept, double preSlope,
                             double postIntercept, double postSlope,
                             double rmsResidualWg,
                             double[] tempC, double[] baselineWg) {
        this.stableId = stableId;
        this.method = method;
        this.preStartC = preStartC;
        this.preEndC = preEndC;
        this.postStartC = postStartC;
        this.postEndC = postEndC;
        this.preIntercept = preIntercept;
        this.preSlope = preSlope;
        this.postIntercept = postIntercept;
        this.postSlope = postSlope;
        this.rmsResidualWg = rmsResidualWg;
        this.tempC = tempC;
        this.baselineWg = baselineWg;
    }

    public String stableId() { return stableId; }
    public Method method() { return method; }
    public double preStartC() { return preStartC; }
    public double preEndC() { return preEndC; }
    public double postStartC() { return postStartC; }
    public double postEndC() { return postEndC; }
    public double preIntercept() { return preIntercept; }
    public double preSlope() { return preSlope; }
    public double postIntercept() { return postIntercept; }
    public double postSlope() { return postSlope; }
    public double rmsResidualWg() { return rmsResidualWg; }
    public double[] tempC() { return tempC; }
    public double[] baselineWg() { return baselineWg; }

    public double baselineAtC(double t) {
        if (method == Method.LINEAR_CHORD) {
            return preIntercept + preSlope * t;
        }
        double mid = (preEndC + postStartC) / 2.0;
        if (t <= mid) return preIntercept + preSlope * t;
        return postIntercept + postSlope * t;
    }

    public String descriptor() {
        return String.format(Locale.ROOT, "%s pre[%.2f,%.2f] post[%.2f,%.2f]",
                method.code, preStartC, preEndC, postStartC, postEndC);
    }
}
