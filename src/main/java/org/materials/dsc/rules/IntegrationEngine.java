package org.materials.dsc.rules;

import org.materials.dsc.domain.BaselineCandidate;
import org.materials.dsc.domain.Ids;
import org.materials.dsc.domain.IntegrationResult;
import org.materials.dsc.domain.NormalizedCurve;
import org.materials.dsc.domain.ScanDirection;
import org.materials.dsc.domain.SourceDocs;
import org.materials.dsc.domain.ThermalEvent;
import org.materials.dsc.domain.Units;

import java.util.ArrayList;
import java.util.List;

/**
 * Event integration on top of one baseline candidate.
 *
 *  - area uses the time domain trapezoid when the instrument output carries
 *    timestamps; otherwise it falls back to the temperature domain divided by
 *    the archived scan rate;
 *  - endo/exo sense is read from the signed, convention-resolved area, never
 *    from a file name;
 *  - onset / peak / endset follow standard tangent / extrema / back-tangent
 *    definitions on the baseline-corrected signal;
 *  - an empty window yields an EMPTY_INTERVAL diagnostic with no fabricated
 *    numbers;
 *  - specific enthalpy carries the mass revision it used; a later mass
 *    revision marks it stale at the dependency layer.
 */
public final class IntegrationEngine {

    /** fraction of peak height used for the onset/endset tangent crossings */
    public static final double TANGENT_LEVEL = 0.0;

    public IntegrationResult integrate(ThermalEvent event, BaselineCandidate baseline,
                                       NormalizedCurve curve,
                                       SourceDocs.SampleMass mass,
                                       SourceDocs.ScanRate rate) {
        IntegrationResult r = new IntegrationResult();
        r.id = Ids.integration(baseline.id);
        r.baselineId = baseline.id;
        r.eventId = event.id;
        r.windowStartC = event.window.startC;
        r.windowEndC = event.window.endC;

        if (event.window.isEmpty()) {
            r.status = "EMPTY_INTERVAL";
            r.diagnostics = "window width is zero (" + event.window.startC + " °C); "
                    + "no area, no characteristic temperatures";
            r.integrationDomain = "none";
            return r;
        }

        int[] idx = windowPoints(event, curve);
        r.sampleCount = idx.length;
        if (idx.length < 2) {
            r.status = "INSUFFICIENT_POINTS";
            r.diagnostics = "fewer than 2 usable points in window";
            r.integrationDomain = "none";
            return r;
        }

        double areaJ = 0;
        boolean timeDomain = true;
        for (int k = 1; k < idx.length; k++) {
            int a = idx[k - 1];
            int b = idx[k];
            double ya = curve.heatW[a] - baseline.valueAt(curve.tempC[a]);
            double yb = curve.heatW[b] - baseline.valueAt(curve.tempC[b]);
            double dt = curve.timeS[b] - curve.timeS[a];
            if (!Double.isNaN(dt) && dt > 0) {
                areaJ += 0.5 * (ya + yb) * dt;
            } else {
                timeDomain = false;
            }
        }
        if (!timeDomain) {
            areaJ = 0;
            Double rateCperMin = parseRate(rate);
            if (rateCperMin == null) {
                r.status = "NO_SCAN_RATE";
                r.diagnostics = "timestamps absent and scan rate unconvertible; area cannot be integrated";
                r.integrationDomain = "none";
            } else {
                double rateCperSec = rateCperMin / 60.0;
                for (int k = 1; k < idx.length; k++) {
                    int a = idx[k - 1];
                    int b = idx[k];
                    double ya = curve.heatW[a] - baseline.valueAt(curve.tempC[a]);
                    double yb = curve.heatW[b] - baseline.valueAt(curve.tempC[b]);
                    double dT = curve.tempC[b] - curve.tempC[a];
                    if (dT == 0) {
                        continue;
                    }
                    double dtSec = dT / rateCperSec;
                    areaJ += 0.5 * (ya + yb) * dtSec;
                }
                r.integrationDomain = "temperature";
            }
        } else {
            r.integrationDomain = "time";
        }
        r.areaJ = areaJ;
        r.thermalSense = areaJ > 1e-15 ? "endothermic"
                : areaJ < -1e-15 ? "exothermic" : "indeterminate";

        characteristics(r, idx, curve, baseline);

        if (mass != null) {
            Object[] parsed = Units.tryToBase(mass.massValue, mass.massUnit, Units.Kind.MASS);
            if (parsed[1] != null) {
                r.status = r.status.equals("ok") ? "MASS_UNCONVERTIBLE" : r.status;
                r.diagnostics = append(r.diagnostics,
                        "mass kept at source ('" + mass.massValue + " " + mass.massUnit
                                + "'); specific enthalpy not derived: "
                                + ((Units.ConversionDiagnostic) parsed[1]).message);
            } else {
                double kg = (Double) parsed[0];
                if (kg > 0 && r.integrationDomain != null && !r.integrationDomain.equals("none")) {
                    r.specificEnthalpyJg = areaJ / (kg * 1000.0);
                    r.massRevisionUsed = mass.massRevision;
                }
            }
        } else {
            r.diagnostics = append(r.diagnostics,
                    "no archived sample mass; specific enthalpy unavailable (area in J is)");
        }
        return r;
    }

