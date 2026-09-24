package dsc;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * DSC domain math: instrument file parsing, segment splitting (heating and
 * cooling are never mixed), baseline candidates, onset/peak/endset tangents
 * and trapezoid integration. Endo/exo direction comes only from instrument
 * metadata or calibration, never from file names.
 */
public final class Dsc {

    private Dsc() {}

    public record Pt(double tSec, double temp, double hfW) {}

    public record Segment(String id, String type, int cycle, List<Pt> pts) {}

    public record InstrumentData(
            List<Segment> segments,
            Map<String, String> meta,
            List<String> diagnostics,
            String tempUnit,
            boolean tempConverted,
            String convention) {}

    public record Baseline(String method, double[] values) {}

    public record Analysis(
            double onsetT,
            double peakT,
            double endsetT,
            double onsetHf,
            double peakHf,
            double endsetHf,
            double areaJ,
            String direction,
            Map<String, Double> errors,
            List<String> errorSources) {}

    public static final class EmptyInterval extends Exception {
        public EmptyInterval(String msg) { super(msg); }
    }

    /** Parse an instrument CSV. Meta lines start with '# key: value'. */
    public static InstrumentData parse(String csv) {
        Map<String, String> meta = new LinkedHashMap<>();
        List<String> diagnostics = new ArrayList<>();
        String header = null;
        List<String[]> rows = new ArrayList<>();
        for (String line : csv.split("\\R")) {
            String s = line.trim();
            if (s.isEmpty()) continue;
            if (s.startsWith("#")) {
                String body = s.substring(1).trim();
                int colon = body.indexOf(':');
                if (colon > 0) {
                    meta.put(body.substring(0, colon).trim(), body.substring(colon + 1).trim());
                }
            } else if (header == null) {
                header = s;
            } else {
                rows.add(s.split(","));
            }
        }
        if (header == null) throw new IllegalArgumentException("instrument file has no header");
        String[] cols = header.split(",");
        int iTime = -1, iTemp = -1, iHf = -1;
        String tempUnit = null, hfUnit = null;
        for (int i = 0; i < cols.length; i++) {
            String c = cols[i].trim();
            if (c.startsWith("time")) iTime = i;
            else if (c.startsWith("temp")) { iTemp = i; tempUnit = unitOf(c); }
            else if (c.startsWith("heatflow")) { iHf = i; hfUnit = unitOf(c); }
        }
        if (iTime < 0 || iTemp < 0 || iHf < 0) {
            throw new IllegalArgumentException("missing required columns in: " + header);
        }

        // Endo/exo convention: metadata only, never the file name.
        String convention = meta.getOrDefault("heatflow_convention", "").trim().toLowerCase();
        boolean flip;
        if (convention.equals("endo_up")) {
            flip = false;
        } else if (convention.equals("exo_up")) {
            flip = true;
            diagnostics.add("REVERSED_HEAT_FLOW: exo_up convention from instrument metadata; "
                    + "sign normalized in derived layer (never inferred from file name)");
        } else {
            flip = false;
            convention = "unknown";
            diagnostics.add("CONVENTION_UNKNOWN: heatflow_convention missing; "
                    + "endo/exo direction left UNDETERMINED");
        }

        boolean tempConverted = Units.known(tempUnit) && "TEMP".equals(Units.dimensionOf(tempUnit));
        if (!tempConverted) {
            diagnostics.add("UNIT_UNCONVERTIBLE: temperature unit '" + tempUnit
                    + "' kept in raw position; normalized curve uses raw basis");
        }
        boolean hfConverted = Units.known(hfUnit) && "POWER".equals(Units.dimensionOf(hfUnit));
        if (!hfConverted) {
            diagnostics.add("UNIT_UNCONVERTIBLE: heatflow unit '" + hfUnit + "' kept raw");
        }

        List<Pt> pts = new ArrayList<>();
        for (String[] r : rows) {
            double t = Double.parseDouble(r[iTime].trim());
            double temp = Double.parseDouble(r[iTemp].trim());
            double hf = Double.parseDouble(r[iHf].trim());
            double tempSi = tempConverted ? Units.toSi(temp, tempUnit) : temp;
            double hfW = hfConverted ? Units.toSi(hf, hfUnit) : hf;
            pts.add(new Pt(t, tempSi, flip ? -hfW : hfW));
        }

        // Non-uniform temperature spacing: record, do not resample.
        if (pts.size() > 3) {
            double mean = 0;
            int n = 0;
            for (int i = 1; i < pts.size(); i++) {
                double d = Math.abs(pts.get(i).temp() - pts.get(i - 1).temp());
                if (d > 1e-12) { mean += d; n++; }
            }
            mean = n == 0 ? 0 : mean / n;
            if (mean > 0) {
                double maxDev = 0;
                for (int i = 1; i < pts.size(); i++) {
                    double d = Math.abs(pts.get(i).temp() - pts.get(i - 1).temp());
                    if (d > 1e-12) maxDev = Math.max(maxDev, Math.abs(d - mean) / mean);
                }
                if (maxDev > 0.05) {
                    diagnostics.add(String.format(
                            "NON_UNIFORM_TEMPERATURE: spacing deviates up to %.1f%%; "
                                    + "integration uses actual sample times, no resampling", maxDev * 100));
                }
            }
        }

        return new InstrumentData(splitSegments(pts), meta, diagnostics, tempUnit, tempConverted, convention);
    }

