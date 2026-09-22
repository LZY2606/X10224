package dsc;

import java.util.ArrayList;
import java.util.List;

/**
 * Core DSC math. Raw instrument text is parsed without losing the original
 * representation; unconvertible cells stay in place and surface as diagnostics.
 */
public final class Dsc {
    private Dsc() {}

    public record Pt(double t, double y, double time, String rawY) {
        public boolean valid() {
            return !Double.isNaN(y);
        }
    }

    public static final class Segment {
        public String id;            // e.g. runA_heat.csv#HEAT#1
        public String outputId;
        public String direction;     // HEAT or COOL, derived from data, never filename
        public int cycleIndex;
        public boolean uniformGrid;
        public String signConvention; // endo_up | exo_up | null (unknown)
        public List<Pt> pts = new ArrayList<>();
        public List<String> diagnostics = new ArrayList<>();

        public double tMin() {
            double m = Double.MAX_VALUE;
            for (Pt p : pts) m = Math.min(m, p.t);
            return m;
        }

        public double tMax() {
            double m = -Double.MAX_VALUE;
            for (Pt p : pts) m = Math.max(m, p.t);
            return m;
        }
    }

    /** Parse one instrument output file into segments. */
    public static List<Segment> parse(String outputId, String filename, String raw) {
        List<Segment> out = new ArrayList<>();
        Segment cur = null;
        String pendingConvention = null;
        for (String line : raw.split("\n", -1)) {
            line = line.trim();
            if (line.isEmpty()) continue;
            if (line.startsWith("#")) {
                String body = line.substring(1).trim();
                if (body.startsWith("segment:")) {
                    cur = new Segment();
                    cur.outputId = outputId;
                    cur.signConvention = pendingConvention;
                    String spec = body.substring("segment:".length()).trim();
                    String[] parts = spec.split("\\s+");
                    cur.direction = parts[0].toUpperCase().startsWith("COOL") ? "COOL" : "HEAT";
                    cur.cycleIndex = out.size() + 1;
                    cur.id = outputId + "#" + cur.direction + "#" + cur.cycleIndex;
                    out.add(cur);
                } else if (body.startsWith("sign_convention:")) {
                    String conv = body.substring("sign_convention:".length()).trim();
                    pendingConvention = conv;
                    if (cur != null) cur.signConvention = conv;
                }
                continue;
            }
            if (line.startsWith("Temp_C")) continue; // column header
            String[] cols = line.split(",");
            if (cols.length < 3) continue;
            if (cur == null) { // single-segment file without explicit marker
                cur = new Segment();
                cur.outputId = outputId;
                cur.signConvention = pendingConvention;
                cur.direction = "HEAT"; // provisional; corrected from data below
                cur.cycleIndex = 1;
                cur.id = outputId + "#HEAT#1";
                out.add(cur);
            }
            double t;
            try {
                t = Double.parseDouble(cols[0].trim());
            } catch (NumberFormatException e) {
                cur.diagnostics.add("UNCONVERTIBLE_TEMPERATURE:" + line);
                continue;
            }
            double y;
            String rawY = cols[1].trim();
            try {
                y = Double.parseDouble(rawY);
            } catch (NumberFormatException e) {
                y = Double.NaN; // kept in place via rawY, excluded from derived math
                cur.diagnostics.add("UNCONVERTIBLE_HEATFLOW:" + rawY + "@" + t);
            }
            double time;
            try {
                time = Double.parseDouble(cols[2].trim());
            } catch (NumberFormatException e) {
                time = Double.NaN;
            }
            cur.pts.add(new Pt(t, y, time, rawY));
        }
        for (Segment s : out) {
            finish(s, filename);
        }
        return out;
    }

    private static void finish(Segment s, String filename) {
        // Direction comes from the temperature course, never from the file name.
        if (s.pts.size() >= 2) {
            double first = s.pts.get(0).t;
            double last = s.pts.get(s.pts.size() - 1).t;
            s.direction = last >= first ? "HEAT" : "COOL";
            s.id = s.outputId + "#" + s.direction + "#" + s.cycleIndex;
        }
        // Grid uniformity: max deviation of dT from median dT.
        List<Double> dts = new ArrayList<>();
        for (int i = 1; i < s.pts.size(); i++) {
            dts.add(Math.abs(s.pts.get(i).t - s.pts.get(i - 1).t));
        }
        if (!dts.isEmpty()) {
            List<Double> sorted = new ArrayList<>(dts);
            sorted.sort(Double::compare);
            double median = sorted.get(sorted.size() / 2);
            double maxDev = 0;
            for (double d : dts) maxDev = Math.max(maxDev, Math.abs(d - median));
            s.uniformGrid = maxDev < 1e-9;
            if (!s.uniformGrid) {
                s.diagnostics.add("NONUNIFORM_TEMPERATURE_GRID:maxDev=" + maxDev);
            }
        }
        // File name is never authoritative; record disagreement as a diagnostic.
        String fn = filename.toLowerCase();
        boolean fnSaysCool = fn.contains("cool");
        boolean fnSaysHeat = fn.contains("heat") && !fnSaysCool;
        if ((fnSaysCool && s.direction.equals("HEAT")) || (fnSaysHeat && s.direction.equals("COOL"))) {
            s.diagnostics.add("FILENAME_DIRECTION_MISMATCH:file=" + filename + ",data=" + s.direction);
        }
    }

