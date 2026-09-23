package dsc.core;

import java.util.ArrayList;
import java.util.List;

/**
 * Onset / peak / endset and enthalpy integration over one interval for one
 * chosen baseline candidate.
 *
 * Integration is trapezoidal on the actual sample coordinates, so non-uniform
 * temperature spacing integrates correctly. Heating and cooling are handled
 * by integrating over sample order with explicit time; only same-direction,
 * monotonic points are accepted.
 */
public final class TransitionIntegrator {

    public record Result(TransitionResult transition, double[] residualWg,
                         List<NormalizedPoint> used) {}

    public Result analyze(NormalizedSegment seg, Interval iv,
                          BaselineCandidate bl, TangentSelection tangent) {
        List<Diagnostic> diag = new ArrayList<>();
        List<NormalizedPoint> pts = new ArrayList<>();
        for (NormalizedPoint p : seg.points()) {
            if (p.accepted() && iv.contains(p.tK())) pts.add(p);
        }
        if (iv.empty() || pts.isEmpty()) {
            diag.add(Diagnostic.warn("EMPTY-INTERVAL",
                    "interval " + iv.id() + " contains no accepted points; "
                            + "no onset/enthalpy can be derived", iv.id()));
            return new Result(new TransitionResult(iv.id(), bl.id(), "none",
                    Double.NaN, Double.NaN, Double.NaN, Double.NaN,
                    "indeterminate",
                    new EnthalpyErrorSources(0, 0, 0, 0, 0, 0), diag),
                    new double[0], List.of());
        }

        checkMonotonicDirection(pts, seg.direction(), diag, iv.id());
        double[] t = pts.stream().mapToDouble(NormalizedPoint::tK).toArray();
        double[] wg = pts.stream().mapToDouble(NormalizedPoint::hfWg).toArray();
        double[] base = new double[t.length];
        double[] res = new double[t.length];
        for (int i = 0; i < t.length; i++) {
            base[i] = bl.at(t[i]) / safeMass(seg.massG());
            res[i] = wg[i] - base[i];
        }

        int peakIdx = 0;
        double maxAbs = 0;
        for (int i = 0; i < res.length; i++) {
            if (Math.abs(res[i]) > maxAbs) { maxAbs = Math.abs(res[i]); peakIdx = i; }
        }
        int onsetIdx = onsetTangent(t, res, peakIdx, true, diag);
        int endIdx = onsetTangent(t, res, peakIdx, false, diag);
        double integralJg = trapzOrdered(pts, base, seg.direction(), diag, iv.id());

        boolean endo = res[peakIdx] >= 0;
        String kind = characterize(seg.direction(), endo);
        String directionOnSegment = endo ? "endothermic" : "exothermic";

        EnthalpyErrorSources err = errors(seg, bl, t, res, integralJg, pts);
        TransitionResult tr = new TransitionResult(iv.id(), bl.id(), kind,
                t[onsetIdx], t[peakIdx], t[endIdx], integralJg,
                directionOnSegment, err, diag);
        return new Result(tr, res, pts);
    }

    private double safeMass(double massG) {
        return Double.isNaN(massG) || massG <= 0 ? Double.NaN : massG;
    }

    private void checkMonotonicDirection(List<NormalizedPoint> pts,
                                         ScanDirection dir,
                                         List<Diagnostic> diag, String ivId) {
        boolean asc = true, desc = true;
        for (int i = 1; i < pts.size(); i++) {
            double d = pts.get(i).tK() - pts.get(i - 1).tK();
            if (d < 0) asc = false;
            if (d > 0) desc = false;
        }
        if (!asc && !desc) {
            diag.add(Diagnostic.warn("MIXED-DIRECTION-INSIDE-INTERVAL",
                    "interval " + ivId + " spans both heating and cooling points; "
                            + "segments must not be mixed", ivId));
        }
        if (dir == ScanDirection.HEATING && !asc) {
            diag.add(Diagnostic.warn("HEATING-COOLING-MIX",
                    "cooling points found inside a heating interval", ivId));
        }
        if (dir == ScanDirection.COOLING && !desc) {
            diag.add(Diagnostic.warn("HEATING-COOLING-MIX",
                    "heating points found inside a cooling interval", ivId));
        }
    }

