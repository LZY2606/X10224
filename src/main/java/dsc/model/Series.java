package dsc.model;

import java.util.ArrayList;
import java.util.List;

/**
 * A measured curve. Temperatures keep their coordinate basis ("C" or "K"),
 * heat flow keeps its raw unit ("mW"). Raw representation is never rewritten
 * by the derived layer; normalization happens in derived copies.
 */
public class Series {
    public final double[] temp;
    public final String tempUnit;
    public final double[] heatFlow;
    public final String heatFlowUnit;
    public final List<Diagnostic> diagnostics = new ArrayList<>();

    public Series(double[] temp, String tempUnit, double[] heatFlow, String heatFlowUnit) {
        this.temp = temp;
        this.tempUnit = tempUnit;
        this.heatFlow = heatFlow;
        this.heatFlowUnit = heatFlowUnit;
    }

    public int size() {
        return temp.length;
    }

    /** Trapezoid integral of heatFlow dT, robust to non-uniform spacing. */
    public double integrateHeatFlowOverTemp(double t0, double t1) {
        double sum = 0;
        for (int i = 0; i + 1 < temp.length; i++) {
            double a = Math.max(t0, temp[i]);
            double b = Math.min(t1, temp[i + 1]);
            if (b <= a) continue;
            double fa = lerp(i, a);
            double fb = lerp(i, b);
            sum += 0.5 * (fa + fb) * (b - a);
        }
        return sum;
    }

    public double lerp(int seg, double t) {
        double tA = temp[seg];
        double tB = temp[seg + 1];
        if (tB == tA) return heatFlow[seg];
        double f = (t - tA) / (tB - tA);
        return heatFlow[seg] + f * (heatFlow[seg + 1] - heatFlow[seg]);
    }

    /** Value at t by interpolation, or NaN outside the measured range. */
    public double at(double t) {
        if (temp.length == 0 || t < temp[0] || t > temp[temp.length - 1]) return Double.NaN;
        for (int i = 0; i + 1 < temp.length; i++) {
            if (t >= temp[i] && t <= temp[i + 1]) return lerp(i, t);
        }
        return heatFlow[heatFlow.length - 1];
    }

    public int countIn(double t0, double t1) {
        int n = 0;
        for (double v : temp) if (v >= t0 && v <= t1) n++;
        return n;
    }

    /** Detect non-uniform temperature spacing beyond a relative tolerance. */
    public boolean spacingNonUniform(double relTol) {
        if (temp.length < 3) return false;
        double[] d = new double[temp.length - 1];
        double mean = 0;
        for (int i = 0; i < d.length; i++) { d[i] = temp[i + 1] - temp[i]; mean += d[i]; }
        mean /= d.length;
        if (mean == 0) return true;
        for (double v : d) if (Math.abs(v - mean) > relTol * Math.abs(mean)) return true;
        return false;
    }
}