    public record Line(double slope, double intercept, double sigma, int n) {
        public double at(double t) {
            return slope * t + intercept;
        }
    }

    public static class EmptyInterval extends RuntimeException {
        public EmptyInterval(String msg) {
            super(msg);
        }
    }

    /** Least-squares tangent over [tLo,tHi]; refuses empty or degenerate intervals. */
    public static Line fitTangent(Segment seg, double tLo, double tHi) {
        double sx = 0, sy = 0, sxx = 0, sxy = 0;
        int n = 0;
        for (Pt p : seg.pts) {
            if (!p.valid() || p.t < tLo || p.t > tHi) continue;
            sx += p.t;
            sy += p.y;
            sxx += p.t * p.t;
            sxy += p.t * p.y;
            n++;
        }
        if (n < 2) {
            throw new EmptyInterval("tangent interval [" + tLo + "," + tHi + "] on " + seg.id
                    + " contains " + n + " usable points");
        }
        double denom = n * sxx - sx * sx;
        if (Math.abs(denom) < 1e-12) {
            throw new EmptyInterval("degenerate tangent interval on " + seg.id);
        }
        double slope = (n * sxy - sx * sy) / denom;
        double intercept = (sy - slope * sx) / n;
        double ss = 0;
        for (Pt p : seg.pts) {
            if (!p.valid() || p.t < tLo || p.t > tHi) continue;
            double r = p.y - (slope * p.t + intercept);
            ss += r * r;
        }
        return new Line(slope, intercept, Math.sqrt(ss / n), n);
    }

    /** Baseline candidate between two tangents. kind: linear-chord | area-sigmoid. */
    public static double baseline(String kind, Line left, Line right, Segment seg,
                                  double onset, double endset, double t) {
        if (kind.equals("linear-chord")) {
            double y1 = left.at(onset);
            double y2 = right.at(endset);
            return y1 + (y2 - y1) * (t - onset) / (endset - onset);
        }
        // area-sigmoid: weight between tangents by cumulative peak area fraction
        double total = 0;
        for (int i = 1; i < seg.pts.size(); i++) {
            Pt a = seg.pts.get(i - 1), b = seg.pts.get(i);
            if (!a.valid() || !b.valid() || b.t < onset || a.t > endset) continue;
            total += 0.5 * (dev(a, left) + dev(b, left)) * (b.t - a.t);
        }
        double acc = 0;
        for (int i = 1; i < seg.pts.size(); i++) {
            Pt a = seg.pts.get(i - 1), b = seg.pts.get(i);
            if (!a.valid() || !b.valid() || b.t < onset || a.t > endset) continue;
            acc += 0.5 * (dev(a, left) + dev(b, left)) * (b.t - a.t);
            if (b.t >= t) break;
        }
        double f = total == 0 ? 0 : Math.min(1.0, Math.max(0.0, acc / total));
        return left.at(t) + (right.at(t) - left.at(t)) * f;
    }

    private static double dev(Pt p, Line l) {
        return Math.abs(p.y - l.at(p.t));
    }

    /** Onset: first point inside window departing from left tangent beyond k*sigma. */
    public static double onset(Segment seg, Line left, double fromT, double k) {
        double threshold = k * Math.max(left.sigma, 1e-6);
        double result = fromT;
        boolean started = false;
        for (Pt p : seg.pts) {
            if (!p.valid() || p.t < fromT) continue;
            started = true;
            if (Math.abs(p.y - left.at(p.t)) > threshold) {
                return p.t;
            }
            result = p.t;
        }
        if (!started) throw new EmptyInterval("no points after " + fromT + " on " + seg.id);
        return result;
    }

