package dsc.core;

import java.util.ArrayList;
import java.util.List;

/**
 * 一段 DSC 曲线（单个扫描段）。温度单位与热流单位随原始表示保留；
 * 规范化（如 mW -> J/g）只发生在派生层。
 */
public record Series(List<Point> points, String tempUnit, String heatFlowUnit) {

    public Series {
        points = List.copyOf(points);
    }

    public int size() { return points.size(); }

    public double minT() { return points.stream().mapToDouble(Point::tC).min().orElse(Double.NaN); }
    public double maxT() { return points.stream().mapToDouble(Point::tC).max().orElse(Double.NaN); }

    /** 非等间隔温度检测：相邻步长相对偏差超过 tol 即视为非等间隔。 */
    public boolean isNonUniform(double tol) {
        if (points.size() < 3) return false;
        List<Double> steps = new ArrayList<>();
        for (int i = 1; i < points.size(); i++) {
            steps.add(points.get(i).tC() - points.get(i - 1).tC());
        }
        double mean = steps.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        if (mean == 0) return true;
        for (double s : steps) {
            if (Math.abs(s - mean) / Math.abs(mean) > tol) return true;
        }
        return false;
    }

    /** 温度是否单调下降（冷却段原始顺序）。 */
    public boolean isDescending() {
        return points.size() > 1 && points.get(points.size() - 1).tC() < points.get(0).tC();
    }

    /** 取温度窗口 [loC, hiC] 内的点，保持原始顺序（冷却段仍为降序）。 */
    public Series window(double loC, double hiC) {
        double lo = Math.min(loC, hiC), hi = Math.max(loC, hiC);
        List<Point> out = new ArrayList<>();
        for (Point p : points) {
            if (p.tC() >= lo && p.tC() <= hi) out.add(p);
        }
        return new Series(out, tempUnit, heatFlowUnit);
    }
}
