package dsc.core;

import dsc.model.CandidateBaseline;
import dsc.model.NormalizedCurve;
import dsc.model.NormalizedPoint;
import dsc.model.BaselineModel;
import dsc.model.TangentSelection;

import java.util.ArrayList;
import java.util.List;

/**
 * 为搜索窗口并列生成多个合理基线候选：
 *  - linear：锚点区间上的直线（玻璃化/轻微漂移）；
 *  - sigmoid：逻辑阶跃基线（玻璃化台阶拟合）；
 *  - piecewise：窗口中点衔接的两段直线（熔融/结晶峰常用）。
 * 若用户分别存档了切线，其锚点区间覆盖默认锚点，并标记 fromUserTangent。
 * 所有候选都保留，差异用锚点 RMS 表达，系统不替审阅者吞掉多解。
 */
public final class Baselines {
    private Baselines() {}

    public record Anchors(List<NormalizedPoint> left, List<NormalizedPoint> right) {}

    public static List<CandidateBaseline> propose(String candidateIdPrefix, NormalizedCurve curve,
                                                  double lo, double hi, TangentSelection tangent) {
        List<NormalizedPoint> pts = curve.points;
        double width = hi - lo;
        double defaultMargin = Math.max(width * 0.15, 0.0);
        double lFrom = lo - defaultMargin, lTo = lo + width * 0.10;
        double rFrom = hi - width * 0.10, rTo = hi + defaultMargin;
        boolean userTangent = false;
        if (tangent != null && tangent.appliesTo(curve.segmentId, idWindow(candidateIdPrefix))) {
            if (tangent.leftFromC.valid() && tangent.leftToC.valid()) {
                lFrom = tangent.leftFromC.value; lTo = tangent.leftToC.value;
            }
            if (tangent.rightFromC.valid() && tangent.rightToC.valid()) {
                rFrom = tangent.rightFromC.value; rTo = tangent.rightToC.value;
            }
            userTangent = true;
        }
        Anchors anchors = collectAnchors(pts, lFrom, lTo, rFrom, rTo);

        List<CandidateBaseline> out = new ArrayList<>();
        if (anchors.left().size() + anchors.right().size() < 4) {
            return out; // 锚点不足：不产生候选（上层产生空/不足诊断）
        }

        double[] ax = new double[anchors.left().size() + anchors.right().size()];
        double[] ay = ax.clone();
        int k = 0;
        for (NormalizedPoint p : anchors.left()) { ax[k] = p.tempC; ay[k] = p.hfMw; k++; }
        for (NormalizedPoint p : anchors.right()) { ax[k] = p.tempC; ay[k] = p.hfMw; k++; }

        // 1) 直线
        double[] lin = LeastSquares.linear(ax, ay);
        double linRms = LeastSquares.rms(ax, ay, lin);
        out.add(mk(idOf(candidateIdPrefix, "linear"), curve, BaselineModel.LINEAR, lin, lo, hi,
                userTangent, linRms, "least-squares line over tangent anchor windows"));

        // 2) 分段直线：用左右锚点各自拟合，交点取窗口中温
        if (anchors.left().size() >= 2 && anchors.right().size() >= 2) {
            double[] lx = anchors.left().stream().mapToDouble(p -> p.tempC).toArray();
            double[] ly = anchors.left().stream().mapToDouble(p -> p.hfMw).toArray();
            double[] rx = anchors.right().stream().mapToDouble(p -> p.tempC).toArray();
            double[] ry = anchors.right().stream().mapToDouble(p -> p.hfMw).toArray();
            double[] abL = LeastSquares.linear(lx, ly);
            double[] abR = LeastSquares.linear(rx, ry);
            double join = (lo + hi) / 2.0;
            double[] pw = {abL[0], abL[1], join, abR[0], abR[1]};
            double pwRms = Math.sqrt((Math.pow(LeastSquares.rms(lx, ly, abL), 2) * lx.length
                    + Math.pow(LeastSquares.rms(rx, ry, abR), 2) * rx.length) / k);
            out.add(mk(idOf(candidateIdPrefix, "piecewise"), curve, BaselineModel.PIECEWISE, pw, lo, hi,
                    userTangent, pwRms, "two tangent lines joined at window midpoint"));
        }

        // 3) sigmoid 基线：先由锚点估计 lo/hi 平台与中温，再做两轮坐标细化
        double yLeft = avg(anchors.left());
        double yRight = avg(anchors.right());
        double mid = (lo + hi) / 2.0;
        double spread = Math.max(width / 6.0, 1e-6);
        double[] best = {yLeft, yRight, mid, spread};
        double bestRms = sigmoidRms(ax, ay, best);
        double[] mids = {lo + width * 0.35, mid, hi - width * 0.35};
        for (double mm : mids) {
            for (double ww : new double[]{width / 12.0, width / 6.0, width / 3.0}) {
                double[] cand = {yLeft, yRight, mm, Math.max(ww, 1e-6)};
                double rms = sigmoidRms(ax, ay, cand);
                if (rms < bestRms) { bestRms = rms; best = cand; }
            }
        }
        out.add(mk(idOf(candidateIdPrefix, "sigmoid"), curve, BaselineModel.SIGMOID, best, lo, hi,
                userTangent, bestRms, "logistic step fitted to anchor plateaus"));

        out.sort((x, y) -> Double.compare(x.anchorRms, y.anchorRms));
        return out;
    }

    private static String idWindow(String prefix) {
        // prefix 形如 cand:<sample>:<window>
        int i = prefix.lastIndexOf(':');
        return i < 0 ? prefix : prefix.substring(i + 1);
    }

    private static String idOf(String prefix, BaselineModel m) {
        return prefix + ":" + m.code();
    }

    private static CandidateBaseline mk(String id, NormalizedCurve c, BaselineModel model, double[] params,
                                        double lo, double hi, boolean userTangent, double rms, String why) {
        CandidateBaseline b = new CandidateBaseline();
        b.candidateId = id;
        b.windowId = idWindow(id.substring(0, id.lastIndexOf(':')));
        b.segmentId = c.segmentId;
        b.model = model;
        b.params = params;
        b.leftTempC = lo;
        b.rightTempC = hi;
        b.fromUserTangent = userTangent;
        b.anchorRms = rms;
        b.rationale = why;
        return b;
    }

    private static Anchors collectAnchors(List<NormalizedPoint> pts, double lFrom, double lTo,
                                          double rFrom, double rTo) {
        List<NormalizedPoint> left = new ArrayList<>();
        List<NormalizedPoint> right = new ArrayList<>();
        for (NormalizedPoint p : pts) {
            if (p.tempC >= lFrom && p.tempC <= lTo) left.add(p);
            if (p.tempC >= rFrom && p.tempC <= rTo) right.add(p);
        }
        return new Anchors(left, right);
    }

    private static double avg(List<NormalizedPoint> ps) {
        double s = 0;
        for (NormalizedPoint p : ps) s += p.hfMw;
        return s / Math.max(1, ps.size());
    }

    private static double sigmoidRms(double[] xs, double[] ys, double[] p) {
        double s = 0;
        for (int i = 0; i < xs.length; i++) {
            double f = 1.0 / (1.0 + Math.exp(-(xs[i] - p[2]) / Math.max(p[3], 1e-9)));
            double y = p[0] + (p[1] - p[0]) * f;
            double d = ys[i] - y;
            s += d * d;
        }
        return Math.sqrt(s / xs.length);
    }
}
