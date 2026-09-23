package dsc.engine;

import dsc.model.CalibrationVersion;
import dsc.model.Diagnostic;
import dsc.model.DiagnosticLevel;
import dsc.model.NormalizedPoint;
import dsc.model.Quantity;
import dsc.model.RawPoint;
import dsc.model.RawSequence;
import dsc.model.SegmentDirection;
import dsc.model.SegmentLeg;
import dsc.model.SignConvention;
import dsc.model.TraceView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Builds the normalized {@link TraceView} from a raw import and a calibration
 * version: applies calibration, normalizes heat flow to the endo-up basis,
 * derives direction from temperature monotonicity and splits cyclic traces
 * into monotone legs. Raw rows stay untouched; unparseable rows are excluded
 * from derivation but the exclusion is reported.
 */
public final class TraceNormalizer {

    private TraceNormalizer() {}

    public static TraceView normalize(RawSequence raw, CalibrationVersion cal) {
        List<Diagnostic> diags = new ArrayList<>(raw.diagnostics());
        List<NormalizedPoint> pts = new ArrayList<>();
        int dropped = 0;
        for (RawPoint p : raw.points()) {
            if (p.tC() == null || p.hfMw() == null) { dropped++; continue; }
            double timeS;
            try { timeS = p.timeS().isBlank() ? Double.NaN : Double.parseDouble(p.timeS()); }
            catch (NumberFormatException e) { timeS = Double.NaN; }
            double tc = cal.apply(p.tC());
            double hf = raw.signConvention() == SignConvention.ENDO_DOWN ? -p.hfMw() : p.hfMw();
            pts.add(new NormalizedPoint(p.index(), timeS, tc, hf));
        }
        if (dropped > 0) {
            diags.add(Diagnostic.warn("ROWS_EXCLUDED_FROM_DERIVATION",
                    dropped + " row(s) excluded from derivation because raw values could not be converted"));
        }
        if (raw.signConvention() == SignConvention.ENDO_DOWN) {
            diags.add(Diagnostic.info("SIGN_NORMALIZED",
                    "heat flow normalized to endo-up basis (instrument declared endo-down); raw trace unchanged"));
        }
        checkSpacing(pts, diags);

        List<SegmentLeg> legs = splitLegs(raw.sequenceId(), pts);
        SegmentDirection overall = overallDirection(legs);
        String declared = raw.declaredDirection() == null ? "" : raw.declaredDirection().toLowerCase();
        if (!declared.isBlank()) {
            SegmentDirection decDir = declared.startsWith("cool") ? SegmentDirection.COOLING
                    : declared.startsWith("heat") ? SegmentDirection.HEATING
                    : SegmentDirection.UNKNOWN;
            if (decDir != SegmentDirection.UNKNOWN && legs.size() == 1 && legs.get(0).direction() != decDir) {
                diags.add(new Diagnostic("DECLARED_DIRECTION_MISMATCH", DiagnosticLevel.WARNING,
                        "metadata declares " + decDir + " but data monotonicity is " + legs.get(0).direction(),
                        "analysis direction is data-derived; metadata declaration retained on the source"));
            }
        }
        return new TraceView(raw.sourceId(), raw.sequenceId(), overall, raw.signConvention(),
                pts, legs, diags, cal.calibrationId());
    }

    private static void checkSpacing(List<NormalizedPoint> pts, List<Diagnostic> diags) {
        if (pts.size() < 4) return;
        List<Double> gaps = new ArrayList<>();
        for (int i = 1; i < pts.size(); i++) gaps.add(Math.abs(pts.get(i).tC() - pts.get(i - 1).tC()));
        List<Double> sorted = new ArrayList<>(gaps);
        Collections.sort(sorted);
        double median = sorted.get(sorted.size() / 2);
        double max = sorted.get(sorted.size() - 1);
        long repeats = gaps.stream().filter(g -> g < median * 1e-6).count();
        if (median > 0 && max > 3.0 * median) {
            diags.add(new Diagnostic("NONUNIFORM_TEMPERATURE_SPACING", DiagnosticLevel.INFO,
                    "non-uniform temperature sampling (median gap " + Quantity.trim(median)
                            + " degC, max " + Quantity.trim(max) + " degC); trapezoid integration used", null));
        }
        if (repeats > 0) {
            diags.add(new Diagnostic("REPEATED_TEMPERATURE", DiagnosticLevel.WARNING,
                    repeats + " zero-width temperature step(s) found; those steps contribute no area", null));
        }
    }

    /**
     * Splits a trace into maximal monotone legs using run-length of temperature
     * differences (runs shorter than minRun are treated as noise). The
     * turnaround index is shared by the two adjacent legs.
     */
    static List<SegmentLeg> splitLegs(String sequenceId, List<NormalizedPoint> pts) {
        List<SegmentLeg> legs = new ArrayList<>();
        int n = pts.size();
        if (n < 2) return legs;
        final int minRun = 2;
        int start = 0;
        Integer dir = null;
        int run = 0;
        for (int i = 1; i <= n; i++) {
            Integer s = i < n ? sign(pts.get(i).tC() - pts.get(i - 1).tC()) : null;
            if (s != null && s != 0 && (dir == null || s.equals(dir))) {
                if (dir == null) { dir = s; run = 1; }
                run++;
            } else {
                if (dir != null && run >= minRun) {
                    int end = i - 1;
                    legs.add(makeLeg(sequenceId, legs.size(), start, end, pts, dir));
                    start = end;
                }
                dir = (s != null && s != 0) ? s : null;
                run = 1;
            }
        }
        if (legs.isEmpty()) {
            legs.add(makeLeg(sequenceId, 0, 0, n - 1, pts, sign(pts.get(n - 1).tC() - pts.get(0).tC())));
        }
        return legs;
    }

    private static int sign(double d) {
        if (d > 1e-12) return 1;
        if (d < -1e-12) return -1;
        return 0;
    }

    private static SegmentLeg makeLeg(String seq, int ord, int from, int to,
                                      List<NormalizedPoint> pts, int dirSign) {
        SegmentDirection d = dirSign >= 0 ? SegmentDirection.HEATING : SegmentDirection.COOLING;
        return new SegmentLeg(seq + "#leg" + (ord + 1), seq, d, from, to,
                pts.get(from).tC(), pts.get(to).tC());
    }

    private static SegmentDirection overallDirection(List<SegmentLeg> legs) {
        if (legs.isEmpty()) return SegmentDirection.UNKNOWN;
        if (legs.size() == 1) return legs.get(0).direction();
        return SegmentDirection.MIXED;
    }
}
