package dsc.analysis;

import java.util.ArrayList;
import java.util.List;

/** 曲线级运算：归一化、梯形积分（支持非等间隔）、极值与重叠转折检测。 */
public final class Curves {

    public record Series(double[] x, double[] y) {
        public int size() { return x.length; }
    }

    /** 在 xs 上线性插值 ys；区间外取端点值。 */
    public static double interp(double[] xs, double[] ys, double x) {
        if (xs.length == 0) return Double.NaN;
        if (x <= xs[0]) return ys[0];
        if (x >= xs[xs.length - 1]) return ys[ys.length - 1];
        int lo = 0, hi = xs.length - 1;
        while (hi - lo > 1) {
            int mid = (lo + hi) >>> 1;
            if (xs[mid] <= x) lo = mid; else hi = mid;
        }
        double dx = xs[hi] - xs[lo];
        if (dx == 0) return ys[lo];
        double w = (x - xs[lo]) / dx;
        return ys[lo] + w * (ys[hi] - ys[lo]);
    }

    /** 在 [x0,x1] 内取数据点下标（含边界插值点由调用方处理）。 */
    public static int[] window(double[] xs, double x0, double x1) {
        List<Integer> idx = new ArrayList<>();
        for (int i = 0; i < xs.length; i++) {
            if (xs[i] >= x0 && xs[i] <= x1) idx.add(i);
        }
        return idx.stream().mapToInt(Integer::intValue).toArray();
    }

    /**
     * 梯形积分 ∫(y - baseline(x)) dx / rateKPerS。
     * 严格按实际横坐标间距（非等间隔安全），不做等距假设。
     * 返回 null 表示空区间。
     */
    public static Double integrateOverBaseline(Series s, double x0, double x1,
                                               Baselines.Model baseline, double rateKPerS) {
        List<Double> px = new ArrayList<>();
        List<Double> pr = new ArrayList<>();
        px.add(x0);
        pr.add(interp(s.x, s.y, x0) - baseline.at(x0));
        for (int i : window(s.x, x0, x1)) {
            px.add(s.x[i]);
            pr.add(s.y[i] - baseline.at(s.x[i]));
        }
        px.add(x1);
        pr.add(interp(s.x, s.y, x1) - baseline.at(x1));
        if (px.size() < 2) return null;
        boolean anyData = window(s.x, x0, x1).length > 0;
        if (!anyData) return null;
        double area = 0;
        for (int i = 1; i < px.size(); i++) {
            area += 0.5 * (pr.get(i) + pr.get(i - 1)) * (px.get(i) - px.get(i - 1));
        }
        return area / rateKPerS; // (mW/mg)*K / (K/s) = (J/s)/g * s = J/g
    }

    /** 区间内残差绝对值最大的点；空区间返回 null。 */
    public static double[] peak(Series s, double x0, double x1, Baselines.Model baseline) {
        int[] idx = window(s.x, x0, x1);
        if (idx.length == 0) return null;
        double best = 0, bx = Double.NaN, br = 0;
        for (int i : idx) {
            double r = s.y[i] - baseline.at(s.x[i]);
            if (Math.abs(r) > Math.abs(best)) { best = r; bx = s.x[i]; br = r; }
        }
        return new double[]{bx, br};
    }

    /**
     * 重叠转折检测：对残差做 3 点滑动平均后统计一阶差分符号变化。
     * 同向双峰（吸热叠吸热等）会产生 >=3 次符号变化。
     */
    public static int residualExtrema(Series s, double x0, double x1, Baselines.Model baseline) {
        int[] idx = window(s.x, x0, x1);
        if (idx.length < 5) return idx.length <= 1 ? 0 : 1;
        double[] r = new double[idx.length];
        for (int k = 0; k < idx.length; k++) r[k] = s.y[idx[k]] - baseline.at(s.x[idx[k]]);
        double[] sm = new double[r.length];
        for (int k = 0; k < r.length; k++) {
            double a = r[Math.max(0, k - 1)], b = r[k], c = r[Math.min(r.length - 1, k + 1)];
            sm[k] = (a + b + c) / 3.0;
        }
        double scale = 0;
        for (double v : sm) scale = Math.max(scale, Math.abs(v));
        double eps = Math.max(1e-9, scale * 0.02);
        int extrema = 0, prevSign = 0;
        for (int k = 1; k < sm.length; k++) {
            double d = sm[k] - sm[k - 1];
            int sign = d > eps ? 1 : (d < -eps ? -1 : 0);
            if (sign != 0) {
                if (prevSign != 0 && sign != prevSign) extrema++;
                prevSign = sign;
            }
        }
        return extrema;
    }

    /** 局部最小二乘拟合切线：以 anchor 为中心取窗口内点拟合 y = a x + b。 */
    public static double[] tangentLine(Series s, double anchor, double halfWindow) {
        double sx = 0, sy = 0, sxx = 0, sxy = 0;
        int n = 0;
        for (int i = 0; i < s.x.length; i++) {
            if (Math.abs(s.x[i] - anchor) <= halfWindow) {
                sx += s.x[i]; sy += s.y[i]; sxx += s.x[i] * s.x[i]; sxy += s.x[i] * s.y[i]; n++;
            }
        }
        if (n < 2) return null;
        double denom = n * sxx - sx * sx;
        if (denom == 0) return null;
        double a = (n * sxy - sx * sy) / denom;
        double b = (sy - a * sx) / n;
        return new double[]{a, b};
    }

    /** 两直线交点 x；平行返回 null。 */
    public static Double intersect(double a1, double b1, double a2, double b2) {
        if (Math.abs(a1 - a2) < 1e-12) return null;
        return (b2 - b1) / (a1 - a2);
    }

    private Curves() {}
}
