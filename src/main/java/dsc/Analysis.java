package dsc;

import dsc.Model.*;
import java.util.ArrayList;
import java.util.List;

/** Pure DSC math. All functions are deterministic and side-effect free so that
 *  a derivation version is fully described by (inputs, code). */
public final class Analysis {
    private Analysis() {}

    /** A line y = a*x + b, used for baselines and tangents. */
    public record Line(double a, double b) {
        public double at(double x) { return a * x + b; }
        public static Line through(double x1, double y1, double x2, double y2) {
            double a = (x2 == x1) ? 0.0 : (y2 - y1) / (x2 - x1);
            return new Line(a, y1 - a * x1);
        }
        public static double intersectX(Line p, Line q) {
            if (Math.abs(p.a - q.a) < 1e-12) return Double.NaN;
            return (q.b - p.b) / (p.a - q.a);
        }
    }

    public sealed interface BaselineFn {
        double at(double tempC);
        /** pre/post tangent lines if the baseline is built from them, else null. */
        default Line pre() { return null; }
        default Line post() { return null; }
    }

    /** Straight line through the two interval endpoints of the curve. */
    public static BaselineFn linear(List<ScanPoint> pts, double t0, double t1) {
        double y0 = interp(pts, t0);
        double y1 = interp(pts, t1);
        Line l = Line.through(t0, y0, t1, y1);
        return new BaselineFn() {
            public double at(double x) { return l.at(x); }
            public String toString() { return "linear"; }
        };
    }

    /** Piecewise baseline from user-selected pre/post tangents (switch at intersection). */
    public static BaselineFn tangent(Line pre, Line post) {
        double xCross = Line.intersectX(pre, post);
        return new BaselineFn() {
            public double at(double x) {
                if (Double.isNaN(xCross)) return pre.at(x);
                return x <= xCross ? pre.at(x) : post.at(x);
            }
            public Line pre() { return pre; }
            public Line post() { return post; }
            public String toString() { return "tangent"; }
        };
    }

    /** Sigmoidal (logistic blend) baseline for glass transitions. */
    public static BaselineFn sigmoidal(Line pre, Line post, double midC, double widthC) {
        double w = Math.max(widthC, 1e-6);
        return new BaselineFn() {
            public double at(double x) {
                double s = 1.0 / (1.0 + Math.exp(-(x - midC) / w));
                return pre.at(x) * (1.0 - s) + post.at(x) * s;
            }
            public Line pre() { return pre; }
            public Line post() { return post; }
            public String toString() { return "sigmoidal"; }
        };
    }

    /** Linear interpolation of heat flow at temperature x; points must belong to one segment. */
    public static double interp(List<ScanPoint> pts, double x) {
        if (pts.isEmpty()) return Double.NaN;
        boolean heating = pts.get(pts.size() - 1).tempC() >= pts.get(0).tempC();
        if (heating) {
            if (x <= pts.get(0).tempC()) return pts.get(0).heatflowMw();
            if (x >= pts.get(pts.size() - 1).tempC()) return pts.get(pts.size() - 1).heatflowMw();
        } else {
            if (x >= pts.get(0).tempC()) return pts.get(0).heatflowMw();
            if (x <= pts.get(pts.size() - 1).tempC()) return pts.get(pts.size() - 1).heatflowMw();
        }
        for (int i = 1; i < pts.size(); i++) {
            double xa = pts.get(i - 1).tempC(), xb = pts.get(i).tempC();
            if ((x - xa) * (x - xb) <= 0 && xa != xb) {
                double f = (x - xa) / (xb - xa);
                return pts.get(i - 1).heatflowMw() + f * (pts.get(i).heatflowMw() - pts.get(i - 1).heatflowMw());
            }
        }
        return pts.get(pts.size() - 1).heatflowMw();
    }

    /** Points of the interval, ordered by time. Empty list when the interval holds no data. */
    public static List<ScanPoint> window(List<ScanPoint> pts, double t0, double t1) {
        double lo = Math.min(t0, t1), hi = Math.max(t0, t1);
        List<ScanPoint> out = new ArrayList<>();
        for (ScanPoint p : pts) {
            if (p.tempC() == null || p.timeS() == null || p.heatflowMw() == null) continue;
            if (p.tempC() >= lo && p.tempC() <= hi) out.add(p);
        }
        return out;
    }

