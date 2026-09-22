package dsc.rule;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** 纯数值工具：线性拟合、非等间隔梯形积分、平滑与差分。全部针对升序 (T,y) 曲线。 */
public final class CurveMath {

    private CurveMath() {
    }

    public record Line(double intercept, double slope) {
        public double y(double x) {
            return intercept + slope * x;
        }
    }

    public static Line fitLine(List<XY> pts) {
        int n = pts.size();
        if (n < 2) {
            throw new IllegalArgumentException("线性拟合至少需要 2 个点");
        }
        double sx = 0, sy = 0, sxx = 0, sxy = 0;
        for (XY p : pts) {
            sx += p.x();
            sy += p.y();
            sxx += p.x() * p.x();
            sxy += p.x() * p.y();
        }
        double den = n * sxx - sx * sx;
        if (den == 0) {
 throw new IllegalArgumentException("温度列共线，无法拟合");
        }
        double slope = (n * sxy - sx * sy) / den;
        double intercept = (sy - slope * sx) / n;
        return new Line(intercept, slope);
    }

    /** 升序、去重温度的曲线视图；若原始顺序非单调则记录（不改变原始点）。 */
    public record SortedCurve(List<XY> points, boolean wasMonotonicInSourceOrder) {
    }

    public static SortedCurve sortAndDedupe(List<XY> pts) {
        boolean mono = true;
        for (int i = 1; i < pts.size(); i++) {
            if (pts.get(i).x() <= pts.get(i - 1).x()) {
                mono = false;
                break;
            }
        }
        List<XY> sorted = new ArrayList<>(pts);
        sorted.sort(Comparator.comparingDouble(XY::x));
        List<XY> out = new ArrayList<>();
        for (XY p : sorted) {
            if (out.isEmpty() || p.x() != out.get(out.size() - 1).x()) {
                out.add(p);
            }
        }
        return new SortedCurve(out, mono);
    }

    /** 区间内点（含端点），要求输入已按温度升序。 */
    public static List<XY> slice(List<XY> curve, double lo, double hi) {
        List<XY> out = new ArrayList<>();
        for (XY p : curve) {
            if (p.x() >= lo && p.x() <= hi) {
                out.add(p);
            }
        }
        return out;
    }

    /**
     * 非等间隔梯形积分。区间端点落在采样点之间时用相邻线性插值补齐，
     * 因此空区间（无任何点）与宽度为零可区分：宽度>0 但无点 -> 抛异常由上层诊断。
     */
    public record IntegralResult(double value, double maxStepK, double minStepK,
                                 int intervals, double trapErrorBound) {
    }

    public static double interpolateY(List<XY> curve, double x) {
        if (curve.isEmpty()) {
            throw new IllegalArgumentException("空曲线无法插值");
        }
        if (x <= curve.get(0).x()) {
            return curve.get(0).y();
        }
        if (x >= curve.get(curve.size() - 1).x()) {
            return curve.get(curve.size() - 1).y();
        }
        for (int i = 1; i < curve.size(); i++) {
            XY a = curve.get(i - 1);
            XY b = curve.get(i);
            if (x >= a.x() && x <= b.x()) {
                double f = (x - a.x()) / (b.x() - a.x());
                return a.y() + f * (b.y() - a.y());
            }
        }
        throw new IllegalStateException("插值失败");
    }

    public static IntegralResult integrate(List<XY> curve, double lo, double hi) {
        if (hi < lo) {
            throw new IllegalArgumentException("积分上界小于下界");
        }
        List<XY> inside = slice(curve, lo, hi);
        if (inside.isEmpty() && lo < hi) {
            throw new EmptyIntervalException(lo, hi);
        }
        List<XY> work = new ArrayList<>();
        work.add(new XY(lo, interpolateY(curve, lo)));
        for (XY p : inside) {
            if (p.x() > lo && p.x() < hi) {
                work.add(p);
            }
        }
        work.add(new XY(hi, interpolateY(curve, hi)));

        double sum = 0;
        double maxStep = 0, minStep = Double.MAX_VALUE;
        double errorBound = 0;
        double[] secondDiff = new double[work.size()];
        for (int i = 1; i < work.size() - 1; i++) {
            double h1 = work.get(i).x() - work.get(i - 1).x();
            double h2 = work.get(i + 1).x() - work.get(i).x();
            double denom = 0.5 * (h1 + h2);
            secondDiff[i] = denom > 0
                    ? (work.get(i + 1).y() - work.get(i).y()) / h2
                        - (work.get(i).y() - work.get(i - 1).y()) / h1
                    : 0;
        }
        for (int i = 1; i < work.size(); i++) {
            double dx = work.get(i).x() - work.get(i - 1).x();
            double area = 0.5 * dx * (work.get(i).y() + work.get(i - 1).y());
            sum += area;
            maxStep = Math.max(maxStep, dx);
            if (dx > 0) {
                minStep = Math.min(minStep, dx);
            }
            double f2 = Math.max(Math.abs(secondDiff[Math.min(i, secondDiff.length - 1)]),
                    Math.abs(secondDiff[Math.max(i - 1, 0)]));
            errorBound += dx * dx * f2 / 9.0;
        }
        if (minStep == Double.MAX_VALUE) {
            minStep = 0;
        }
        return new IntegralResult(sum, maxStep, minStep, work.size() - 1, errorBound);
    }

    public static class EmptyIntervalException extends RuntimeException {
        public EmptyIntervalException(double lo, double hi) {
            super("区间 (" + lo + " K, " + hi + " K) 内没有任何有效采样点");
        }
    }

    /** 等权滑动平均（端点缩短窗），只用于自动检峰，不参与积分。 */
    public static List<XY> movingAverage(List<XY> curve, int halfWidth) {
        List<XY> out = new ArrayList<>();
        for (int i = 0; i < curve.size(); i++) {
            int a = Math.max(0, i - halfWidth);
            int b = Math.min(curve.size() - 1, i + halfWidth);
            double s = 0;
            int n = 0;
            for (int j = a; j <= b; j++) {
                s += curve.get(j).y();
                n++;
            }
            out.add(new XY(curve.get(i).x(), s / n));
        }
        return out;
    }
}