    private static String unitOf(String column) {
        int u = column.indexOf('_');
        return u < 0 ? "" : column.substring(u + 1).trim();
    }

    /** Split a point stream into HEATING / COOLING / ISOTHERMAL segments. */
    public static List<Segment> splitSegments(List<Pt> pts) {
        List<Segment> out = new ArrayList<>();
        if (pts.isEmpty()) return out;
        List<Pt> cur = new ArrayList<>();
        int curSign = 0;
        int cycle = 0;
        String curType = null;
        for (int i = 0; i < pts.size(); i++) {
            int sign = 0;
            if (i > 0) {
                double d = pts.get(i).temp() - pts.get(i - 1).temp();
                if (d > 1e-9) sign = 1;
                else if (d < -1e-9) sign = -1;
                else sign = curSign;
            } else {
                sign = 0;
            }
            if (curSign != 0 && sign != 0 && sign != curSign) {
                out.add(new Segment("seg" + (out.size() + 1), curType, cycle, List.copyOf(cur)));
                cur = new ArrayList<>();
                cur.add(pts.get(i - 1));
            }
            if (sign != 0 && sign != curSign) {
                curSign = sign;
                if (sign > 0) {
                    cycle++;
                    curType = "HEATING";
                } else {
                    curType = "COOLING";
                }
            }
            cur.add(pts.get(i));
        }
        if (!cur.isEmpty()) {
            String type = curType == null ? "ISOTHERMAL" : curType;
            out.add(new Segment("seg" + (out.size() + 1), type, cycle, List.copyOf(cur)));
        }
        return out;
    }

    /** Baseline candidates over pts[i1..i2]; all plausible baselines are kept. */
    public static List<Baseline> baselines(List<Pt> pts, int i1, int i2) {
        int n = i2 - i1 + 1;
        double t1 = pts.get(i1).temp(), t2 = pts.get(i2).temp();
        double h1 = pts.get(i1).hfW(), h2 = pts.get(i2).hfW();
        double[] linear = new double[n];
        double[] left = new double[n];
        double[] right = new double[n];
        double[] sigmoid = new double[n];
        for (int k = 0; k < n; k++) {
            double frac = (pts.get(i1 + k).temp() - t1) / (t2 - t1 == 0 ? 1 : t2 - t1);
            linear[k] = h1 + frac * (h2 - h1);
            left[k] = h1;
            right[k] = h2;
            double s = frac * frac * (3 - 2 * frac); // smoothstep
            sigmoid[k] = h1 + s * (h2 - h1);
        }
        return List.of(
                new Baseline("linear", linear),
                new Baseline("left_horizontal", left),
                new Baseline("right_horizontal", right),
                new Baseline("sigmoidal", sigmoid));
    }

    /** Least-squares line fit, returns {slope, intercept} in (temp, hf) space. */
    public static double[] fitLine(List<Pt> pts, int from, int to) {
        double sx = 0, sy = 0, sxx = 0, sxy = 0;
        int n = 0;
        for (int i = from; i <= to && i < pts.size(); i++) {
            double x = pts.get(i).temp(), y = pts.get(i).hfW();
            sx += x; sy += y; sxx += x * x; sxy += x * y; n++;
        }
        double denom = n * sxx - sx * sx;
        if (Math.abs(denom) < 1e-18) return new double[] {0, sy / Math.max(n, 1)};
        double slope = (n * sxy - sx * sy) / denom;
        return new double[] {slope, (sy - slope * sx) / n};
    }

