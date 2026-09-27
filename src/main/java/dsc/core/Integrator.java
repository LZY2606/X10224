package dsc.core;

import java.util.List;

/**
 * Trapezoid integration on arbitrarily spaced samples. Non-uniform
 * temperature/time spacing is handled natively; no resampling is done.
 */
public final class Integrator {

    private Integrator() {}

    /**
     * Integrates (heatFlow - baseline) over time for the points in [fromIdx, toIdx].
     * Result unit: mW*s = mJ. Points must belong to a single segment.
     */
    public static double integrateOverTime(List<DscPoint> pts, int fromIdx, int toIdx, Baseline baseline) {
        double area = 0.0;
        for (int i = Math.max(fromIdx, 0) + 1; i <= Math.min(toIdx, pts.size() - 1); i++) {
            DscPoint a = pts.get(i - 1);
            DscPoint b = pts.get(i);
            double fa = a.heatFlowMw() - baseline.valueAt(a.tempC());
            double fb = b.heatFlowMw() - baseline.valueAt(b.tempC());
            area += 0.5 * (fa + fb) * (b.timeS() - a.timeS());
        }
        return area;
    }

    /** Indices of points whose temperature lies inside [loC, hiC] (order-free). */
    public static int[] windowIndices(List<DscPoint> pts, double loC, double hiC) {
        double lo = Math.min(loC, hiC);
        double hi = Math.max(loC, hiC);
        int from = -1;
        int to = -1;
        for (int i = 0; i < pts.size(); i++) {
            double t = pts.get(i).tempC();
            if (t >= lo && t <= hi) {
                if (from < 0) from = i;
                to = i;
            }
        }
        return new int[] {from, to};
    }
}
