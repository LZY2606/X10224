package dsc.core;

import java.util.ArrayList;
import java.util.List;
import java.util.OptionalDouble;

/** Core DSC numerics. All quantities carry their coordinate basis explicitly:
 *  temperature in degC, time in seconds, heat flow in mW. */
public final class DscMath {

    public record Point(double tSec, double tempC, double hfMw) {}

    /** Linear baseline in temperature coordinates through (t1,y1)-(t2,y2). */
    public record LinearBaseline(double t1, double y1, double t2, double y2) {
        public double at(double tempC) {
            if (t2 == t1) return y1;
            return y1 + (y2 - y1) * (tempC - t1) / (t2 - t1);
        }
    }

    /** Sigmoidal (step) baseline for glass transitions, in temperature coordinates. */
    public record SigmoidalBaseline(double yLow, double yHigh, double tMid, double width) {
        public double at(double tempC) {
            double w = width == 0 ? 1e-9 : width;
            return yLow + (yHigh - yLow) / (1.0 + Math.exp(-(tempC - tMid) / w));
        }
    }

    public sealed interface Baseline permits LinearBaseline, SigmoidalBaseline {
        double at(double tempC);
    }

    /** Points whose temperature lies inside [loC, hiC] (inclusive), preserving time order. */
    public static List<Point> window(List<Point> pts, double loC, double hiC) {
        double lo = Math.min(loC, hiC), hi = Math.max(loC, hiC);
        List<Point> out = new ArrayList<>();
        for (Point p : pts) if (p.tempC() >= lo && p.tempC() <= hi) out.add(p);
        return out;
    }

    /** Trapezoid integral of (hf - baseline) dt over the temperature interval.
     *  Works on the actual (non-uniformly spaced) samples; boundaries are interpolated.
     *  Returns mW*s = mJ. Empty when the interval contains no samples. */
    public static OptionalDouble enthalpyMj(List<Point> pts, double aC, double bC, Baseline bl) {
        double lo = Math.min(aC, bC), hi = Math.max(aC, bC);
        List<Point> in = window(pts, lo, hi);
        if (in.isEmpty()) return OptionalDouble.empty();
        double area = 0.0;
        // integrate between consecutive samples
        for (int i = 1; i < in.size(); i++) {
            Point p0 = in.get(i - 1), p1 = in.get(i);
            double d0 = p0.hfMw() - bl.at(p0.tempC());
            double d1 = p1.hfMw() - bl.at(p1.tempC());
            area += 0.5 * (d0 + d1) * (p1.tSec() - p0.tSec());
        }
        // extend to interpolated boundaries if interval edges fall outside first/last sample
        Point first = in.get(0), last = in.get(in.size() - 1);
        if (first.tempC() > lo) {
            double hfLo = interpHf(pts, lo);
            if (!Double.isNaN(hfLo)) {
                double tLo = interpTime(pts, lo);
                area += 0.5 * ((hfLo - bl.at(lo)) + (first.hfMw() - bl.at(first.tempC()))) * (first.tSec() - tLo);
            }
        }
        if (last.tempC() < hi) {
            double hfHi = interpHf(pts, hi);
            if (!Double.isNaN(hfHi)) {
                double tHi = interpTime(pts, hi);
                area += 0.5 * ((last.hfMw() - bl.at(last.tempC())) + (hfHi - bl.at(hi))) * (tHi - last.tSec());
            }
        }
        return OptionalDouble.of(area);
    }

    private static double interpHf(List<Point> pts, double tempC) {
        for (int i = 1; i < pts.size(); i++) {
            Point a = pts.get(i - 1), b = pts.get(i);
            if ((a.tempC() - tempC) * (b.tempC() - tempC) <= 0 && a.tempC() != b.tempC()) {
                double f = (tempC - a.tempC()) / (b.tempC() - a.tempC());
                return a.hfMw() + f * (b.hfMw() - a.hfMw());
            }
        }
        return Double.NaN;
    }

    private static double interpTime(List<Point> pts, double tempC) {
        for (int i = 1; i < pts.size(); i++) {
            Point a = pts.get(i - 1), b = pts.get(i);
            if ((a.tempC() - tempC) * (b.tempC() - tempC) <= 0 && a.tempC() != b.tempC()) {
                double f = (tempC - a.tempC()) / (b.tempC() - a.tempC());
                return a.tSec() + f * (b.tSec() - a.tSec());
            }
        }
        return pts.get(0).tSec();
    }

    public record OnsetPeakEndset(Double onsetC, Double peakC, Double endsetC, String deviationSign) {}

    /** Onset/peak/endset from deviation of the signal from the baseline.
     *  Threshold = 5% of peak deviation. Nulls when the interval is empty. */
    public static OnsetPeakEndset onsetPeakEndset(List<Point> pts, double aC, double bC, Baseline bl) {
        List<Point> in = window(pts, aC, bC);
        if (in.isEmpty()) return new OnsetPeakEndset(null, null, null, "none");
        double[] dev = new double[in.size()];
        int peakIdx = 0;
        for (int i = 0; i < in.size(); i++) {
            dev[i] = in.get(i).hfMw() - bl.at(in.get(i).tempC());
            if (Math.abs(dev[i]) > Math.abs(dev[peakIdx])) peakIdx = i;
        }
        double dPeak = dev[peakIdx];
        double thr = 0.05 * Math.abs(dPeak);
        // onset: first sample (from low-temperature side) exceeding threshold
        List<Point> byTemp = new ArrayList<>(in);
        byTemp.sort((p, q) -> Double.compare(p.tempC(), q.tempC()));
        Double onset = null, endset = null;
        for (Point p : byTemp) {
            double d = p.hfMw() - bl.at(p.tempC());
            if (Math.abs(d) >= thr) { onset = p.tempC(); break; }
        }
        for (int i = byTemp.size() - 1; i >= 0; i--) {
            Point p = byTemp.get(i);
            double d = p.hfMw() - bl.at(p.tempC());
            if (Math.abs(d) >= thr) { endset = p.tempC(); break; }
        }
        String sign = dPeak > 0 ? "positive-deviation" : dPeak < 0 ? "negative-deviation" : "none";
        return new OnsetPeakEndset(onset, in.get(peakIdx).tempC(), endset, sign);
    }

    /** True when consecutive sample spacings in temperature are not uniform (rel. tol 1e-6). */
    public static boolean isNonUniform(List<Point> pts) {
        if (pts.size() < 3) return false;
        double ref = 0; boolean first = true;
        for (int i = 1; i < pts.size(); i++) {
            double d = Math.abs(pts.get(i).tempC() - pts.get(i - 1).tempC());
            if (d == 0) continue;
            if (first) { ref = d; first = false; }
            else if (Math.abs(d - ref) / ref > 1e-6) return true;
        }
        return false;
    }

    /** True when any heat-flow value is negative (reverse heat flow). */
    public static boolean hasReverseHeatFlow(List<Point> pts) {
        for (Point p : pts) if (p.hfMw() < 0) return true;
        return false;
    }
}