    /**
     * Full transition analysis on pts[i1..i2] of one segment (never across
     * segment boundaries). tangents: optional user tangents as
     * {leftSlope, leftIntercept, rightSlope, rightIntercept} or null.
     */
    public static Analysis analyze(
            List<Pt> pts, int i1, int i2, Baseline baseline, List<Baseline> allCandidates,
            double[] tangents, String convention, double calibTempOffsetK, double calibHfOffsetW)
            throws EmptyInterval {
        int n = i2 - i1 + 1;
        if (n < 3) {
            throw new EmptyInterval("interval holds " + n + " points (need >= 3)");
        }
        int w = Math.max(2, n / 6);
        double[] leftFit = tangents != null
                ? new double[] {tangents[0], tangents[1]}
                : fitLine(pts, i1, i1 + w - 1);
        double[] rightFit = tangents != null
                ? new double[] {tangents[2], tangents[3]}
                : fitLine(pts, i2 - w + 1, i2);

        double t1 = pts.get(i1).temp(), t2 = pts.get(i2).temp();
        double h1 = pts.get(i1).hfW(), h2 = pts.get(i2).hfW();
        double baseSlope = (h2 - h1) / (t2 - t1 == 0 ? 1e-12 : t2 - t1);
        double baseIntercept = h1 - baseSlope * t1;

        double onsetT = intersect(leftFit[0], leftFit[1], baseSlope, baseIntercept, t1);
        double endsetT = intersect(rightFit[0], rightFit[1], baseSlope, baseIntercept, t2);

        // peak: extremum of (hf - baseline) inside the interval
        int peakIdx = i1;
        double peakDev = 0;
        for (int k = 0; k < n; k++) {
            double dev = pts.get(i1 + k).hfW() - baseline.values()[k];
            if (Math.abs(dev) > Math.abs(peakDev)) {
                peakDev = dev;
                peakIdx = i1 + k;
            }
        }
        String direction = switch (convention) {
            case "endo_up" -> peakDev >= 0 ? "ENDOTHERMIC" : "EXOTHERMIC";
            case "exo_up" -> peakDev >= 0 ? "EXOTHERMIC" : "ENDOTHERMIC";
            default -> "UNDETERMINED";
        };

        // trapezoid integration over actual sample times (non-uniform safe)
        double areaJ = 0;
        for (int k = 0; k < n - 1; k++) {
            Pt a = pts.get(i1 + k), b = pts.get(i1 + k + 1);
            double fa = a.hfW() - baseline.values()[k];
            double fb = b.hfW() - baseline.values()[k + 1];
            areaJ += 0.5 * (fa + fb) * (b.tSec() - a.tSec());
        }

        // error sources
        Map<String, Double> errors = new LinkedHashMap<>();
        List<String> sources = new ArrayList<>();
        double minA = Double.MAX_VALUE, maxA = -Double.MAX_VALUE;
        for (Baseline cand : allCandidates) {
            double a = 0;
            for (int k = 0; k < n - 1; k++) {
                Pt pa = pts.get(i1 + k), pb = pts.get(i1 + k + 1);
                a += 0.5 * ((pa.hfW() - cand.values()[k]) + (pb.hfW() - cand.values()[k + 1]))
                        * (pb.tSec() - pa.tSec());
            }
            minA = Math.min(minA, a);
            maxA = Math.max(maxA, a);
        }
        errors.put("baselineSpreadJ", maxA - minA);
        sources.add("baseline choice spread across " + allCandidates.size() + " candidates");

        double[] leftShift = fitLine(pts, i1, Math.min(i1 + w + 1, i2));
        double onsetShift = Math.abs(
                intersect(leftShift[0], leftShift[1], baseSlope, baseIntercept, t1) - onsetT);
        errors.put("tangentPlacementK", onsetShift);
        sources.add("tangent placement sensitivity (window +/-2 points)");

        if (calibTempOffsetK != 0 || calibHfOffsetW != 0) {
            errors.put("calibrationTempOffsetK", calibTempOffsetK);
            errors.put("calibrationHfOffsetW", calibHfOffsetW);
            sources.add("temperature calibration offset");
        }
        if ("UNDETERMINED".equals(direction)) {
            sources.add("endo/exo direction undetermined (no convention metadata)");
        }

        return new Analysis(
                onsetT, pts.get(peakIdx).temp(), endsetT,
                leftFit[0] * onsetT + leftFit[1], pts.get(peakIdx).hfW(),
                rightFit[0] * endsetT + rightFit[1],
                areaJ, direction, errors, sources);
    }

    private static double intersect(double m1, double b1, double m2, double b2, double fallback) {
        if (Math.abs(m1 - m2) < 1e-15) return fallback;
        double x = (b2 - b1) / (m1 - m2);
        return Double.isFinite(x) ? x : fallback;
    }
}
