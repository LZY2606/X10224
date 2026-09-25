package dsc.domain;

import java.util.ArrayList;
import java.util.List;

/**
 * 基线引擎：对同一段曲线、同一组区间/锚点生成多种合理候选。
 * 不挑出“唯一正确答案”；并列候选由审阅者裁定，未选中的也保留。
 */
public final class BaselineEngine {

    public enum Method {
        TANGENT_LINEAR("tangent-linear"),
        PIECEWISE_LINEAR("piecewise-linear"),
        CUBIC_SPLINE("cubic-spline");

        private final String id;

        Method(String id) {
            this.id = id;
        }

        public String id() {
            return id;
        }
    }

    public List<BaselineCandidate> generate(CalibratedSeries series,
                                            TangentAnchors anchors,
                                            double windowStartK,
                                            double windowEndK) {
        List<BaselineCandidate> out = new ArrayList<>();
        out.add(tangentLinear(series, anchors));
        out.add(piecewiseLinear(series, anchors, windowStartK, windowEndK));
        out.add(cubicSpline(series, anchors, windowStartK, windowEndK));
        return out;
    }

    private BaselineCandidate tangentLinear(CalibratedSeries series, TangentAnchors anchors) {
        double[] left = fitLine(series, anchors.leftStartK(), anchors.leftEndK());
        double[] right = fitLine(series, anchors.rightStartK(), anchors.rightEndK());
        double lm = left[0];
        double lb = left[1];
        double rm = right[0];
        double rb = right[1];
        double midT = (anchors.leftEndK() + anchors.rightStartK()) / 2.0;
        double[] ts = {anchors.leftStartK(), anchors.leftEndK(), midT,
                anchors.rightStartK(), anchors.rightEndK()};
        double[] vs = {lm * ts[0] + lb, lm * ts[1] + lb,
                0.5 * (lm * ts[2] + lb + rm * ts[2] + rb),
                rm * ts[3] + rb, rm * ts[4] + rb};
        BaselineCandidate raw = new BaselineCandidate(
                "bl-tangent", Method.TANGENT_LINEAR.id(),
                List.of("left-tangent:" + fmt(anchors.leftStartK()) + "-" + fmt(anchors.leftEndK()),
                        "right-tangent:" + fmt(anchors.rightStartK()) + "-" + fmt(anchors.rightEndK())),
                0, ts, vs);
        return withRmse(series, raw);
    }

    private BaselineCandidate piecewiseLinear(CalibratedSeries series, TangentAnchors anchors,
                                              double windowStartK, double windowEndK) {
        double[] left = fitLine(series, anchors.leftStartK(), anchors.leftEndK());
        double[] right = fitLine(series, anchors.rightStartK(), anchors.rightEndK());
        double[] ts = {anchors.leftStartK(), anchors.leftEndK(),
                anchors.rightStartK(), anchors.rightEndK()};
        double[] vs = {left[0] * ts[0] + left[1], left[0] * ts[1] + left[1],
                right[0] * ts[2] + right[1], right[0] * ts[3] + right[1]};
        BaselineCandidate raw = new BaselineCandidate(
                "bl-piecewise", Method.PIECEWISE_LINEAR.id(),
                List.of("linear-pre-transition", "linear-post-transition"), 0, ts, vs);
        return withRmse(series, raw);
    }

    private BaselineCandidate cubicSpline(CalibratedSeries series, TangentAnchors anchors,
                                          double windowStartK, double windowEndK) {
        List<double[]> knots = new ArrayList<>();
        knots.add(new double[]{anchors.leftStartK(), avgHeat(series, anchors.leftStartK(), anchors.leftStartK() + 0.5)});
        knots.add(new double[]{anchors.leftEndK(), avgHeat(series, anchors.leftEndK() - 0.5, anchors.leftEndK())});
        knots.add(new double[]{anchors.rightStartK(), avgHeat(series, anchors.rightStartK(), anchors.rightStartK() + 0.5)});
        knots.add(new double[]{anchors.rightEndK(), avgHeat(series, anchors.rightEndK() - 0.5, anchors.rightEndK())});
        NaturalCubicSpline spline = new NaturalCubicSpline(knots);
        double lo = Math.max(windowStartK, anchors.leftStartK());
        double hi = Math.min(windowEndK, anchors.rightEndK());
        int steps = 24;
        double[] ts = new double[steps + 1];
        double[] vs = new double[steps + 1];
        for (int i = 0; i <= steps; i++) {
            ts[i] = lo + (hi - lo) * i / steps;
            vs[i] = spline.value(ts[i]);
        }
        BaselineCandidate raw = new BaselineCandidate(
                "bl-spline", Method.CUBIC_SPLINE.id(),
                List.of("natural-cubic-spline-4-knots"), 0, ts, vs);
        return withRmse(series, raw);
    }

    private BaselineCandidate withRmse(CalibratedSeries series, BaselineCandidate c) {
        double sse = 0;
        int count = 0;
        for (int i = 0; i < series.size(); i++) {
            double t = series.tempK(i);
            if (t < c.tempsK()[0] || t > c.tempsK()[c.tempsK().length - 1]) {
                continue;
            }
            double residual = series.heat(i) - c.valueAt(t);
            sse += residual * residual;
            count++;
        }
        double rmse = count == 0 ? Double.POSITIVE_INFINITY : Math.sqrt(sse / count);
        return new BaselineCandidate(c.candidateId(), c.method(), c.evidence(), rmse,
                c.tempsK(), c.valuesMilliWatt());
    }

    /** 返回 [slope, intercept]，在锚点区间内对实测点做最小二乘。 */
    private double[] fitLine(CalibratedSeries series, double tLo, double tHi) {
        double sx = 0, sy = 0, sxx = 0, sxy = 0;
        int n = 0;
        for (int i = 0; i < series.size(); i++) {
            double t = series.tempK(i);
            if (t < tLo || t > tHi) {
                continue;
            }
            double y = series.heat(i);
            sx += t;
            sy += y;
            sxx += t * t;
            sxy += t * y;
            n++;
        }
        if (n < 2) {
            throw new IllegalArgumentException("anchors contain fewer than 2 samples: " + tLo + ".." + tHi);
        }
        double denom = n * sxx - sx * sx;
        double slope = (n * sxy - sx * sy) / denom;
        double intercept = (sy - slope * sx) / n;
        return new double[]{slope, intercept};
    }

    private double avgHeat(CalibratedSeries series, double tLo, double tHi) {
        double sum = 0;
        int n = 0;
        for (int i = 0; i < series.size(); i++) {
            double t = series.tempK(i);
            if (t >= tLo && t <= tHi) {
                sum += series.heat(i);
                n++;
            }
        }
        if (n == 0) {
            return series.interpolateHeatAtTemp((tLo + tHi) / 2);
        }
        return sum / n;
    }

    private static String fmt(double k) {
        return String.format("%.2fK", k);
    }
}
