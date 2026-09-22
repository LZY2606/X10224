package dsc.derive;

import dsc.events.Judgment;
import dsc.model.*;

import java.util.*;

/**
 * Baseline construction rules.
 *
 * <p>Key semantics enforced here:
 * <ul>
 *   <li>a region is bound to one segment; intervals crossing a cycle boundary
 *       are rejected instead of silently stitching heating and cooling data;</li>
 *   <li>several legal baselines (two-sided tangents, parallel-shift, global)
 *       are all returned as candidates — the method never hides multiplicity;</li>
 *   <li>an empty interval yields an INDETERMINATE candidate carrying the cause.</li>
 * </ul>
 */
public class BaselineRules {

    private static final int GLOBAL_WINDOW = 3;

    public List<BaselineResult> derive(String regionId, Region region, ScanSegment segment,
                                       TangentSelection tangent, Calibration calibration) {
        double offset = calibration == null ? 0.0 : calibration.offsetCelsius().value();
        List<SamplePoint> inRegion = new ArrayList<>();
        List<double[]> segmentPts = new ArrayList<>();
        for (SamplePoint p : segment.plottablePoints()) {
            double t = p.tCelsius().value() + offset;
            segmentPts.add(new double[]{t, p.hfMilliwatts().value()});
            if (region.contains(t)) inRegion.add(p);
        }
        if (inRegion.isEmpty()) {
            return List.of(indeterminate(regionId, region, "EMPTY_REGION",
                    "no measured points fall inside the selected interval",
                    Map.of("tLo", region.tLoCelsius(), "tHi", region.tHiCelsius())));
        }
        double[] rt = new double[inRegion.size()];
        double[] rh = new double[inRegion.size()];
        for (int i = 0; i < inRegion.size(); i++) {
            rt[i] = inRegion.get(i).tCelsius().value() + offset;
            rh[i] = inRegion.get(i).hfMilliwatts().value();
        }

        List<BaselineResult> out = new ArrayList<>();
        out.add(twoSided(regionId, region, tangent, segmentPts, rt, rh));
        out.add(parallelShift(regionId, region, tangent, segmentPts, rt, rh));
        out.add(globalSlope(regionId, region, segmentPts, rt, rh));
        return List.copyOf(out);
    }

    private BaselineResult twoSided(String regionId, Region region, TangentSelection tan,
                                    List<double[]> seg, double[] rt, double[] rh) {
        List<Judgment> js = new ArrayList<>();
        if (tan == null) {
            return indeterminate(regionId, region, "TANGENT_MISSING",
                    "no tangent selection archived for this region", Map.of());
        }
        double[] lw = window(seg, tan.leftLoCelsius(), tan.leftHiCelsius());
        double[] rw = window(seg, tan.rightLoCelsius(), tan.rightHiCelsius());
        if (lw == null || rw == null) {
            return indeterminate(regionId, region, "TANGENT_WINDOW_EMPTY",
                    "a tangent anchor window contains no measured points",
                    Map.of("leftPoints", lw == null ? 0 : 1, "rightPoints", rw == null ? 0 : 1));
        }
        double[] leftFit = fitWindow(seg, lw);
        double[] rightFit = fitWindow(seg, rw);
        double slope = (leftFit[0] + rightFit[0]) / 2.0;
        double intercept = (leftFit[1] + rightFit[1]) / 2.0;
        double rmse = Numeric.rmse(rt, rh, slope, intercept);
        if (Math.abs(leftFit[0] - rightFit[0]) > 0.002) {
            js.add(Judgment.of("BASELINE_SLOPE_MISMATCH", Judgment.Severity.SUSPECT, regionId,
                    "left/right tangent slopes disagree; both baselines remain legal candidates",
                    RuleVersions.BASELINE_RULES,
                    Map.of("leftSlope", leftFit[0], "rightSlope", rightFit[0])));
        }
        return new BaselineResult(
                dsc.core.Ids.derived("bl", RuleVersions.BASELINE_RULES, regionId,
                        "two-sided", fmt(tan.leftLoCelsius()), fmt(tan.leftHiCelsius()),
                        fmt(tan.rightLoCelsius()), fmt(tan.rightHiCelsius())),
                regionId, "two-sided", slope, intercept, tan.leftLoCelsius(), tan.rightHiCelsius(),
                rmse, NodeStatus.MULTIPLE, RuleVersions.BASELINE_RULES,
                List.of("seg:" + region.segmentId(), "tan:" + (tan == null ? "none" : tan.tangentId())),
                js, -1);
    }