    /** Endset: scan backwards from hiT for return to right tangent within k*sigma. */
    public static double endset(Segment seg, Line right, double hiT, double k) {
        double threshold = k * Math.max(right.sigma, 1e-6);
        double result = hiT;
        boolean started = false;
        for (int i = seg.pts.size() - 1; i >= 0; i--) {
            Pt p = seg.pts.get(i);
            if (!p.valid() || p.t > hiT) continue;
            started = true;
            if (Math.abs(p.y - right.at(p.t)) > threshold) {
                return p.t;
            }
            result = p.t;
        }
        if (!started) throw new EmptyInterval("no points before " + hiT + " on " + seg.id);
        return result;
    }

    public record Peak(double t, double y, double height) {}

    /** Peaks of (curve - chord) inside [onset,endset] above k*sigma of the window. */
    public static List<Peak> peaks(Segment seg, Line left, Line right,
                                   double onset, double endset, String kind) {
        List<Double> devs = new ArrayList<>();
        List<Pt> win = new ArrayList<>();
        for (Pt p : seg.pts) {
            if (!p.valid() || p.t < onset || p.t > endset) continue;
            win.add(p);
            devs.add(p.y - baseline(kind, left, right, seg, onset, endset, p.t));
        }
        double maxAbs = 0;
        for (double d : devs) maxAbs = Math.max(maxAbs, Math.abs(d));
        double threshold = Math.max(0.15 * maxAbs, 3 * Math.max(left.sigma, right.sigma));
        List<Peak> out = new ArrayList<>();
        for (int i = 1; i < win.size() - 1; i++) {
            double d0 = devs.get(i - 1), d1 = devs.get(i), d2 = devs.get(i + 1);
            boolean localMax = d1 >= d0 && d1 >= d2 && d1 > threshold;
            boolean localMin = d1 <= d0 && d1 <= d2 && -d1 > threshold;
            if (localMax || localMin) {
                Pt p = win.get(i);
                out.add(new Peak(p.t, p.y, d1));
            }
        }
        if (out.isEmpty() && !win.isEmpty()) {
            int best = 0;
            for (int i = 1; i < devs.size(); i++) {
                if (Math.abs(devs.get(i)) > Math.abs(devs.get(best))) best = i;
            }
            Pt p = win.get(best);
            out.add(new Peak(p.t, p.y, devs.get(best)));
        }
        return out;
    }

    /**
     * Enthalpy in J/g via trapezoids over the real (possibly non-uniform) grid.
     * mW * K / (K/min) * 60 s/min = mJ; mJ/mg = J/g.
     */
    public static double enthalpy(Segment seg, Line left, Line right, String kind,
                                  double onset, double endset,
                                  double scanRateKPerMin, double massMg) {
        double mj = 0;
        for (int i = 1; i < seg.pts.size(); i++) {
            Pt a = seg.pts.get(i - 1), b = seg.pts.get(i);
            if (!a.valid() || !b.valid()) continue;
            double t0 = Math.max(a.t, onset), t1 = Math.min(b.t, endset);
            if (t1 <= t0 || b.t < onset || a.t > endset) continue;
            double ya = interp(a, b, t0) - baseline(kind, left, right, seg, onset, endset, t0);
            double yb = interp(a, b, t1) - baseline(kind, left, right, seg, onset, endset, t1);
            mj += 0.5 * (ya + yb) * (t1 - t0) / scanRateKPerMin * 60.0;
        }
        return mj / massMg;
    }

    private static double interp(Pt a, Pt b, double t) {
        if (b.t == a.t) return a.y;
        return a.y + (b.y - a.y) * (t - a.t) / (b.t - a.t);
    }

    /** Error-source breakdown for one enthalpy value, all in J/g. */
    public static List<String[]> errorSources(double enthalpy, double otherCandidateEnthalpy,
                                              Line left, Line right, double onset, double endset,
                                              double scanRate, double massMg, double massTolMg,
                                              double calibUncertainty) {
        List<String[]> out = new ArrayList<>();
        out.add(new String[]{"baseline-choice",
                fmt(Math.abs(enthalpy - otherCandidateEnthalpy) / 2.0)});
        double sigma = Math.max(left.sigma, right.sigma);
        out.add(new String[]{"noise",
                fmt(sigma * (endset - onset) / scanRate * 60.0 / massMg)});
        out.add(new String[]{"temperature-calibration", fmt(Math.abs(enthalpy) * calibUncertainty)});
        out.add(new String[]{"sample-mass", fmt(Math.abs(enthalpy) * massTolMg / massMg)});
        return out;
    }

    private static String fmt(double v) {
        return String.format(java.util.Locale.ROOT, "%.6f", v);
    }
}
