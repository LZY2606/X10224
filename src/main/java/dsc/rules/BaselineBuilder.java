package dsc.rules;

import dsc.domain.BaselineCandidate;
import dsc.domain.TangentSpec;
import java.util.ArrayList;
import java.util.List;

/**
 * Builds the legitimate baseline candidates for an interval.
 *
 * Peak events (crystallization / melting) use straight baselines anchored on the
 * pre/post-event flanks. Two equally defensible anchor choices are produced whenever the
 * data supports them — the reviewer sees them side by side and the difference is retained.
 *
 * Glass transitions use the classic two-tangent step construction from the separately
 * archived user-selected tangent windows.
 */
public final class BaselineBuilder {

    private BaselineBuilder() {
    }

    public static final class Result {
        public final List<BaselineCandidate> candidates = new ArrayList<>();
        public BaselineCandidate empty;
    }

    public static Result buildPeak(String idPrefix, String intervalId,
                                   PreparedSeries series, double fromT, double toT,
                                   List<Double> anchorsLow, List<Double> anchorsHigh,
                                   String fingerprint, String ruleset) {
        Result r = new Result();
        List<double[]> window = series.window(fromT, toT);

        if (window.size() < 2) {
            r.empty = emptyCandidate(idPrefix + "-empty", intervalId, window.size(),
                    "区间内可计算样本少于 2 个（空区间或全部不可转换）", fingerprint, ruleset);
            return r;
        }

        // Candidate 1: chord joining the first and last in-window samples.
        {
            BaselineCandidate c = base(idPrefix + "-chord", intervalId, "linear-chord",
                    "直线弦（区间端点连线）", fingerprint, ruleset);
            double[] first = window.get(0);
            double[] last = window.get(window.size() - 1);
            fillStraight(c, window, first[1], first[2], last[1], last[2]);
            c.residualRms = CurveMath.rms(PreparedSeries.xy(window), c.curve);
            if (anchorsInvalidOverlap(window, c)) {
                c.warnings.add("overlapping-transitions: flanks not flat, chord biased");
            }
            r.candidates.add(c);
        }

        // Candidate 2: least-squares tangent fitted over explicit flank anchor windows.
        if (anchorsLow != null && anchorsHigh != null
                && anchorsLow.size() == 2 && anchorsHigh.size() == 2) {
            List<double[]> pre = PreparedSeries.xy(series.window(anchorsLow.get(0), anchorsLow.get(1)));
            List<double[]> post = PreparedSeries.xy(series.window(anchorsHigh.get(0), anchorsHigh.get(1)));
            if (pre.size() >= 2 && post.size() >= 2) {
                BaselineCandidate c = base(idPrefix + "-tangent", intervalId,
                        "anchored-linear", "锚定线性（前后沿最小二乘）", fingerprint, ruleset);
                double[] lf = CurveMath.linearFit(pre);
                double[] rt = CurveMath.linearFit(post);
                // Average of the two flank lines, the standard single peak baseline.
                double a = 0.5 * (lf[0] + rt[0]);
                double b = 0.5 * (lf[1] + rt[1]);
                fillLineModel(c, window, a, b);
                double[] yAt = new double[]{
                        a + b * window.get(0)[1],
                        a + b * window.get(window.size() - 1)[1]
                };
                c.residualRms = CurveMath.rms(PreparedSeries.xy(window), c.curve);
                double slopeDisagree = Math.abs(lf[1] - rt[1]);
                if (slopeDisagree > 1e-3) {
                    c.warnings.add("overlapping-transitions: pre/post flank slopes differ ("
                            + fmt(lf[1]) + " vs " + fmt(rt[1]) + " W/g/K)");
                }
                double endGap = Math.abs(yAt[1] - yAt[0]);
                if (endGap > 0.02) {
                    c.warnings.add("baseline does not close: end-to-end offset " + fmt(endGap) + " W/g");
                }
                r.candidates.add(c);
            }
        }
        return r;
    }