    /** Trapezoid integral of (hf - baseline) over *actual* timestamps (non-uniform safe).
     *  Returns mJ (= mW * s). */
    public static double integrate(List<ScanPoint> pts, BaselineFn base, double t0, double t1) {
        List<ScanPoint> w = window(pts, t0, t1);
        if (w.size() < 2) return 0.0;
        double area = 0.0;
        for (int i = 1; i < w.size(); i++) {
            ScanPoint a = w.get(i - 1), b = w.get(i);
            double fa = a.heatflowMw() - base.at(a.tempC());
            double fb = b.heatflowMw() - base.at(b.tempC());
            area += 0.5 * (fa + fb) * (b.timeS() - a.timeS());
        }
        return area;
    }

    /** Peak temperature: extremum of (hf - baseline) inside the interval. */
    public static double peak(List<ScanPoint> pts, BaselineFn base, double t0, double t1, boolean endoUp) {
        List<ScanPoint> w = window(pts, t0, t1);
        if (w.isEmpty()) return Double.NaN;
        ScanPoint best = w.get(0);
        double bestV = base.at(best.tempC()) - best.heatflowMw(); // placeholder sign set below
        double bestDev = Double.NEGATIVE_INFINITY;
        for (ScanPoint p : w) {
            double dev = p.heatflowMw() - base.at(p.tempC());
            double signed = endoUp ? dev : -dev; // deviation in "endothermic-positive" space
            if (Math.abs(signed) > bestDev) { bestDev = Math.abs(signed); best = p; }
        }
        return best.tempC();
    }

    /** Onset/endset from intersections of the steepest tangents with the baseline
     *  pre/post lines. Falls back to interval bounds (flagged by caller). */
    public static double[] onsetEndset(List<ScanPoint> pts, BaselineFn base, double t0, double t1) {
        List<ScanPoint> w = window(pts, t0, t1);
        if (w.size() < 4 || base.pre() == null || base.post() == null) {
            return new double[]{t0, t1};
        }
        // steepest ascent/descent tangents of the residual
        Line rise = steepest(w, base, true);
        Line fall = steepest(w, base, false);
        double onset = rise == null ? t0 : Line.intersectX(base.pre(), rise);
        double endset = fall == null ? t1 : Line.intersectX(base.post(), fall);
        if (Double.isNaN(onset)) onset = t0;
        if (Double.isNaN(endset)) endset = t1;
        return new double[]{onset, endset};
    }

    private static Line steepest(List<ScanPoint> w, BaselineFn base, boolean rising) {
        double bestSlope = 0;
        Line best = null;
        int n = w.size();
        int half = n / 2;
        int from = rising ? 0 : half, to = rising ? half : n - 1;
        for (int i = Math.max(from, 1); i < to; i++) {
            ScanPoint a = w.get(i - 1), b = w.get(i + 1);
            double dt = b.timeS() - a.timeS();
            if (dt <= 0) continue;
            double ra = a.heatflowMw() - base.at(a.tempC());
            double rb = b.heatflowMw() - base.at(b.tempC());
            double slope = (rb - ra) / (b.tempC() - a.tempC());
            if (Math.abs(slope) > Math.abs(bestSlope)) {
                bestSlope = slope;
                double midX = 0.5 * (a.tempC() + b.tempC());
                double midY = 0.5 * (ra + rb) + base.at(midX);
                best = new Line(slope, midY - slope * midX);
            }
        }
        return best;
    }

    /** Direction from instrument convention + signed residual area. Never from filenames. */
    public static String direction(DirectionConvention conv, double residualArea) {
        if (conv == DirectionConvention.UNKNOWN) return "UNDETERMINED";
        boolean endo = conv == DirectionConvention.ENDO_UP ? residualArea > 0 : residualArea < 0;
        return endo ? "ENDOTHERMIC" : "EXOTHERMIC";
    }

    /** Signed enthalpy in J/g: endothermic positive. residualArea in mW*s = mJ. */
    public static double enthalpyJg(DirectionConvention conv, double residualAreaMj, double massMg) {
        double signed = conv == DirectionConvention.ENDO_DOWN ? -residualAreaMj : residualAreaMj;
        return signed / massMg; // mJ/mg == J/g
    }
}
