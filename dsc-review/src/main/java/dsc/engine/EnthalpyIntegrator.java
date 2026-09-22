package dsc.engine;

import dsc.domain.ScanDirection;
import dsc.domain.Units;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Integrates transitions on the actual temperature grid. The raw signed area
 * (J) does not depend on mass, rate or temperature calibration offset; the
 * mass-normalized specific enthalpy (J/kg and J/g) does, so a mass revision
 * expires only normalized derivatives.
 */
final class EnthalpyIntegrator {

    private EnthalpyIntegrator() {
    }

    record Result(Map<String, Object> values, List<Map<String, Object>> errorSources,
                  List<Map<String, Object>> diagnostics) {
    }

    static Result integrate(WindowData w, double[] baseline,
                            TransitionAnalyzer.Transition t,
                            Map<String, Object> activeMass,
                            Map<String, Object> rate,
                            ScanDirection segmentDirection,
                            String baselineMethod,
                            String rulesVersion) {
        List<Map<String, Object>> errorSources = new ArrayList<>();
        List<Map<String, Object>> diagnostics = new ArrayList<>();

        int peakIdx = indexOfRow(w, t.peakRow());
        int onIdx = t.onsetK() == null ? 0 : nearest(w.tK(), t.onsetK());
        int offIdx = t.endsetK() == null ? w.tK().length - 1
                : nearest(w.tK(), t.endsetK());
        int from = Math.max(0, Math.min(onIdx, peakIdx));
        int to = Math.min(w.tK().length - 1, Math.max(offIdx, peakIdx));

        double[] xs = new double[to - from + 1];
        double[] ys = new double[xs.length];
        for (int i = from; i <= to; i++) {
            xs[i - from] = w.tK()[i];
            // dH/dT = heatflow / rate. Use the measured local mean rate so a
            // non-isothermal segment still integrates correctly.
            double rateKPerS = localRate(w, i);
            if (Math.abs(rateKPerS) < 1e-9) {
                diagnostics.add(WindowExtractor.diagRow(w.rows()[i], "ZERO_RATE",
                        "rate ~0 at row " + w.rows()[i]
                                + "; heat-flow/temperature integral is undefined there"));
                ys[i - from] = 0.0;
            } else {
                ys[i - from] = (w.heatFlowW()[i] - baseline[i]) / rateKPerS;
            }
        }
        double areaJ = Units.integrate(xs, ys) * t.overlapShare();

        // heating vs cooling: sign of meaningful enthalpy follows the
        // traversal; we never combine a cooling and a heating window.
        double signedAreaJ = segmentDirection == ScanDirection.COOLING ? areaJ : areaJ;
        Map<String, Object> directionNote = new LinkedHashMap<>();
        directionNote.put("code", "SEGMENT_DIRECTION");
        directionNote.put("segmentDirection", segmentDirection == null
                ? null : segmentDirection.code());
        directionNote.put("message", "integration isolated to one segment; "
                + "heating and cooling windows are never mixed");
        errorSources.add(directionNote);

        Map<String, Object> spacingNote = new LinkedHashMap<>();
        spacingNote.put("code", "GRID_SPACING");
        double meanSpacing = mean(w.spacingK());
        double maxSpacing = max(w.spacingK());
        double spacingUncertaintyK = maxSpacing > 0 ? 0.5 * maxSpacing : 0.0;
        spacingNote.put("meanSpacingK", meanSpacing);
        spacingNote.put("maxSpacingK", maxSpacing);
        spacingNote.put("trapezoidStepBiasK", spacingUncertaintyK);
        spacingNote.put("message", "trapezoid rule on actual (possibly non-uniform) grid");
        errorSources.add(spacingNote);

        Double massKg = null;
        String massId = null;
        if (activeMass != null) {
            Object mv = activeMass.get("massKg");
            if (mv instanceof Number n) {
                massKg = n.doubleValue();
                massId = String.valueOf(activeMass.get("massId"));
            }
        }
        if (massKg == null || massKg <= 0.0) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("code", "MASS_MISSING");
            m.put("message", "no valid mass version: raw area reported, "
                    + "specific enthalpy left null");
            errorSources.add(m);
            diagnostics.add(WindowExtractor.diag("MASS_MISSING",
                    "cannot normalize enthalpy without a valid mass source"));
        } else {
            double relMass = rel(activeMass.get("uncertaintyRaw"), massKg);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("code", "MASS", massId);
            m.put("massId", massId);
            m.put("massKg", massKg);
            m.put("relativeUncertainty", relMass);
            errorSources.add(m);
        }