    public static Result buildStep(String idPrefix, String intervalId,
                                   PreparedSeries series, TangentSpec spec,
                                   String fingerprint, String ruleset) {
        Result r = new Result();
        if (spec == null) {
            r.empty = emptyCandidate(idPrefix + "-empty", intervalId, 0,
                    "未存档用户选定的切线，无法构造玻璃化阶跃基线", fingerprint, ruleset);
            return r;
        }
        List<double[]> whole = series.window(spec.preFromT, spec.postToT);
        List<double[]> pre = PreparedSeries.xy(series.window(spec.preFromT, spec.preToT));
        List<double[]> post = PreparedSeries.xy(series.window(spec.postFromT, spec.postToT));
        if (pre.size() < 2 || post.size() < 2) {
            r.empty = emptyCandidate(idPrefix + "-empty", intervalId, whole.size(),
                    "切线锚定窗口内可计算样本不足（前=" + pre.size() + " 后=" + post.size() + "）",
                    fingerprint, ruleset);
            return r;
        }
        double[] lf = CurveMath.linearFit(pre);
        double[] rt = CurveMath.linearFit(post);

        BaselineCandidate c = base(idPrefix + "-step", intervalId,
                "two-tangent-step", "双切线阶跃（用户切线）", fingerprint, ruleset);
        c.tangentSourceId = spec.id + "@v" + spec.version;
        for (double[] p : whole) {
            double t = p[1];
            double y;
            if (t <= spec.preToT) {
                y = lf[0] + lf[1] * t;
            } else if (t >= spec.postFromT) {
                y = rt[0] + rt[1] * t;
            } else {
                double y1 = lf[0] + lf[1] * spec.preToT;
                double y2 = rt[0] + rt[1] * spec.postFromT;
                double f = (t - spec.preToT) / (spec.postFromT - spec.preToT);
                y = y1 + f * (y2 - y1);
            }
            c.curve.add(new double[]{t, y});
        }
        c.residualRms = CurveMath.rms(PreparedSeries.xy(whole), c.curve);
        c.warnings.add("glass-transition: step baseline, enthalpy reporting is step/area only");
        r.candidates.add(c);

        // Conservative alternative: flat step through the midpoint of the two tangents.
        BaselineCandidate alt = base(idPrefix + "-flatstep", intervalId,
                "flat-step", "平阶跃（保守替代）", fingerprint, ruleset);
        alt.tangentSourceId = spec.id + "@v" + spec.version;
        double midT = 0.5 * (spec.preToT + spec.postFromT);
        double midY = 0.5 * (lf[0] + lf[1] * midT + rt[0] + rt[1] * midT);
        for (double[] p : whole) {
            alt.curve.add(new double[]{p[1], midY});
        }
        alt.residualRms = CurveMath.rms(PreparedSeries.xy(whole), alt.curve);
        alt.warnings.add("alternative baseline deliberately retained; differs from tangent step");
        r.candidates.add(alt);
        return r;
    }

    private static boolean anchorsInvalidOverlap(List<double[]> window, BaselineCandidate c) {
        double[] xy = PreparedSeries.xy(window).stream()
                .mapToDouble(p -> p[1]).toArray();
        if (xy.length < 6) {
            return false;
        }
        double first = avg(xy, 0, 2);
        double last = avg(xy, xy.length - 2, xy.length);
        return Math.abs(last - first) > 0.02;
    }

    private static double avg(double[] a, int from, int to) {
        double s = 0;
        int n = 0;
        for (int i = from; i < to; i++) {
            s += a[i];
            n++;
        }
        return s / n;
    }

    private static void fillStraight(BaselineCandidate c, List<double[]> window,
                                     double t0, double y0, double t1, double y1) {
        double b = (y1 - y0) / (t1 - t0);
        double a = y0 - b * t0;
        fillLineModel(c, window, a, b);
    }

    private static void fillLineModel(BaselineCandidate c, List<double[]> window,
                                      double a, double b) {
        for (double[] p : window) {
            c.curve.add(new double[]{p[1], a + b * p[1]});
        }
    }

    private static BaselineCandidate base(String id, String intervalId, String method,
                                          String label, String fingerprint, String ruleset) {
        BaselineCandidate c = new BaselineCandidate();
        c.id = id;
        c.intervalId = intervalId;
        c.method = method;
        c.methodLabel = label;
        c.inputsFingerprint = fingerprint;
        c.rulesetVersion = ruleset;
        return c;
    }

    private static BaselineCandidate emptyCandidate(String id, String intervalId, int n,
                                                    String reason, String fingerprint,
                                                    String ruleset) {
        BaselineCandidate c = base(id, intervalId, "empty", "空区间（不可积分）",
                fingerprint, ruleset);
        c.usable = false;
        c.unusableReason = reason + "；样本数=" + n;
        return c;
    }

    private static String fmt(double v) {
        return String.format(java.util.Locale.ROOT, "%.4f", v);
    }
}
