package dsc.model;

/**
 * One legitimate baseline candidate. Multiple candidates are retained side by side;
 * an accepted decision never deletes the alternatives.
 */
public final class BaselineFit {
    public enum Kind { LINEAR_TANGENT, PIECEWISE_TANGENT, CURVATURE }

    public final String id;
    public final String intervalId;
    public final Kind kind;
    /** Coefficients of the baseline as a function of calibrated temperature K: a + b*T + c*T^2. */
    public final double a;
    public final double b;
    public final double c;
    /** Fit residual RMS in W; populated when the kind is fitted rather than tangential. */
    public final double rmsW;
    public final Types.Decision decision;
    public final String decidedByEventId;
    public final String rationale;
    public final int version;

    public BaselineFit(String id, String intervalId, Kind kind, double a, double b, double c,
                       double rmsW, Types.Decision decision, String decidedByEventId,
                       String rationale, int version) {
        this.id = id;
        this.intervalId = intervalId;
        this.kind = kind;
        this.a = a;
        this.b = b;
        this.c = c;
        this.rmsW = rmsW;
        this.decision = decision;
        this.decidedByEventId = decidedByEventId;
        this.rationale = rationale;
        this.version = version;
    }

    public double valueAt(double tempK) {
        return a + b * tempK + c * tempK * tempK;
    }

    public BaselineFit withDecision(Types.Decision d, String eventId) {
        return new BaselineFit(id, intervalId, kind, a, b, c, rmsW, d, eventId, rationale, version);
    }
}
