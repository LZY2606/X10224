package io.dsc.engine;

import io.dsc.model.BaselineCandidate;
import io.dsc.model.Derivation;
import io.dsc.model.DiagnosticCodes;
import io.dsc.model.ErrorContribution;
import io.dsc.model.Interval;
import io.dsc.model.ScanDirection;
import io.dsc.model.Segment;
import io.dsc.model.SignConvention;
import io.dsc.model.Unit;

import java.util.ArrayList;
import java.util.List;

/**
 * Characterises a thermal event against one adopted baseline.
 *
 * Sign rules (never inferred from a file name):
 *  - residual r = measuredHeatFlow - baseline
 *  - endothermic / exothermic is resolved from the instrument sign convention
 *    together with the segment direction; heating and cooling are kept apart.
 *
 * Integration uses time-based trapezoids so non-uniform temperature grids are
 * integrated correctly; the grid irregularity is reported in the error budget
 * instead of being hidden.
 */
public final class TransitionEngine {

    public static class Computed {
        public double onsetK, peakK, endsetK;
        public double enthalpyJ;
        public Double deltaCpJPerGK;
        public String thermalCharacter; // endothermic / exothermic
        public final List<ErrorContribution> errors = new ArrayList<>();
        public final List<String> diagnosticCodes = new ArrayList<>();
        public double temperatureUncertaintyK;
        public double enthalpyUncertaintyJ;
    }

    public Computed compute(String transitionType, Interval interval, Segment segment,
                            BaselineCandidate baseline, boolean nonuniformGrid,
                            double calibrationUncertaintyK, double rateUncertaintyFraction,
                            double massUncertaintyFraction, Double massKg) {
        Computed out = new Computed();
        List<Segment.Sample> pts = new Window(interval.fromK(), interval.toK()).select(segment);
        int n = pts.size();
        double[] t = new double[n];
        double[] s = new double[n];
        double[] r = new double[n];
        for (int i = 0; i < n; i++) {
            Segment.Sample p = pts.get(i);
            t[i] = p.tK();
            s[i] = p.s();
            r[i] = p.hfW() - baseline.valueAt(p.tK());
        }

        boolean heating = segment.direction() == ScanDirection.HEATING;
        boolean endoPeak = resolveEndothermicPeak(segment.signConvention(), heating);
        double signedExtreme = endoPeak ? min(r) : max(r);
        int peakIndex = indexOf(r, signedExtreme);
        out.peakK = t[peakIndex];

        double threshold = 0.05 * Math.abs(signedExtreme);
        out.onsetK = crossingBefore(t, r, peakIndex, signedExtreme, threshold, heating);
        out.endsetK = crossingAfter(t, r, peakIndex, signedExtreme, threshold, heating);

        // integrated residual over time, sign of the resolved event
        double integral = 0;
        for (int i = 1; i < n; i++) {
            integral += 0.5 * (r[i] + r[i - 1]) * (s[i] - s[i - 1]);
        }
        double eventSign = (endoPeak ? (integral < 0 ? -1 : 1) : (integral > 0 ? -1 : 1));
        out.enthalpyJ = eventSign * Math.abs(integral);
        out.thermalCharacter = endoPeak ? "endothermic" : "exothermic";

        if ("glass".equals(transitionType)) {
            double pre = armSlopeWPerK(pts, baseline, true);
            double post = armSlopeWPerK(pts, baseline, false);
            // convert W/K of the whole sample to J/(g*K) needs mass; supply 0 when mass missing
            double massG = massKg == null ? Double.NaN : massKg * 1000.0;
            out.deltaCpJPerGK = Double.isNaN(massG) ? null : (post - pre) / massG;
            out.thermalCharacter = "glass-step";
        }

        // --- error budget -------------------------------------------------
        double baselineRms = baseline.rmsResidual();
        double spanS = s[n - 1] - s[0];
        double esBaseline = baselineRms * spanS;
        double esResolution = resolutionEnergy(t, s, r);
        double esGrid = nonuniformGrid ? 0.01 * Math.abs(out.enthalpyJ) : 0.0;
        double esRate = rateUncertaintyFraction * Math.abs(out.enthalpyJ);
        double esMass = massKg == null ? 0.0 : massUncertaintyFraction * Math.abs(out.enthalpyJ);

        double totalE = Math.sqrt(esBaseline * esBaseline + esResolution * esResolution
                + esGrid * esGrid + esRate * esRate + esMass * esMass);
        add(out.errors, "ES-BASELINE", "baseline candidate spread / fit residual",
                esBaseline, "J", totalE);
        add(out.errors, "ES-MASS", "sample mass revision uncertainty", esMass, "J", totalE);
        add(out.errors, "ES-RATE", "programmed vs logged scan rate", esRate, "J", totalE);
        add(out.errors, "ES-GRID", "non-uniform temperature grid", esGrid, "J", totalE);
        add(out.errors, "ES-RESOLUTION", "sampling interval resolution", esResolution, "J", totalE);
        out.enthalpyUncertaintyJ = totalE;
        out.temperatureUncertaintyK = calibrationUncertaintyK;
        addTemp(out.errors, "ES-CAL", "temperature calibration uncertainty",
                calibrationUncertaintyK, "K");

        if (nonuniformGrid) out.diagnosticCodes.add(DiagnosticCodes.NONUNIFORM_TEMPERATURE_GRID);
        return out;
    }

