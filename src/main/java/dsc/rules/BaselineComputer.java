package dsc.rules;

import dsc.model.BaselineCandidate;
import dsc.model.RuleVersions;
import dsc.model.SamplePoint;

import java.util.ArrayList;
import java.util.List;

/**
 * Baseline construction rules.
 *
 * <ul>
 *   <li>BL-CHORD-1:    straight line joining window edges,</li>
 *   <li>BL-TANGENT-1: least-squares lines on the two reviewer-picked
 *                      tangent anchors (pre/post event), joined at the
 *                      intersection of the tangent lines,</li>
 *   <li>BL-STEP-1:    pre-glass and post-glass levels (used for the step
 *                      in Cp of a glass transition).</li>
 * </ul>
 *
 * Each baseline is a straight line y = slope * T + intercept expressed in
 * W / K and W. Integration ({@link Integrator}) is linear-exact also on a
 * non-uniform temperature grid, so grid spacing never biases the enthalpy.
 */
public final class BaselineComputer {

    private BaselineComputer() {}

    public record Line(double slope, double intercept) {
        public double at(double tK) { return slope * tK + intercept; }
    }

    public static Line chord(List<SamplePoint> points, double leftTK, double rightTK) {
        SamplePoint l = nearest(points, leftTK);
        SamplePoint r = nearest(points, rightTK);
        double slope = (r.hfW() - l.hfW()) / (r.tK() - l.tK());
        return new Line(slope, l.hfW() - slope * l.tK());
    }

    /**
     * Two-tangent baseline. {@code anchorL} / {@code anchorR} are the two
     * temperature intervals [lo,hi] (K) the reviewer picked; a line is fit
     * in each. The returned slope/intercept is the pre-event tangent (the
     * post tangent is exposed separately for the glass step); the meeting
     * point is the tangent intersection.
     */
    public record TangentResult(Line pre, Line post, double meetTK,
                                double preHF, double postHF) {}

    public static TangentResult tangents(List<SamplePoint> points,
                                         double anchorLLo, double anchorLHi,
                                         double anchorRLo, double anchorRHi) {
        Line pre = fit(points, anchorLLo, anchorLHi);
        Line post = fit(points, anchorRLo, anchorRHi);
        double meet = (post.intercept() - pre.intercept()) / (pre.slope() - post.slope());
        return new TangentResult(pre, post, meet, pre.at(meet), post.at(meet));
    }

    public static Line fit(List<SamplePoint> points, double loTK, double hiTK) {
        double sx = 0, sy = 0, sxx = 0, sxy = 0;
        int n = 0;
        for (SamplePoint p : points) {
            if (!p.parseable()) continue;
            double t = p.tK();
            if (t < loTK - 1e-9 || t > hiTK + 1e-9) continue;
            double y = p.hfW();
            sx += t;
            sy += y;
            sxx += t * t;
            sxy += t * y;
            n++;
        }
        if (n < 2) {
            return chord(points, loTK, hiTK);
        }
        double slope = (n * sxy - sx * sy) / (n * sxx - sx * sx);
        double intercept = (sy - slope * sx) / n;
        return new Line(slope, intercept);
    }

    public static SamplePoint nearest(List<SamplePoint> points, double tK) {
        SamplePoint best = null;
        double bd = Double.POSITIVE_INFINITY;
        for (SamplePoint p : points) {
            if (!p.parseable()) continue;
            double d = Math.abs(p.tK() - tK);
            if (d < bd) {
                bd = d;
                best = p;
            }
        }
        if (best == null) throw new IllegalArgumentException("no parseable point near " + tK);
        return best;
    }

    /** Rule BL-CHORD-1, returning the full candidate record shell. */
    public static BaselineCandidate build(String id, String studyId, String segmentId,
                                          int version, BaselineCandidate.Method method,
                                          List<SamplePoint> points,
                                          double leftTK, double rightTK,
                                          TangentResult tangent, List<String> extraDiags,
                                          int seq, List<String> dependsOn) {
        List<String> diags = new ArrayList<>(extraDiags);
        Line line;
        double leftHF, rightHF;
        switch (method) {
            case CHORD -> {
                line = chord(points, leftTK, rightTK);
                SamplePoint l = nearest(points, leftTK);
                SamplePoint r = nearest(points, rightTK);
                leftHF = l.hfW();
                rightHF = r.hfW();
            }
            case TWO_TANGENT -> {
                if (tangent == null) {
                    throw new IllegalStateException("tangent anchors required");
                }
                // Baseline follows the pre tangent to the intersection and the post
                // tangent afterwards; for the integral the piecewise line is needed,
                // but slope/intercept stored here describe the pre tangent and the
                // post tangent is persisted in diagnostics as machine-readable text.
                line = tangent.pre();
                leftHF = tangent.preHF();
                rightHF = tangent.post().at(rightTK);
                diags.add(Integrator.tangentDiag(
                        RuleVersions.R_BASELINE_TANGENT, tangent.pre().slope(),
                        tangent.pre().intercept(), tangent.post().slope(),
                        tangent.post().intercept(), tangent.meetTK()));
            }
            case STEP -> {
                if (tangent == null) {
                    throw new IllegalStateException("step levels required");
                }
                line = tangent.pre();
                leftHF = tangent.pre().at(leftTK);
                rightHF = tangent.post().at(rightTK);
                double midTK = (leftTK + rightTK) / 2.0;
                diags.add(Integrator.tangentDiag(
                        RuleVersions.R_BASELINE_STEP, tangent.pre().slope(),
                        tangent.pre().intercept(), tangent.post().slope(),
                        tangent.post().intercept(), midTK));
                diags.add(String.format(java.util.Locale.ROOT,
                        "{\"rule\":\"%s\",\"stepCp\":%.10g,\"midT_K\":%.6f}",
                        RuleVersions.R_BASELINE_STEP,
                        (tangent.post().at(midTK) - tangent.pre().at(midTK)), midTK));
            }
            default -> throw new IllegalStateException();
        }
        return new BaselineCandidate(id, studyId, segmentId, version, method,
                leftTK, rightTK, leftHF, rightHF, line.slope(), line.intercept(),
                BaselineCandidate.Status.PROPOSED, null, diags,
                List.copyOf(dependsOn), seq, 0);
    }
}
