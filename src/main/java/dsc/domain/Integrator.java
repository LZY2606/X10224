package dsc.domain;

import java.util.ArrayList;
import java.util.List;

/**
 * Event integration in the temperature coordinate. Works with non-uniform
 * temperature spacing (trapezoids on actual sample temperatures). Enthalpy
 * is normalized by the *current* sample-mass version; a mass revision must
 * mark every normalized derivation stale. Empty intervals produce a
 * diagnostic, never a fabricated number.
 */
public final class Integrator {

    public static final class Result {
        public Double onsetC, peakC, endsetC;   // nullable when undecidable
        public Double enthalpyJg;               // normalized, null when empty/unnormalizable
        public final List<Diagnostic> diagnostics = new ArrayList<>();
        public final List<String> errorSources = new ArrayList<>();
        public int pointCount;
    }

    /**
     * @param seg            segment the event belongs to (heating and cooling never mixed)
     * @param baseline       chosen or candidate baseline
     * @param massMg         current sample mass version value
     * @param rateKPerMin    scan rate
     * @param exoConvention  instrument metadata; null -> direction undecidable, do not guess
     */
    public static Result integrate(Curve.Segment seg, Baseline baseline,
                                   double massMg, double rateKPerMin, String exoConvention) {
        Result r = new Result();
        List<Curve.Pt> in = new ArrayList<>();
        for (Curve.Pt p : seg.points)
            if (p.tempC >= baseline.tStart && p.tempC <= baseline.tEnd) in.add(p);
        r.pointCount = in.size();
        if (in.isEmpty()) {
            r.diagnostics.add(new Diagnostic("EMPTY_INTERVAL", "WARN",
                    "interval [" + baseline.tStart + ", " + baseline.tEnd
                            + "] degC contains no samples; no enthalpy computed", baseline.id));
            return r;
        }
        if (Curve.hasReverseHeatFlow(seg))
            r.diagnostics.add(new Diagnostic("REVERSE_HEAT_FLOW", "WARN",
                    "heat-flow sign reverses inside segment; verify instrument convention before interpreting",
                    baseline.id));
        if (!seg.isUniformSpacing())
            r.errorSources.add("non-uniform temperature spacing: trapezoid on actual temperatures");
        if (exoConvention == null)
            r.diagnostics.add(new Diagnostic("DIRECTION_UNKNOWN", "BLOCK",
                    "exo/endo direction unavailable in instrument metadata; refusing to guess from file name",
                    baseline.id));

        // onset / peak / endset from deviation against baseline
        double maxDev = 0;
        int peakIdx = 0;
        double[] dev = new double[in.size()];
        for (int i = 0; i < in.size(); i++) {
            dev[i] = in.get(i).heatFlow - baseline.eval(in.get(i).tempC);
            if (Math.abs(dev[i]) > Math.abs(maxDev)) { maxDev = dev[i]; peakIdx = i; }
        }
        double thresh = Math.abs(maxDev) * 0.02 + 1e-9;
        int onset = 0, endset = in.size() - 1;
        for (int i = peakIdx; i >= 0; i--) if (Math.abs(dev[i]) <= thresh) { onset = i; break; }
        for (int i = peakIdx; i < in.size(); i++) if (Math.abs(dev[i]) <= thresh) { endset = i; break; }
        r.onsetC = in.get(onset).tempC;
        r.peakC = in.get(peakIdx).tempC;
        r.endsetC = in.get(endset).tempC;

        // enthalpy: trapezoid over temperature, hf [mW = mJ/s], dt = dT / (rate/60) [s]
        double area_mJ = 0;
        for (int i = 1; i < in.size(); i++) {
            double t0 = in.get(i - 1).tempC, t1 = in.get(i).tempC;
            double y0 = dev[i - 1], y1 = dev[i];
            double dT = t1 - t0;
            double dtSec = Math.abs(dT) / (rateKPerMin / 60.0); // cooling runs scan downward; time is positive
            area_mJ += 0.5 * (y0 + y1) * dtSec; // mJ
        }
        r.enthalpyJg = area_mJ / massMg; // mJ/mg = J/g
        r.errorSources.add("baseline candidate spread (see parallel candidates)");
        r.errorSources.add("sample mass version sensitivity");
        r.errorSources.add("temperature-calibration offset");
        return r;
    }
}
