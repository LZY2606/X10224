package dsc.derive;

import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.List;

/**
 * Baseline candidates. We deliberately keep EVERY legitimate candidate and their spread,
 * rather than collapsing to one "best" baseline.
 *
 *  - LINEAR_ENDPOINT : straight line through the two reviewer anchors
 *  - TANGENT         : flat pre + flat post joined through the reviewer tangent slopes
 *  - STEP_SIGMOID    : sigmoid step across the interval (the physically correct shape for Tg)
 */
public final class Baselines {
    private Baselines() {}

    public static List<Models.Baseline> candidates(Models.Segment seg, Models.Interval iv) {
        int[] idx = bracket(seg.tK, iv.loK, iv.hiK);
        int lo = idx[0], hi = idx[1];
        double[] x = seg.tK, y = seg.hfEndoUp;
        int m = hi - lo + 1;
        double[] gx = new double[m];
        for (int i = 0; i < m; i++) gx[i] = x[lo + i];

        List<Models.Baseline> out = new ArrayList<>();
        out.add(linearEndpoint(x, y, iv, gx));
        out.add(tangent(x, y, iv, gx, lo, hi));
        out.add(stepSigmoid(x, y, iv, gx));
        return out;
    }

    private static Models.Baseline linearEndpoint(double[] x, double[] y, Models.Interval iv, double[] gx) {
        int la = Numeric.lowerKnot(x, iv.leftAnchorK), ra = Numeric.lowerKnot(x, iv.rightAnchorK);
        double m = (y[ra] - y[la]) / (x[ra] - x[la]);
        double b = y[la] - m * x[la];
        double[] v = new double[gx.length];
        for (int i = 0; i < gx.length; i++) v[i] = m * gx[i] + b;
        ObjectNode p = dsc.domain.Json.obj();
        p.put("slope", m); p.put("intercept", b);
        p.put("leftAnchorK", x[la]); p.put("rightAnchorK", x[ra]);
        return new Models.Baseline("LINEAR_ENDPOINT", v, p);
    }

    private static Models.Baseline tangent(double[] x, double[] y, Models.Interval iv, double[] gx, int lo, int hi) {
        int win = Math.max(2, (hi - lo) / 10);
        double pre = Numeric.mean(y, Math.max(0, lo - win), lo);
        double post = Numeric.mean(y, hi, Math.min(y.length - 1, hi + win));
        double width = Math.max(1.0, (x[hi] - x[lo]) / 6.0);
        double mid = 0.5 * (x[lo] + x[hi]);
        double[] v = new double[gx.length];
        for (int i = 0; i < gx.length; i++) {
            double z = (gx[i] - mid) / width;
            double s = 1.0 / (1.0 + Math.exp(-z)); // 0 pre -> 1 post
            v[i] = pre + (post - pre) * s;
        }
        ObjectNode p = dsc.domain.Json.obj();
        p.put("preLevel", pre); p.put("postLevel", post); p.put("midK", mid); p.put("widthK", width);
        return new Models.Baseline("TANGENT", v, p);
    }

    /** Sigmoid step baseline for glass transitions; amplitude estimated from endpoint levels. */
    private static Models.Baseline stepSigmoid(double[] x, double[] y, Models.Interval iv, double[] gx) {
        int la = Numeric.lowerKnot(x, iv.leftAnchorK), ra = Numeric.lowerKnot(x, iv.rightAnchorK);
        int win = Math.max(2, (ra - la) / 8);
        double pre = Numeric.mean(y, Math.max(0, la - win), la);
        double post = Numeric.mean(y, ra, Math.min(y.length - 1, ra + win));
        double center = 0.5 * (iv.loK + iv.hiK);
        double width = Math.max(0.5, (iv.hiK - iv.loK) / 8.0);
        double[] v = new double[gx.length];
        for (int i = 0; i < gx.length; i++) {
            double z = (gx[i] - center) / width;
            v[i] = pre + (post - pre) / (1.0 + Math.exp(-z));
        }
        ObjectNode p = dsc.domain.Json.obj();
        p.put("preCpLevel", pre); p.put("postCpLevel", post); p.put("tgCenterK", center); p.put("widthK", width);
        return new Models.Baseline("STEP_SIGMOID", v, p);
    }

    public static int[] bracket(double[] x, double loK, double hiK) {
        int lo = Math.max(0, Numeric.lowerKnot(x, loK));
        int hi = Math.min(x.length - 1, Numeric.lowerKnot(x, hiK) + 1);
        if (hi <= lo) hi = Math.min(x.length - 1, lo + 1);
        return new int[]{lo, hi};
    }
}
