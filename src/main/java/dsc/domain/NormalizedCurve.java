package dsc.domain;

import java.util.ArrayList;
import java.util.List;

/**
 * Derived layer: calibration-corrected temperature, canonical endo-up heat flow
 * normalized by sample mass (W/g). Raw data stays untouched in {@link Curve}.
 *
 * Direction semantics: the sign is fixed ONLY from the archived instrument
 * convention metadata. If the convention is missing, no guess is made; the
 * derived curve carries a diagnostic and direction-dependent results stay
 * undecided (both interpretations remain listed as candidates upstream).
 */
public final class NormalizedCurve {
    public final String curveObjectId;
    public final String massObjectId;
    public final String calibrationObjectId;
    public final double[] tempC;      // calibrated
    public final double[] timeS;
    public final double[] heatFlowWg; // endo-up canonical, per gram... actually W/g
    public final List<String> diagnostics;

    public NormalizedCurve(String curveObjectId, String massObjectId, String calibrationObjectId,
                           double[] tempC, double[] timeS, double[] heatFlowWg, List<String> diagnostics) {
        this.curveObjectId = curveObjectId;
        this.massObjectId = massObjectId;
        this.calibrationObjectId = calibrationObjectId;
        this.tempC = tempC;
        this.timeS = timeS;
        this.heatFlowWg = heatFlowWg;
        this.diagnostics = diagnostics;
    }

    public static NormalizedCurve derive(Curve curve, Quantity mass, Quantity calA, Quantity calB) {
        List<String> diag = new ArrayList<>();
        int n = curve.points.size();
        double[] t = new double[n];
        double[] ts = new double[n];
        double[] hf = new double[n];

        double a = calA != null && calA.value != null ? calA.value : 1.0;
        double b = calB != null && calB.value != null ? calB.value : 0.0;
        if (calA == null || calB == null) diag.add("temperature calibration missing; identity used");

        Double massG = null;
        if (mass == null || mass.value == null) {
            diag.add("sample mass missing; normalization to W/g impossible, kept as mW");
        } else {
            Quantity g = mass.convertTo("g");
            if (g.value == null) {
                diag.add("sample mass not convertible: " + g.diagnostic);
            } else if (g.value <= 0) {
                diag.add("sample mass non-positive (" + mass.raw + " " + mass.unit + "); kept as mW");
            } else {
                massG = g.value;
            }
        }

        String convention = curve.signConvention();
        double sign;
        if (convention == null) {
            sign = 1.0;
            diag.add("heatFlowSignConvention missing from instrument archive; "
                    + "endo/exo direction UNDECIDED (not guessed from file name)");
        } else {
            // canonical analysis basis is endo-up
            sign = switch (convention) {
                case "endo_up" -> 1.0;
                case "exo_up" -> -1.0;
                default -> {
                    diag.add("unknown sign convention '" + convention + "'; direction undecided");
                    yield 1.0;
                }
            };
        }
        if (curve.isSignalReversed()) {
            sign = -sign;
            diag.add("instrument archive marks signalReversed=true; sign corrected from metadata");
        }

        for (int i = 0; i < n; i++) {
            Curve.Point p = curve.points.get(i);
            t[i] = a * p.tempC + b;
            ts[i] = p.timeS;
            double mw = sign * p.heatFlowMw;
            hf[i] = massG != null ? mw / 1000.0 / massG : mw; // W/g when mass known
        }
        return new NormalizedCurve(curve.objectId, null, null, t, ts, hf, diag);
    }
}
