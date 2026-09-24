package dsc.domain;

import dsc.model.BaselineCandidate;

import java.util.ArrayList;
import java.util.List;

/**
 * 基线构造。输入为已归一化曲线（mW/mg）与温度（°C）。
 * 三种构造都可能合理：并列返回候选，由审阅者裁定，系统不吞掉多解。
 */
public final class BaselineBuilder {

    /** 最小二乘直线：返回 {slope, intercept}。 */
    static double[] linearFit(double[] t, double[] y, int from, int to) {
        int n = to - from + 1;
        double sx = 0, sy = 0, sxx = 0, sxy = 0;
        for (int i = from; i <= to; i++) {
            sx += t[i]; sy += y[i]; sxx += t[i] * t[i]; sxy += t[i] * y[i];
        }
        double denom = n * sxx - sx * sx;
        double slope = denom == 0 ? 0 : (n * sxy - sx * sy) / denom;
        double intercept = (sy - slope * sx) / n;
        return new double[]{slope, intercept};
    }

    static int lowerBound(double[] t, double value) {
        int lo = 0, hi = t.length;
        while (lo < hi) {
            int mid = (lo + hi) >>> 1;
            if (t[mid] < value) lo = mid + 1; else hi = mid;
        }
        return lo;
    }

    static int upperBound(double[] t, double value) {
        int lo = 0, hi = t.length;
        while (lo < hi) {
            int mid = (lo + hi) >>> 1;
            if (t[mid] <= value) lo = mid + 1; else hi = mid;
        }
        return lo;
    }

    private static double[] emptyHf(int n) {
        double[] b = new double[n];
        java.util.Arrays.fill(b, Double.NaN);
        return b;
    }

    public record BuildResult(double[] baseline, List<Double> params,
                              double suggestedOnsetC, double suggestedEndsetC,
                              List<String> diagnostics) {}

    /** 线性基线：连接区间两端数据点的直线（区间外 NaN）。 */
    public static BuildResult linear(double[] t, double[] y, double t0, double t1) {
        List<String> diag = new ArrayList<>();
        int n = t.length;
        int i0 = Math.max(0, lowerBound(t, t0));
        int i1 = Math.min(n - 1, upperBound(t, t1) - 1);
        double[] b = emptyHf(n);
        if (i1 <= i0) {
            diag.add("empty-interval:[" + t0 + "," + t1 + "]内无有效点");
            return new BuildResult(b, List.of(), Double.NaN, Double.NaN, diag);
        }
        double slope = (y[i1] - y[i0]) / (t[i1] - t[i0]);
        double intercept = y[i0] - slope * t[i0];
        for (int i = i0; i <= i1; i++) b[i] = slope * t[i] + intercept;
        return new BuildResult(b, List.of(t[i0], t[i1]), t[i0], t[i1], diag);
    }