    private BaselineResult parallelShift(String regionId, Region region, TangentSelection tan,
                                         List<double[]> seg, double[] rt, double[] rh) {
        if (tan == null) {
            return indeterminate(regionId, region, "TANGENT_MISSING",
                    "no tangent selection archived for this region", Map.of());
        }
        double[] lw = window(seg, tan.leftLoCelsius(), tan.leftHiCelsius());
        if (lw == null) {
            return indeterminate(regionId, region, "TANGENT_WINDOW_EMPTY",
                    "the pre-transition anchor window contains no measured points", Map.of());
        }
        double[] fit = fitWindow(seg, lw);
        double slope = fit[0];
        double intercept = fit[1];
        double rmse = Numeric.rmse(rt, rh, slope, intercept);
        List<Judgment> js = new ArrayList<>();
        js.add(Judgment.of("BASELINE_PARALLEL_SHIFT", Judgment.Severity.INFO, regionId,
                "baseline fixed by pre-transition tangent only (parallel-shift candidate)",
                RuleVersions.BASELINE_RULES, Map.of("slope", slope)));
        return new BaselineResult(
                dsc.core.Ids.derived("bl", RuleVersions.BASELINE_RULES, regionId,
                        "parallel", fmt(tan.leftLoCelsius()), fmt(tan.leftHiCelsius())),
                regionId, "parallel", slope, intercept, tan.leftLoCelsius(), tan.leftHiCelsius(),
                rmse, NodeStatus.MULTIPLE, RuleVersions.BASELINE_RULES,
                List.of("seg:" + region.segmentId(), "tan:" + tan.tangentId()), js, -1);
    }

    private BaselineResult globalSlope(String regionId, Region region, List<double[]> seg,
                                       double[] rt, double[] rh) {
        List<double[]> ends = new ArrayList<>();
        int n = seg.size();
        for (int i = 0; i < Math.min(GLOBAL_WINDOW, n); i++) ends.add(seg.get(i));
        for (int i = Math.max(GLOBAL_WINDOW, n - GLOBAL_WINDOW); i < n; i++) ends.add(seg.get(i));
        if (ends.size() < 2) {
            return indeterminate(regionId, region, "SEGMENT_TOO_SHORT",
                    "segment is too short to build an end-to-end baseline", Map.of());
        }
        double[] x = ends.stream().mapToDouble(d -> d[0]).toArray();
        double[] y = ends.stream().mapToDouble(d -> d[1]).toArray();
        double[] fit = Numeric.linearFit(x, y);
        double rmse = Numeric.rmse(rt, rh, fit[0], fit[1]);
        List<Judgment> js = List.of(Judgment.of("BASELINE_GLOBAL_ENDS", Judgment.Severity.INFO,
                regionId, "end-to-end chord baseline candidate; ignores local tangent curvature",
                RuleVersions.BASELINE_RULES, Map.of("anchorPoints", ends.size())));
        return new BaselineResult(
                dsc.core.Ids.derived("bl", RuleVersions.BASELINE_RULES, regionId, "global",
                        Integer.toString(ends.size())),
                regionId, "global", fit[0], fit[1],
                seg.isEmpty() ? 0 : seg.get(0)[0], seg.isEmpty() ? 0 : seg.get(n - 1)[0],
                rmse, NodeStatus.MULTIPLE, RuleVersions.BASELINE_RULES,
                List.of("seg:" + region.segmentId()), js, -1);
    }

    private BaselineResult indeterminate(String regionId, Region region, String code, String msg,
                                         Map<String, Object> evidence) {
        return new BaselineResult(
                dsc.core.Ids.derived("bl", RuleVersions.BASELINE_RULES, regionId, code,
                        fmt(region.tLoCelsius()), fmt(region.tHiCelsius())),
                regionId, "indeterminate", Double.NaN, Double.NaN,
                region.tLoCelsius(), region.tHiCelsius(), Double.NaN,
                NodeStatus.INDETERMINATE, RuleVersions.BASELINE_RULES,
                List.of("seg:" + region.segmentId()),
                List.of(Judgment.of(code, Judgment.Severity.REJECTED, regionId, msg,
                        RuleVersions.BASELINE_RULES, evidence)), -1);
    }

    private double[] window(List<double[]> seg, double lo, double hi) {
        List<Double> xs = new ArrayList<>();
        List<Double> ys = new ArrayList<>();
        for (double[] p : seg) {
            if (p[0] >= lo - 1e-9 && p[0] <= hi + 1e-9) { xs.add(p[0]); ys.add(p[1]); }
        }
        if (xs.size() < 2) return null;
        double[] out = new double[xs.size() * 2];
        for (int i = 0; i < xs.size(); i++) { out[i] = xs.get(i); out[xs.size() + i] = ys.get(i); }
        return out;
    }

    private double[] fitWindow(List<double[]> seg, double[] packed) {
        int m = packed.length / 2;
        double[] x = new double[m];
        double[] y = new double[m];
        System.arraycopy(packed, 0, x, 0, m);
        System.arraycopy(packed, m, y, 0, m);
        return Numeric.linearFit(x, y);
    }

    private static String fmt(double v) { return dsc.core.Units.fmt(v); }
}
