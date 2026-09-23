package dsc.rules;

import dsc.model.BaselineCandidate;
import dsc.model.SamplePoint;

import java.util.List;
import java.util.Locale;

/**
 * Rule INT-NONUNIFORM-1: ∫ (HF − baseline) dT by the trapezoidal rule over
 * the measured grid. Because integration is per-segment trapezoids it is
 * exact for the piecewise-linear instrument signal and a linear baseline on
 * arbitrarily non-uniform temperature spacing; no rebinning is performed.
 *
 * <p>The raw integral in K·W = J is signed in instrument polarity. Rule
 * DH-SIGN-1 then normalises the sign so that endothermic (melting, glass)
 * is positive and exothermic (crystallisation) is negative on both heating
 * and cooling legs.
 */
public final class Integrator {

    private Integrator() {}

    public record RawResult(double energyJ, int pointsUsed, boolean empty) {}

    public static RawResult integrate(List<SamplePoint> points, BaselineCandidate bl,
                                      boolean reversePolarity) {
        double acc = 0;
        int used = 0;
        double prevT = Double.NaN;
        double prevSignal = Double.NaN;
        double pol = reversePolarity ? -1 : 1;
        Double meet = readMeet(bl);

        for (SamplePoint p : points) {
            if (!p.parseable()) continue;
            double t = p.tK();
            if (t < bl.leftTK() - 1e-9 || t > bl.rightTK() + 1e-9) continue;
            double base = baselinePiecewise(bl, t, meet);
            double signal = pol * p.hfW() - base;
            if (used > 0) {
                acc += 0.5 * (prevSignal + signal) * (t - prevT);
            }
            prevT = t;
            prevSignal = signal;
            used++;
        }
        return new RawResult(acc, used, used < 2);
    }

    /** Baseline value at T: piecewise pre/post tangent at the meeting point. */
    public static double baselinePiecewise(BaselineCandidate bl, double tK, Double meetTK) {
        if (bl.method() == BaselineCandidate.Method.TWO_TANGENT
                && meetTK != null) {
            double[] pp = readTangents(bl);
            double preSlope = pp[0], preInt = pp[1], postSlope = pp[2], postInt = pp[3];
            return tK < meetTK ? preSlope * tK + preInt : postSlope * tK + postInt;
        }
        if (bl.method() == BaselineCandidate.Method.STEP && meetTK != null) {
            double[] pp = readTangents(bl);
            double preSlope = pp[0], preInt = pp[1], postSlope = pp[2], postInt = pp[3];
            return tK < meetTK ? preSlope * tK + preInt : postSlope * tK + postInt;
        }
        return bl.slopeWPerK() * tK + bl.interceptW();
    }

    private static Double readMeet(BaselineCandidate bl) {
        for (String d : bl.diagnostics()) {
            if (d.startsWith("{") && d.contains("meetT_K")) {
                return extractDouble(d, "meetT_K");
            }
            if (d.startsWith("{") && d.contains("midT_K")) {
                return extractDouble(d, "midT_K");
            }
        }
        return null;
    }

    private static double[] readTangents(BaselineCandidate bl) {
        for (String d : bl.diagnostics()) {
            if (d.startsWith("{") && d.contains("preSlope")) {
                return new double[] {
                        extractDouble(d, "preSlope"), extractDouble(d, "preIntercept"),
                        extractDouble(d, "postSlope"), extractDouble(d, "postIntercept")
                };
            }
        }
        return new double[] { bl.slopeWPerK(), bl.interceptW(), bl.slopeWPerK(),
                bl.interceptW() };
    }

    private static double extractDouble(String jsonLine, String key) {
        String needle = "\"" + key + "\":";
        int i = jsonLine.indexOf(needle);
        if (i < 0) {
            if (key.equals("preIntercept_missing")) return 0;
            return Double.NaN;
        }
        int j = i + needle.length();
        int k = j;
        while (k < jsonLine.length() && "0123456789+-.eE".indexOf(jsonLine.charAt(k)) >= 0) k++;
        return Double.parseDouble(jsonLine.substring(j, k));
    }

    /** Diagnostic string emitted with a two-tangent candidate (helper for build). */
    public static String tangentDiag(String rule, double preSlope, double preIntercept,
                                     double postSlope, double postIntercept, double meetTK) {
        return String.format(Locale.ROOT,
                "{\"rule\":\"%s\",\"preSlope\":%.10g,\"preIntercept\":%.10g,"
                        + "\"postSlope\":%.10g,\"postIntercept\":%.10g,\"meetT_K\":%.6f}",
                rule, preSlope, preIntercept, postSlope, postIntercept, meetTK);
    }
}