    private Double parseRate(SourceDocs.ScanRate rate) {
        if (rate == null) {
            return null;
        }
        Object[] parsed = Units.tryToBase(rate.rateValue, rate.rateUnit, Units.Kind.RATE);
        return parsed[1] == null ? (Double) parsed[0] : null;
    }

    private int[] windowPoints(ThermalEvent event, NormalizedCurve curve) {
        List<Integer> out = new ArrayList<>();
        ScanDirection d = curve.observedDirection;
        for (int i = 0; i < curve.tempC.length; i++) {
            if (curve.usable[i] && event.window.contains(curve.tempC[i])) {
                out.add(i);
            }
        }
        // cooling segments are integrated in their acquired (descending) order;
        // heating and cooling are never mixed, but within one segment order
        // follows acquisition, not ascending temperature.
        if (d == ScanDirection.HEATING) {
            out.sort((a, b) -> Double.compare(curve.tempC[a], curve.tempC[b]));
        } else if (d == ScanDirection.COOLING) {
            out.sort((a, b) -> Double.compare(curve.tempC[b], curve.tempC[a]));
        }
        return out.stream().mapToInt(Integer::intValue).toArray();
    }

    private void characteristics(IntegrationResult r, int[] idx, NormalizedCurve curve,
                                 BaselineCandidate baseline) {
        double[] y = new double[idx.length];
        double[] t = new double[idx.length];
        int peakPos = 0;
        int troughPos = 0;
        double maxV = Double.NEGATIVE_INFINITY;
        double minV = Double.POSITIVE_INFINITY;
        for (int k = 0; k < idx.length; k++) {
            int i = idx[k];
            t[k] = curve.tempC[i];
            y[k] = curve.heatW[i] - baseline.valueAt(t[k]);
            if (y[k] > maxV) {
                maxV = y[k];
                peakPos = k;
            }
            if (y[k] < minV) {
                minV = y[k];
                troughPos = k;
            }
        }
        boolean endo = r.thermalSense.equals("endothermic");
        int ext = endo ? peakPos : troughPos;
        double amp = endo ? maxV : -minV;
        r.peakC = t[ext];
        r.peakHeightW = y[ext];

        // onset = intersection of pre-event baseline tangent and tangent at the
        // steepest corrected-signal slope on the leading flank; endset likewise
        // on the trailing flank.
        r.onsetC = tangentIntersection(t, y, ext, true, endo);
        r.endsetC = tangentIntersection(t, y, ext, false, endo);
        if (Double.isNaN(r.onsetC)) {
            r.onsetC = t[0];
        }
        if (Double.isNaN(r.endsetC)) {
            r.endsetC = t[t.length - 1];
        }
    }

    private double tangentIntersection(double[] t, double[] y, int ext, boolean leading, boolean endo) {
        int slopePos = steepest(t, y, ext, leading, endo);
        if (slopePos < 0) {
            return Double.NaN;
        }
        double m = slopeAt(t, y, slopePos);
        double b = y[slopePos] - m * t[slopePos];
        // corrected signal baseline level ≈ 0 (signal already baseline-subtracted)
        if (m == 0) {
            return Double.NaN;
        }
        double crossing = -b / m;
        int lo = leading ? 0 : ext;
        int hi = leading ? ext : t.length - 1;
        double tlo = Math.min(t[lo], t[hi]);
        double thi = Math.max(t[lo], t[hi]);
        if (crossing < tlo || crossing > thi) {
            return leading ? t[0] : t[t.length - 1];
        }
        return crossing;
    }

    private int steepest(double[] t, double[] y, int ext, boolean leading, boolean endo) {
        int from = leading ? 1 : ext + 1;
        int to = leading ? ext : t.length - 1;
        int best = -1;
        double bestV = 0;
        for (int k = from; k <= to && k < t.length; k++) {
            double dt = t[k] - t[k - 1];
            if (dt == 0) {
                continue;
            }
            double s = (y[k] - y[k - 1]) / dt;
            double v = endo ? s : -s;
            if (v > bestV) {
                bestV = v;
                best = k;
            }
        }
        return best;
    }

    private double slopeAt(double[] t, double[] y, int k) {
        double dt = t[k] - t[k - 1];
        return dt == 0 ? 0 : (y[k] - y[k - 1]) / dt;
    }

    private String append(String a, String b) {
        return a == null || a.isEmpty() ? b : a + "; " + b;
    }
}
