package dsc.model;

/**
 * One baseline candidate under a peak or step. Several plausible baselines
 * may coexist; accepting one never deletes the others.
 */
public final class Baseline {
    public enum Type { LINEAR, SIGMOIDAL, TANGENT }
    public enum State { CANDIDATE, ACCEPTED, REJECTED }

    public final String id;
    public final Type type;
    public final double t1;
    public final double t2;
    public final double y1;
    public final double y2;
    public final double curvature;
    public final State state;
    public final double spreadVsPeers;

    public Baseline(String id, Type type, double t1, double t2, double y1, double y2,
                    double curvature, State state, double spreadVsPeers) {
        this.id = id;
        this.type = type;
        this.t1 = t1;
        this.t2 = t2;
        this.y1 = y1;
        this.y2 = y2;
        this.curvature = curvature;
        this.state = state;
        this.spreadVsPeers = spreadVsPeers;
    }

    public Baseline withSpread(double spread) {
        return new Baseline(id, type, t1, t2, y1, y2, curvature, state, spread);
    }

    public Baseline withState(State s) {
        return new Baseline(id, type, t1, t2, y1, y2, curvature, s, spreadVsPeers);
    }

    /** Evaluate baseline signal at temperature t (K). */
    public double at(double t) {
        if (t2 == t1) return y1;
        double f = (t - t1) / (t2 - t1);
        f = Math.max(0, Math.min(1, f));
        double lin = y1 + (y2 - y1) * f;
        if (curvature <= 0) return lin;
        double s = f * f * (3 - 2 * f);
        double sig = y1 + (y2 - y1) * s;
        return lin * (1 - curvature) + sig * curvature;
    }
}
