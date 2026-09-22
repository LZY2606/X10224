package dsc.casepack;

import java.util.ArrayList;
import java.util.List;

/**
 * Deterministic synthetic DSC curves with real shapes: sigmoid glass step,
 * Gaussian crystallization/melting peaks, optional reversed heat flow,
 * non-uniform temperature spacing and deliberately unparseable cells.
 * Fixture-only — never downloads anything.
 */
public final class SyntheticDsc {

    private SyntheticDsc() {}

    public static final class Row {
        public final String t;
        public final String hf;
        public Row(String t, String hf) { this.t = t; this.hf = hf; }
    }

    /** Heating curve: glass step ~72°C, cold crystallization ~115°C, melt ~155°C. */
    public static List<Row> heatingCurve(double t0, double t1, boolean nonUniform, boolean reversed) {
        List<Row> rows = new ArrayList<>();
        double t = t0;
        while (t <= t1 + 1e-9) {
            double hf = heatingHeatFlow(t);
            if (reversed) hf = -hf;
            rows.add(new Row(f(t), f(hf)));
            t += nonUniform ? nextStep(t) : 1.0;
        }
        return rows;
    }

    /** Cooling curve: crystallization ~140°C, possibly glass overshoot ~70°C. */
    public static List<Row> coolingCurve(double t0, double t1, boolean reversed) {
        List<Row> rows = new ArrayList<>();
        double t = t0;
        while (t >= t1 - 1e-9) {
            double hf = coolingHeatFlow(t);
            if (reversed) hf = -hf;
            rows.add(new Row(f(t), f(hf)));
            t -= 1.0;
        }
        return rows;
    }

    private static double heatingHeatFlow(double t) {
        double baseline = 0.02 * (t - 40.0);
        double glassStep = 1.2 / (1.0 + Math.exp(-(t - 72.0) / 1.8));
        double coldCryst = -2.8 * Math.exp(-sq(t - 115.0) / (2 * 4.2 * 4.2));
        double melt = 6.5 * Math.exp(-sq(t - 155.0) / (2 * 3.2 * 3.2));
        return baseline + glassStep + coldCryst + melt;
    }

    private static double coolingHeatFlow(double t) {
        double baseline = 0.02 * (t - 40.0);
        double cryst = -4.0 * Math.exp(-sq(t - 140.0) / (2 * 4.0 * 4.0));
        double glassOvershoot = 0.8 * Math.exp(-sq(t - 68.0) / (2 * 2.0 * 2.0));
        return baseline + cryst + glassOvershoot;
    }

    private static double nextStep(double t) {
        // cluster more points around the transitions, wider steps elsewhere
        if (Math.abs(t - 155) < 12) return 0.5;
        if (Math.abs(t - 115) < 10) return 0.6;
        return 1.3;
    }

    /** Two overlapping melts at 151/156 to exercise overlapping transitions. */
    public static List<Row> overlappingCurve() {
        List<Row> rows = new ArrayList<>();
        for (double t = 120; t <= 180.0001; t += 0.7) {
            double hf = 0.01 * (t - 120)
                    + 5.0 * Math.exp(-sq(t - 151.0) / (2 * 3.0 * 3.0))
                    + 5.6 * Math.exp(-sq(t - 156.5) / (2 * 3.4 * 3.4));
            rows.add(new Row(f(t), f(hf)));
        }
        return rows;
    }

    public static List<Row> withBadCells(List<Row> rows, int... indices) {
        List<Row> out = new ArrayList<>(rows);
        for (int i : indices) {
            Row old = out.get(i);
            out.set(i, new Row(old.t, "n/a"));
        }
        return out;
    }

    private static double sq(double x) { return x * x; }
    private static String f(double v) { return String.format(java.util.Locale.ROOT, "%.4f", v); }
}
