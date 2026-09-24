package dsc.rules;

import java.util.ArrayList;
import java.util.List;

/** 纯数学工具：线性插值、梯形积分、等距重采样——支持非等间隔温度。 */
public final class CurveMath {
    private CurveMath() {
    }

    /** 在单调升温点列上插值；越界返回端点值。仅使用未排除点。 */
    public static double interpolate(List<NormPoint> pts, double tK) {
        if (pts.isEmpty()) {
            return Double.NaN;
        }
        NormPoint first = pts.get(0);
        NormPoint last = pts.get(pts.size() - 1);
        if (tK <= first.tK()) {
            return first.hfPerMass();
        }
        if (tK >= last.tK()) {
            return last.hfPerMass();
        }
        for (int i = 0; i < pts.size() - 1; i++) {
            NormPoint a = pts.get(i);
            NormPoint b = pts.get(i + 1);
            if (tK >= a.tK() && tK <= b.tK()) {
                double f = (tK - a.tK()) / (b.tK() - a.tK());
                return a.hfPerMass() + f * (b.hfPerMass() - a.hfPerMass());
            }
        }
        return last.hfPerMass();
    }

    /**
     * 区间梯形积分 ∫ y dT，单位 J/g（W/g * K = J/g）。
     * 对每个相邻采样对处理区间重叠；非等间隔由实际 ΔT 直接体现。
     * 排除点不参与。
     */
    public static double trapezoid(List<NormPoint> pts, double t0, double t1) {
        double sum = 0;
        double prevT = Double.NaN;
        double prevY = Double.NaN;
        for (NormPoint p : pts) {
            if (p.excluded()) {
                prevT = Double.NaN;
                continue;
            }
            double t = p.tK();
            double y = p.hfPerMass();
            if (!Double.isNaN(prevT) && t > prevT) {
                double a = Math.max(prevT, t0);
                double b = Math.min(t, t1);
                if (b > a) {
                    double ya = interpolate(pts, a);
                    double yb = interpolate(pts, b);
                    sum += 0.5 * (ya + yb) * (b - a);
                }
            }
            prevT = t;
            prevY = y;
        }
        return sum;
    }

    /** 区间内有效点数。 */
    public static int countPoints(List<NormPoint> pts, double t0, double t1) {
        int n = 0;
        for (NormPoint p : pts) {
            if (!p.excluded() && p.tK() >= t0 && p.tK() <= t1) {
                n++;
            }
        }
        return n;
    }

    /** 温度跨度（K）。 */
    public static double span(double t0, double t1) {
        return Math.abs(t1 - t0);
    }

    /** 等距重采样（用于并排叠加显示）。 */
    public static List<double[]> resample(List<NormPoint> pts, double t0, double t1, int n) {
        List<double[]> out = new ArrayList<>();
        if (n < 2 || pts.isEmpty()) {
            return out;
        }
        for (int i = 0; i < n; i++) {
            double t = t0 + (t1 - t0) * i / (n - 1);
            out.add(new double[]{t, interpolate(pts, t)});
        }
        return out;
    }
}