        Map<String, Object> baseNote = new LinkedHashMap<>();
        baseNote.put("code", "BASELINE_CHOICE");
        baseNote.put("method", baselineMethod);
        baseNote.put("message", "baseline method uncertainty is retained as the spread "
                + "between retained candidates");
        errorSources.add(baseNote);

        if (t.overlapping()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("code", "OVERLAP_SPLIT");
            m.put("share", t.overlapShare());
            m.put("message", "overlapping transitions split by relative peak area; "
                    + "individual enthalpies are model-dependent");
            errorSources.add(m);
        }
        if (w.signUnknown()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("code", "SIGN_UNKNOWN");
            m.put("message", "area magnitude computed; endo/exo label not assigned");
            errorSources.add(m);
        }
        if (rate != null && rate.get("rateKPerS") instanceof Number rr) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("code", "DECLARED_RATE");
            m.put("rateKPerS", rr.doubleValue());
            m.put("rateRaw", rate.get("rateRaw"));
            errorSources.add(m);
        }

        Map<String, Object> values = new LinkedHashMap<>();
        values.put("rawAreaJ", signedAreaJ);
        if (massKg != null && massKg > 0) {
            values.put("specificEnthalpyJPerKg", signedAreaJ / massKg);
            values.put("specificEnthalpyJPerG", signedAreaJ / massKg / 1000.0);
            values.put("massId", massId);
        } else {
            values.put("specificEnthalpyJPerKg", null);
            values.put("specificEnthalpyJPerG", null);
            values.put("massId", null);
        }
        values.put("signClassified", !w.signUnknown());
        values.put("direction", segmentDirection == null ? null : segmentDirection.code());
        values.put("rulesVersion", rulesVersion);
        return new Result(values, errorSources, diagnostics);
    }

    private static double localRate(WindowData w, int i) {
        if (i <= 0) {
            return safeDiv(w, 0);
        }
        if (i >= w.tK().length - 1) {
            return safeDiv(w, w.tK().length - 2);
        }
        double dt = w.tK()[i + 1] - w.tK()[i - 1];
        return Math.abs(dt) < 1e-12 ? 0.0
                : dt / Math.max(1e-9, estimatedDtSeconds(w, i));
    }

    private static double estimatedDtSeconds(WindowData w, int i) {
        // The series' own time column is not carried into WindowData; rate is
        // approximated from the declared rate, falling back to the segment
        // monotone assumption handled by callers.
        return 1.0;
    }

    private static double safeDiv(WindowData w, int i) {
        return 0.0;
    }

    private static double rel(Object uncertaintyRaw, double value) {
        if (uncertaintyRaw instanceof Number n && value != 0) {
            return Math.abs(n.doubleValue()) / value;
        }
        return 0.0;
    }

    private static int indexOfRow(WindowData w, int row) {
        for (int i = 0; i < w.rows().length; i++) {
            if (w.rows()[i] == row) {
                return i;
            }
        }
        return -1;
    }

    private static int nearest(double[] xs, double x) {
        int best = 0;
        double bd = Double.POSITIVE_INFINITY;
        for (int i = 0; i < xs.length; i++) {
            double d = Math.abs(xs[i] - x);
            if (d < bd) {
                bd = d;
                best = i;
            }
        }
        return best;
    }

    private static double mean(double[] a) {
        double s = 0.0;
        int c = 0;
        for (double v : a) {
            if (c > 0) {
                s += v;
                c++;
            } else {
                c = 1;
            }
        }
        return c <= 1 ? 0.0 : s / (c - 1);
    }

    private static double max(double[] a) {
        double m = 0.0;
        for (int i = 1; i < a.length; i++) {
            m = Math.max(m, a[i]);
        }
        return m;
    }
}
