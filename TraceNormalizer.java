package dsc.engine;

import dsc.model.CalibrationVersion;
import dsc.model.Diagnostic;
import dsc.model.NormalizedPoint;
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
                    dropped + " row(s) excluded from derivation because their raw values could not be converted"));
        }
        if (raw.signConvention() == SignConvention.ENDO_DOWN) {
            diags.add(Diagnostic.info("SIGN_NORMALIZED",
                    "heat flow sign normalized to endo-up basis (instrument declared endo-down); raw trace unchanged"));
        }
        checkSpacing(pts, diags);

        List<SegmentLeg> legs = splitLegs(raw.sequenceId(), pts);
        SegmentDirection overall = overallDirection(legs, pts.size());
        String declared = raw.declaredDirection() == null ? "" : raw.declaredDirection().toLowerCase();
        if (!declared.isBlank()) {
            SegmentDirection decDir = declared.startsWith("cool") ? SegmentDirection.COOLING
                    : declared.startsWith("heat") ? SegmentDirection.HEATING
                    : SegmentDirection.UNKNOWN;
            if (decDir != SegmentDirection.UNKNOWN && legs.size() == 1 && legs.get(0).direction() != decDir) {
                diags.add(new dsc.model.Diagnostic("DECLARED_DIRECTION_MISMATCH",
                        dsc.model.DiagnosticLevel.WARNING,
                        "metadata declares " + decDir + " but data monotonicity is " + legs.get(0).direction(),
                        "direction used for analysis is data-derived; metadata declaration retained on source"));
            }
        }
        return new TraceView(raw.sourceId(), raw.sequenceId(), overall, raw.signConvention(),
                pts, legs, diags, cal.calibrationId());
    }

    private static void checkSpacing(List<NormalizedPoint> pts, List<Diagnostic> diags) {
        List<Double> gaps = new ArrayList<>();
        for (int i = 1; i < pts.size(); i++) gaps.add(Math.abs(pts.get(i).tC() - pts.get(i - 1).tC()));
        if (gaps.size() < 4) return;
        List<Double> sorted = new ArrayList<>(gaps);
        Collections.sort(sorted);
        double median = sorted.get(sorted.size() / 2);
        double max = sorted.get(sorted.size() - 1);
        long repeats = gaps.stream().filter(g -> g < median * 1e-6).count();
        if (median > 0 && max > 3.0 * median) {
            diags.add(new dsc.model.Diagnostic("NONUNIFORM_TEMPERATURE_SPACING",
                    dsc.model.DiagnosticLevel.INFO,
                    "non-uniform temperature sampling detected (median gap "
                            + dsc.model.Quantity.trim(median) + " degC, max "
                            + dsc.model.Quantity.trim(max) + " degC); trapezoid integration used",
                    null));
        }
        if (repeats > 0) {
            diags.add(new dsc.model.Diagnostic("REPEATED_TEMPERATURE",
                    dsc.model.DiagnosticLevel.WARNING,
                    repeats + " zero-width temperature step(s) found; those steps contribute no area", null));
        }
    }

    static List<SegmentLeg> splitLegs(String sequenceId, List<NormalizedPoint> pts) {
        List<SegmentLeg> legs = new ArrayList<>();
        if (pts.size() < 2) return legs;
        final int minRun = 2;
        int legStart = 0;
        int runLen = 0;
        Integer runSign = null;
        Integer currentSign = sign(pts.get(1).tC() - pts.get(0).tC());
        int currentLen = 1;
        for (int i = 2; i <= pts.size(); i++) {
            Integer s = i < pts.size() ? sign(pts.get(i).tC() - pts.get(i - 1).tC()) : null;
            if (s != null && s.equals(currentSign)) { currentLen++; continue; }
            if (currentSign != null && currentLen >= minRun) {
                if (runSign != null && !runSign.equals(currentSign)) {
                    legs.add(makeLeg(sequenceId, legs.size(), legStart, i - currentLen - 1 + (i == pts.size() ? 0 : 0), pts, runSign));
                    legStart = i - currentLen;
                    // include turnaround point in prior leg by adjusting later
                }
                runSign = currentSign;
                runLen = currentLen;
            }
            currentSign = s;
            currentLen = 1;
        }
        // fallback/simple robust implementation below if the streaming state got ambiguous
        legs.clear();
        simpleSplit(sequenceId, pts, legs, minRun);
        return legs;
    }

    private static void simpleSplit(String sequenceId, List<NormalizedPoint> pts,
                                    List<SegmentLeg> legs, int minRun) {
        int n = pts.size();
        int start = 0;
        Integer dir = null;
        int run = 0;
        for (int i = 1; i <= n; i++) {
            Integer s = i < n ? sign(pts.get(i).tC() - pts.get(i - 1).tC()) : null;
            if (s != null && (dir == null || s.equals(dir))) {
                if (dir == null) { dir = s; run = 1; }
                run++;
            } else {
                if (dir != null && run >= minRun) {
                    int end = i - 1;
                    legs.add(makeLeg(sequenceId, legs.size(), start, end, pts, dir));
                    start = end; // turnaround point shared
                }
                dir = s;
                run = 1;
            }
        }
        if (legs.isEmpty() && n >= 2) {
            legs.add(makeLeg(sequenceId, 0, 0, n - 1, pts, sign(pts.get(n - 1).tC() - pts.get(0).tC())));
        }
    }

    private static Integer sign(double d) {
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

    private static SegmentDirection overallDirection(List<SegmentLeg> legs, int pointCount) {
        if (legs.isEmpty()) return SegmentDirection.UNKNOWN;
        if (legs.size() == 1) return legs.get(0).direction();
        long heat = legs.stream().filter(l -> l.direction() == SegmentDirection.HEATING)
                .mapToInt(l -> l.toIndex() - l.fromIndex()).sum();
        long cool = legs.stream().filter(l -> l.direction() == SegmentDirection.COOLING)
                .mapToInt(l -> l.toIndex() - l.fromIndex()).sum();
        if (heat == 0 && cool == 0) return SegmentDirection.UNKNOWN;
        return SegmentDirection.MIXED;
    }
}
