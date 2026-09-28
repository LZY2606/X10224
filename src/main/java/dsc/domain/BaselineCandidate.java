package dsc.domain;

import java.util.List;

/**
 * One legal baseline inside an interval. Multiple candidates may coexist:
 * linear tangent baseline vs. spline interpolation between the two tangent
 * endpoints. Differences must be preserved, never collapsed to one answer.
 */
public final class BaselineCandidate {
    public final String id;
    public final String intervalId;
    public final String kind;
    public final List<Pt> points;
    public final String rationale;
    public final double rmsResidual;

    public BaselineCandidate(String id, String intervalId, String kind, List<Pt> points,
                          String rationale, double rmsResidual) {
        this.id = id;
        this.intervalId = intervalId;
        this.kind = kind;
        this.points = List.copyOf(points);
        this.rationale = rationale;
        this.rmsResidual = rmsResidual;
    }
}