    private boolean resolveEndothermicPeak(SignConvention convention, boolean heating) {
        // EXO_UP + heating: downward deviation (negative residual) = endotherm (melt).
        // EXO_DOWN reverses the plotted axis. Cooling crystallisation is exothermic.
        return switch (convention) {
            case EXO_UP -> heating;
            case EXO_DOWN -> !heating;
        };
    }

    private double armSlopeWPerK(List<Segment.Sample> pts, BaselineCandidate b, boolean pre) {
        int n = pts.size();
        int from = pre ? 0 : (3 * n) / 4;
        int to = pre ? n / 4 : n;
        double[] xs = new double[to - from];
        double[] ys = new double[to - from];
        for (int i = from; i < to; i++) {
            xs[i - from] = pts.get(i).tK();
            ys[i - from] = pts.get(i).hfW() - b.valueAt(pts.get(i).tK());
        }
        return LinearFit.of(xs, ys).slope();
    }

    private double resolutionEnergy(double[] t, double[] s, double[] r) {
        double maxStep = 0;
        for (int i = 1; i < s.length; i++) maxStep = Math.max(maxStep, s[i] - s[i - 1]);
        double amp = 0;
        for (double v : r) amp = Math.max(amp, Math.abs(v));
        return amp * maxStep * 0.5;
    }

    private double crossingBefore(double[] t, double[] r, int peak, double extreme,
                                  double threshold, boolean heating) {
        for (int i = peak; i > 0; i--) {
            if (Math.abs(r[i - 1]) <= threshold) return t[i];
        }
        return t[0];
    }

    private double crossingAfter(double[] t, double[] r, int peak, double extreme,
                                 double threshold, boolean heating) {
        for (int i = peak; i < r.length - 1; i++) {
            if (Math.abs(r[i + 1]) <= threshold) return t[i];
        }
        return t[t.length - 1];
    }

    private int indexOf(double[] v, double target) {
        for (int i = 0; i < v.length; i++) if (Double.compare(v[i], target) == 0) return i;
        return 0;
    }

    private double min(double[] v) {
        double m = Double.POSITIVE_INFINITY;
        for (double d : v) m = Math.min(m, d);
        return m;
    }

    private double max(double[] v) {
        double m = Double.NEGATIVE_INFINITY;
        for (double d : v) m = Math.max(m, d);
        return m;
    }

    private void add(List<ErrorContribution> errs, String code, String label,
                     double magnitude, String unit, double total) {
        errs.add(new ErrorContribution(code, label, magnitude, unit,
                total == 0 ? 0 : magnitude / total));
    }

    private void addTemp(List<ErrorContribution> errs, String code, String label,
                         double magnitude, String unit) {
        errs.add(new ErrorContribution(code, label, magnitude, unit, 1.0));
    }
}
