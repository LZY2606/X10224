package dsc.core;

import java.util.List;

/** 由用户选定的切线点拟合基线。切线与仪器输出分开存档，这里只消费拟合输入。 */
public final class BaselineFitter {

    private BaselineFitter() {}

    /** 最小二乘直线，返回 [截距, 斜率]。点不足时抛 IllegalArgumentException。 */
    public static double[] fitLine(List<Point> pts) {
        if (pts.size() < 2) throw new IllegalArgumentException("切线点不足: " + pts.size());
        double sx = 0, sy = 0, sxx = 0, sxy = 0;
        int n = pts.size();
        for (Point p : pts) {
            sx += p.tC(); sy += p.hfMw(); sxx += p.tC() * p.tC(); sxy += p.tC() * p.hfMw();
        }
        double denom = n * sxx - sx * sx;
        if (Math.abs(denom) < 1e-12) throw new IllegalArgumentException("切线点退化（温度无变化）");
        double b = (n * sxy - sx * sy) / denom;
        double a = (sy - b * sx) / n;
        return new double[]{a, b};
    }

    /** 由左右切线构造线性连接基线（在 tLo/tHi 处取左右切线值后直线相连）。 */
    public static Baseline linearBetween(double[] left, double[] right, double tLo, double tHi) {
        double yLo = left[0] + left[1] * tLo;
        double yHi = right[0] + right[1] * tHi;
        double b = (yHi - yLo) / (tHi - tLo);
        double a = yLo - b * tLo;
        return Baseline.linear(a, b);
    }

    /** 由左右切线构造 sigmoid 过渡基线，中点取两切线交点。 */
    public static Baseline sigmoidalBetween(double[] left, double[] right, double tLo, double tHi) {
        double tMid = (Math.abs(right[1] - left[1]) < 1e-12)
                ? (tLo + tHi) / 2
                : (left[0] - right[0]) / (right[1] - left[1]);
        if (tMid < tLo || tMid > tHi) tMid = (tLo + tHi) / 2;
        double k = 8.0 / Math.max(1e-6, (tHi - tLo));
        return Baseline.sigmoidal(left[0], left[1], right[0], right[1], k, tMid);
    }
}