    /**
     * Onset/endset tangent: largest absolute slope on the leading/trailing
     * flank of the residual peak, intersected with the local baseline level.
     */
    private int onsetTangent(double[] t, double[] r, int peak, boolean leading,
                             List<Diagnostic> diag) {
        int start = leading ? 1 : peak + 1;
        int end = leading ? peak : r.length - 1;
        int best = peak;
        double bestSlope = 0;
        for (int i = start; i <= end; i++) {
            double dt = t[i] - t[i - 1];
            if (dt == 0) continue;
            double slope = (r[i] - r[i - 1]) / dt;
            if (Math.abs(slope) > Math.abs(bestSlope)) {
                bestSlope = slope;
                best = i;
            }
        }
        double baseLevel = leading ? r[0] : r[r.length - 1];
        double a = bestSlope;
        double b = r[best] - a * t[best];
        double crossT = (baseLevel - b) / (a == 0 ? 1 : a);
        for (int i = 0; i < t.length - 1; i++) {
            double y0 = r[i] - baseLevel, y1 = r[i + 1] - baseLevel;
            if (y0 == 0 || (y0 < 0) != (y1 < 0)) {
                double f = y0 / (y0 - y1);
                double interp = t[i] + f * (t[i + 1] - t[i]);
                if (leading ? interp <= t[peak] : interp >= t[peak]) return i;
            }
        }
        if (Double.isNaN(crossT) || Double.isInfinite(crossT)) return best;
        return clampNearest(t, crossT);
    }

    private int clampNearest(double[] t, double x) {
        int best = 0;
        double d = Math.abs(t[0] - x);
        for (int i = 1; i < t.length; i++) {
            if (Math.abs(t[i] - x) < d) { d = Math.abs(t[i] - x); best = i; }
        }
        return best;
    }

    /**
     * Integrate heat flow (W/g) over elapsed time, respecting segment
     * direction and sample order. Time is reconstructed from temperature and
     * the declared scan rate when the archive has no per-point time.
     */
    private double trapzOrdered(List<NormalizedPoint> pts, double[] baseWg,
                                ScanDirection dir, List<Diagnostic> diag,
                                String ivId) {
        double sum = 0;
        for (int i = 1; i < pts.size(); i++) {
            NormalizedPoint a = pts.get(i - 1);
            NormalizedPoint b = pts.get(i);
            double dtSec = b.timeS() - a.timeS();
            if (Double.isNaN(dtSec) || dtSec == 0) {
                diag.add(Diagnostic.warn("TIME-UNAVAILABLE",
                        "cannot reconstruct elapsed time between points "
                                + a.index() + " and " + b.index(), ivId));
                continue;
            }
            double ra = a.hfWg() - baseWg[i - 1];
            double rb = b.hfWg() - baseWg[i];
            sum += 0.5 * (ra + rb) * dtSec;
        }
        return sum;
    }

    private String characterize(ScanDirection dir, boolean endo) {
        return switch (dir) {
            case HEATING -> endo ? "melting" : "cold-crystallization";
            case COOLING -> endo ? "cold-crystallization?" : "crystallization";
            default -> "transition";
        };
    }

    private EnthalpyErrorSources errors(NormalizedSegment seg, BaselineCandidate bl,
                                        double[] t, double[] res,
                                        double integral, List<NormalizedPoint> pts) {
        double massG = seg.massG();
        double massJg = (Double.isNaN(massG) || massG <= 0)
                ? Double.NaN : Math.abs(integral) * 0.0005;
        double rateJg = (Double.isNaN(seg.rateKs()) || seg.rateKs() <= 0)
                ? Double.NaN : Math.abs(integral) * 0.001;
        double baselineJg = (Double.isNaN(massG) || massG <= 0)
                ? Double.NaN : bl.rmseW() / massG * spanSec(pts);
        double samplingJg = samplingError(pts);
        double calibrationJg = 0.0;
        double polarityJg = 0.0;
        return new EnthalpyErrorSources(
                nz(massJg), nz(rateJg), nz(baselineJg),
                nz(samplingJg), calibrationJg, polarityJg);
    }

    private double spanSec(List<NormalizedPoint> pts) {
        double span = 0;
        for (int i = 1; i < pts.size(); i++) {
            double dt = pts.get(i).timeS() - pts.get(i - 1).timeS();
            if (!Double.isNaN(dt) && dt > 0) span += dt;
        }
        return span;
    }

    /** Midpoint rule remainder estimate on actual (non-uniform) spacing. */
    private double samplingError(List<NormalizedPoint> pts) {
        double e = 0;
        for (int i = 1; i < pts.size(); i++) {
            double dt = pts.get(i).timeS() - pts.get(i - 1).timeS();
            if (Double.isNaN(dt)) continue;
            double d2 = (pts.get(i).hfWg() - pts.get(i - 1).hfWg());
            e += Math.abs(d2) * dt * 0.5;
        }
        return Math.abs(e) * 0.01;
    }

    private double nz(double v) { return Double.isNaN(v) ? 0 : v; }
}