    /**
     * 切线基线（经典 onset/endset 构造）：
     * 转折前后最小二乘切线 + 最陡点处拐点切线，交点给出 onset/endset；
     * 积分基线为两交点之间的直线。anchors 为用户选定的四段锚点温度
     * [左起, 左止, 右起, 右止]；缺省时取区间两端各 25%。
     */
    public static BuildResult tangent(double[] t, double[] y, double t0, double t1,
                                      List<Double> anchors, int signMul) {
        List<String> diag = new ArrayList<>();
        int n = t.length;
        int i0 = Math.max(0, lowerBound(t, t0));
        int i1 = Math.min(n - 1, upperBound(t, t1) - 1);
        double[] b = emptyHf(n);
        if (i1 <= i0) {
            diag.add("empty-interval:[" + t0 + "," + t1 + "]内无有效点");
            return new BuildResult(b, List.of(), Double.NaN, Double.NaN, diag);
        }
        double span = t1 - t0;
        double la0 = anchors != null && anchors.size() > 0 ? anchors.get(0) : t0;
        double la1 = anchors != null && anchors.size() > 1 ? anchors.get(1) : t0 + 0.25 * span;
        double ra0 = anchors != null && anchors.size() > 2 ? anchors.get(2) : t1 - 0.25 * span;
        double ra1 = anchors != null && anchors.size() > 3 ? anchors.get(3) : t1;

        int jl0 = clamp(lowerBound(t, Math.max(t0, la0)), i0, i1);
        int jl1 = clamp(upperBound(t, Math.min(t1, la1)) - 1, i0, i1);
        int jr0 = clamp(lowerBound(t, Math.max(t0, ra0)), i0, i1);
        int jr1 = clamp(upperBound(t, Math.min(t1, ra1)) - 1, i0, i1);
        if (jl1 - jl0 < 1 || jr1 - jr0 < 1) {
            diag.add("tangent-anchor-insufficient:切线锚点区间内点数不足");
            return new BuildResult(b, List.of(), Double.NaN, Double.NaN, diag);
        }
        double[] pre = linearFit(t, y, jl0, jl1);
        double[] post = linearFit(t, y, jr0, jr1);

        int peak = i0;
        double best = 0;
        for (int i = i0 + 1; i < i1; i++) {
            double d = (y[i] - pre[0] * t[i] - pre[1]) * signMul;
            if (d > best) { best = d; peak = i; }
        }
        // 最陡斜率（按符号约定后）
        int steep = peak;
        double steepAbs = 0;
        for (int i = i0 + 1; i < i1; i++) {
            double ds = (y[i + 1] - y[i - 1]) / (t[i + 1] - t[i - 1]);
            if (Math.abs(ds) > steepAbs) { steepAbs = Math.abs(ds); steep = i; }
        }
        double k = (steep > 0 && steep < n - 1)
                ? (y[steep + 1] - y[steep - 1]) / (t[steep + 1] - t[steep - 1])
                : 0;
        double cInf = y[steep] - k * t[steep];
        double onsetT = intersectX(pre[0], pre[1], k, cInf);
        double endsetT = intersectX(post[0], post[1], k, cInf);
        if (!Double.isFinite(onsetT)) { onsetT = t[i0]; diag.add("tangent-onset-fallback:交点退化"); }
        if (!Double.isFinite(endsetT)) { endsetT = t[i1]; diag.add("tangent-endset-fallback:交点退化"); }
        double onsetY = pre[0] * onsetT + pre[1];
        double endsetY = post[0] * endsetT + post[1];
        double mb = (endsetY - onsetY) / (endsetT - onsetT);
        double cb = onsetY - mb * onsetT;
        int bo = clamp(lowerBound(t, onsetT), 0, n - 1);
        int be = clamp(upperBound(t, endsetT) - 1, 0, n - 1);
        for (int i = bo; i <= be; i++) b[i] = mb * t[i] + cb;
        List<Double> params = List.of(onsetT, endsetT, pre[0], pre[1], post[0], post[1],
                t[steep], k);
        return new BuildResult(b, params, onsetT, endsetT, diag);
    }

    /**
     * 分段基线（重叠转折）：以 splitT 为界各拟一条直线，
     * 用于两个峰重叠时保留第二种合理解释。
     */
    public static BuildResult step(double[] t, double[] y, double t0, double t1, double splitT) {
        List<String> diag = new ArrayList<>();
        int n = t.length;
        int i0 = Math.max(0, lowerBound(t, t0));
        int i1 = Math.min(n - 1, upperBound(t, t1) - 1);
        double[] b = emptyHf(n);
        int isplit = clamp(lowerBound(t, splitT), i0, i1);
        if (i1 <= i0 || isplit <= i0 || isplit >= i1) {
            diag.add("empty-or-bad-split-interval:[" + t0 + "," + t1 + "] split=" + splitT);
            return new BuildResult(b, List.of(splitT), Double.NaN, Double.NaN, diag);
        }
        double[] left = linearFit(t, y, i0, isplit);
        double[] right = linearFit(t, y, isplit + 1, i1);
        for (int i = i0; i <= isplit; i++) b[i] = left[0] * t[i] + left[1];
        for (int i = isplit + 1; i <= i1; i++) b[i] = right[0] * t[i] + right[1];
        return new BuildResult(b, List.of(splitT), t[i0], t[i1], diag);
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    private static double intersectX(double k1, double c1, double k2, double c2) {
        if (Math.abs(k1 - k2) < 1e-12) return Double.NaN;
        return (c2 - c1) / (k1 - k2);
    }

    public static List<Double> boxed(double[] arr) {
        List<Double> out = new ArrayList<>(arr.length);
        for (double v : arr) out.add(v);
        return out;
    }

    public static BaselineCandidate toCandidate(String id, String type, String segmentId,
                                                String massVersionId, double t0, double t1,
                                                List<Double> anchors, BuildResult r) {
        return new BaselineCandidate(id, type, segmentId, massVersionId, t0, t1,
                r.params(), boxed(r.baseline()), anchors, r.diagnostics(), false);
    }
}
