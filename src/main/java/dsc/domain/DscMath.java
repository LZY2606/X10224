package dsc.domain;

import java.util.ArrayList;
import java.util.List;

/** Pure DSC numerics. All functions tolerate non-uniform spacing and reversed axes. */
public final class DscMath {

    /** A raw sample point as imported: time[s], temperature[degC], heatFlow[mW]. */
    public record Point(double t, double temp, double hf) {}

    /** Onset/peak/endset marker triple, in temperature coordinates. */
    public record Markers(Double onset, Double peak, Double endset) {}

    private DscMath() {}

    /** Linear baseline through the two interval boundary points. */
    public static double[] linearBaseline(List<Point> pts) {
        if (pts.size() < 2) throw new IllegalArgumentException("need >=2 points");
        Point a = pts.get(0), b = pts.get(pts.size() - 1);
        double[] out = new double[pts.size()];
        double span = b.temp - a.temp;
        for (int i = 0; i < pts.size(); i++) {
            double f = span == 0 ? 0 : (pts.get(i).temp - a.temp) / span;
            out[i] = a.hf + f * (b.hf - a.hf);
        }
        return out;
    }

    /**
     * Sigmoid (step-weighted) baseline: interpolates with a smoothstep weight so it
     * hugs the pre-transition tangent longer, common for melting with a cp step.
     */
    public static double[] sigmoidBaseline(List<Point> pts) {
        if (pts.size() < 2) throw new IllegalArgumentException("need >=2 points");
        Point a = pts.get(0), b = pts.get(pts.size() - 1);
        double[] out = new double[pts.size()];
        double span = b.temp - a.temp;
        for (int i = 0; i < pts.size(); i++) {
            double f = span == 0 ? 0 : (pts.get(i).temp - a.temp) / span;
            double s = f * f * (3 - 2 * f);
            out[i] = a.hf + s * (b.hf - a.hf);
        }
        return out;
    }

    /** Baseline value at an arbitrary temperature, given per-point baseline samples. */
    public static double baselineAt(List<Point> pts, double[] base, double temp) {
        if (pts.isEmpty()) return 0;
        for (int i = 1; i < pts.size(); i++) {
            double t0 = pts.get(i - 1).temp, t1 = pts.get(i).temp;
            if ((temp - t0) * (temp - t1) <= 0 && t0 != t1) {
                double f = (temp - t0) / (t1 - t0);
                return base[i - 1] + f * (base[i] - base[i - 1]);
            }
        }
        return base[temp < pts.get(0).temp ? 0 : base.length - 1];
    }

    /**
     * Markers for a transition: peak = extremum of (hf - baseline) respecting the
     * expected peak sign; onset/endset = first/last crossing of a noise-scaled
     * threshold around the interval edges.
     */
    public static Markers markers(List<Point> pts, double[] base, int peakSign) {
        if (pts.isEmpty()) return new Markers(null, null, null);
        double peakDev = 0;
        Double peak = null;
        for (int i = 0; i < pts.size(); i++) {
            double dev = pts.get(i).hf - base[i];
            if (peak == null || dev * peakSign > peakDev * peakSign) {
                peakDev = dev;
                peak = pts.get(i).temp;
            }
        }
        double threshold = Math.abs(peakDev) * 0.02 + 1e-9;
        Double onset = null, endset = null;
        for (int i = 0; i < pts.size(); i++) {
            if (Math.abs(pts.get(i).hf - base[i]) > threshold) {
                if (onset == null) onset = pts.get(i).temp;
                endset = pts.get(i).temp;
            }
        }
        return new Markers(onset, peak, endset);
    }

    /**
     * Enthalpy of (hf - baseline) integrated over the time axis (trapezoid,
     * non-uniform safe). hf[mW] * dt[s] = mJ. Sign follows the raw signal; the
     * caller maps sign to endo/exo via the instrument convention, never filenames.
     */
    public static double integrateMillijoule(List<Point> pts, double[] base) {
        double area = 0;
        for (int i = 1; i < pts.size(); i++) {
            double d0 = pts.get(i - 1).hf - base[i - 1];
            double d1 = pts.get(i).hf - base[i];
            area += 0.5 * (d0 + d1) * (pts.get(i).t - pts.get(i - 1).t);
        }
        return area; // mJ
    }

    /** Slice points whose temperature lies inside [lo, hi] regardless of axis direction. */
    public static List<Point> slice(List<Point> pts, double lo, double hi) {
        double a = Math.min(lo, hi), b = Math.max(lo, hi);
        List<Point> out = new ArrayList<>();
        for (Point p : pts) if (p.temp >= a && p.temp <= b) out.add(p);
        return out;
    }

    /** Peak sign expected for a transition kind under a signal convention. */
    public static int peakSign(String transitionKind, String convention) {
        boolean endo = switch (transitionKind) {
            case "melting", "glass" -> true;
            case "crystallization", "cold-crystallization" -> false;
            default -> throw new IllegalArgumentException("unknown transition " + transitionKind);
        };
        boolean positive = "exo-up".equals(convention) ? !endo : endo;
        return positive ? 1 : -1;
    }
}
