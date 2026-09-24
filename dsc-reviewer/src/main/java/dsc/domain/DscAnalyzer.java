package dsc.domain;

import dsc.domain.Diagnostic.Code;
import dsc.domain.Diagnostic.Severity;

import java.util.ArrayList;
import java.util.List;

import static dsc.domain.BaselineFit.Line;

/**
 * Recomputable DSC rules. Everything is pure: the same (segment, interval,
 * tangent selection, metadata, ruleVersion) always yields the same
 * {@link TransitionResult}, including its stable content-hash id.
 *
 * Guarded semantics (see RULES.md):
 *  - endo/exo direction comes only from the instrument convention;
 *  - heating and cooling data are never combined;
 *  - integration tolerates non-uniform temperature spacing (true trapezoid in
 *    temperature, no resampling);
 *  - empty / over-sparse intervals refuse with diagnostics instead of 0.
 */
public final class DscAnalyzer {

    public static final String RULE_VERSION = "rules-1.0.0";

    private DscAnalyzer() {
    }

    /** Analyse one interval. Mass may be null (un-normalised area is still returned). */
    public static TransitionResult analyse(
            Segment segment,
            Interval rawInterval,
            TangentSelection tangent,
            TransitionType type,
            Metadata metadata,
            String massVersion) {

        List<Diagnostic> diag = new ArrayList<>();
        Interval interval = rawInterval.normalized();

        // 1) Direction / convention guards ----------------------------------------------------
        guardSegment(segment, diag);

        // 2) Slice the data actually inside the interval (segments are never mixed) -----------
        int[] idx = slice(segment.raw().temperature(), interval);
        if (idx[1] - idx[0] < 2) {
            diag.add(new Diagnostic(Severity.ERROR, Code.EMPTY_INTERVAL,
                    "interval " + interval.startK() + ".." + interval.endK()
                            + " K contains fewer than two samples", null));
            return TransitionIds.result(segment.id(), interval, type, null,
                    null, null, null, null, null, null, List.of(), diag, massVersion);
        }

        // 3) Baseline candidates from the archived tangent windows ----------------------------
        BaselineFit baseline = fitBaseline(segment, tangent, diag);
        boolean baselineUsable = baseline.diagnostics().stream()
                .noneMatch(d -> d.severity() == Severity.ERROR);

        // 4) Non-uniform spacing diagnostic (informational; trapezoid handles it exactly) ------
        flagNonUniform(segment.raw().temperature(), idx, diag);

        Double onset = null;
        Double peak = null;
        Double endset = null;
        Double peakHF = null;
        Double areaMilliJ = null;
        Double enthalpy = null;
        List<UncertaintySource> uncertainty = new ArrayList<>();

        if (baselineUsable) {
            double[] t = segment.raw().temperature();
            double[] hf = segment.raw().heatFlow();

            // 5) Peak of the baseline-subtracted signal ----------------------------------------
            int peakIdx = peakIndex(t, hf, idx, baseline, segment.raw().convention(), type);
            peak = t[peakIdx];
            peakHF = hf[peakIdx] - baseline.yAt(t[peakIdx]);

            // 6) Onset / endset as tangent-line intersections ----------------------------------
            double[] oe = onsetEndset(t, hf, idx, baseline, peakIdx,
                    segment.raw().convention(), diag);
            onset = oe[0];
            endset = oe[1];

            // 7) Area in mW*K, then / scan rate (K/min) * 60 -> mJ; canonical store is MJ ------
            double areaMWK = trapezoidAreaMWK(t, hf, idx, baseline,
                    segment.raw().convention(), type);
            double rateKPerMin = metadata.scanRate().value();
            double integratedMilliJ = areaMWK / rateKPerMin * 60.0;
            areaMilliJ = integratedMilliJ;
            // 8) Mass normalisation; a stale/unknown mass never silently zeroes the enthalpy ---
            if (metadata.mass() != null && metadata.mass().convertible() && metadata.mass().value() > 0) {
                double massG = metadata.mass().value() / 1000.0; // mg -> g
                enthalpy = areaMilliJ / 1000.0 / massG;             // mJ -> J
                uncertainty = uncertaintyBudget(segment, metadata, tangent, baseline,
                        idx, areaMWK, rateKPerMin, massG);
            } else {
                diag.add(new Diagnostic(Severity.ERROR, Code.MASS_NOT_NORMALIZABLE,
                        "specific enthalpy unavailable: mass "
                                + (metadata.mass() == null ? "missing" : metadata.mass().conversionError()),
                        metadata.mass() == null ? null : massVersion));
            }
            return TransitionIds.result(segment.id(), interval, type, baseline,
                    onset, peak, endset, peakHF, areaMilliJ, enthalpy, uncertainty, diag, massVersion);
        }

        return TransitionIds.result(segment.id(), interval, type, baseline,
                null, null, null, null, null, null, List.of(), diag, massVersion);
    }

    // ------------------------------------------------------------------ guards

    public static void guardSegment(Segment segment, List<Diagnostic> diag) {
        ScanDirection observed = segment.observedDirection();
        if (observed == ScanDirection.NON_MONOTONIC) {
            diag.add(new Diagnostic(Severity.ERROR, Code.NON_MONOTONIC_PROGRESSION,
                    "segment '" + segment.id() + "' reverses direction; split cycles before analysis",
                    segment.id()));
        }
        if (observed != ScanDirection.NON_MONOTONIC
                && segment.declaredDirection() != observed
                && observed != ScanDirection.ISOTHERMAL) {
            diag.add(new Diagnostic(Severity.WARNING, Code.DIRECTION_MISMATCH,
                    "declared " + segment.declaredDirection() + " but temperature progression is "
                            + observed, segment.id()));
        }
        if (segment.raw().convention() == HeatFlowConvention.UNKNOWN) {
            diag.add(new Diagnostic(Severity.ERROR, Code.UNKNOWN_HEATFLOW_CONVENTION,
                    "endo/exo sign unknown; refusing direction-sensitive integration", segment.id()));
        }
        for (RawData.RawRowIssue issue : segment.raw().badRows()) {
            diag.add(new Diagnostic(Severity.WARNING, Code.UNPARSEABLE_CELL,
                    "row " + issue.rowIndex() + ": " + issue.reason(), segment.id()));
        }
    }

    static int[] slice(double[] t, Interval interval) {
        int first = -1;
        int last = -1;
        for (int i = 0; i < t.length; i++) {
            if (interval.contains(t[i])) {
                if (first < 0) first = i;
                last = i;
            }
        }
        // always include the bounding samples so trapezoid spans the full interval
        if (first > 0) first--;
        if (last >= 0 && last < t.length - 1) last++;
        return new int[]{Math.max(first, 0), last < 0 ? 0 : Math.min(last + 1, t.length)};
    }

    static void flagNonUniform(double[] t, int[] idx, List<Diagnostic> diag) {
        double prev = Double.NaN;
        double min = Double.POSITIVE_INFINITY;
        double max = 0;
        for (int i = idx[0] + 1; i < idx[1]; i++) {
            double d = Math.abs(t[i] - t[i - 1]);
            if (!(d > 0)) continue;
            min = Math.min(min, d);
            max = Math.max(max, d);
            prev = d;
        }
        if (max > 0 && min < Double.POSITIVE_INFINITY && max / Math.max(min, 1e-12) > 3.0) {
            diag.add(new Diagnostic(Severity.INFO, Code.NON_UNIFORM_SPACING,
                    String.format("temperature spacing varies %.3g..%.3g K; trapezoid integration used",
                            min, max), null));
        }
    }

    // ---------------------------------------------------------------- baseline

    public static BaselineFit fitBaseline(Segment segment, TangentSelection sel, List<Diagnostic> diag) {
        double[] t = segment.raw().temperature();
        double[] hf = segment.raw().heatFlow();
        int[] lwin = slice(t, sel.leftWindow().normalized());
        int[] rwin = slice(t, sel.rightWindow().normalized());

        int lCount = countInside(t, sel.leftWindow());
        int rCount = countInside(t, sel.rightWindow());
        if (lCount < 2) {
            diag.add(new Diagnostic(Severity.ERROR, Code.WINDOW_TOO_SPARSE,
                    "left tangent window holds " + lCount + " sample(s); need >= 2", sel.id()));
        }
        if (rCount < 2) {
            diag.add(new Diagnostic(Severity.WARNING, Code.WINDOW_TOO_SPARSE,
                    "right tangent window holds " + rCount + " sample(s); need >= 2", sel.id()));
        }

        Line left = linearFit(t, hf, lwin);
        Line right = linearFit(t, hf, rwin);
        double rmse = rmse(t, hf, lwin, left) + rmse(t, hf, rwin, right);

        if (sel.model() == BaselineModel.LINEAR_TANGENT) {
            return new BaselineFit(BaselineModel.LINEAR_TANGENT, left, null, null,
                    lCount, rCount, rmse, List.copyOf(diag));
        }

        // STEP_TANGENT: join of the two tangents is Tg; a missing intersection is a refusal.
        double join = Double.NaN;
        if (Math.abs(left.b() - right.b()) > 1e-15) {
            join = (right.a() - left.a()) / (left.b() - right.b());
        }
        if (Double.isNaN(join) || join < sel.leftWindow().startK() || join > sel.rightWindow().endK()) {
            diag.add(new Diagnostic(Severity.ERROR, Code.NO_LEGAL_RESULT,
                    "pre/post Tg tangents do not intersect between the tangent windows", sel.id()));
        }
        return new BaselineFit(BaselineModel.STEP_TANGENT, left, right,
                Double.isNaN(join) ? null : join, lCount, rCount, rmse, List.copyOf(diag));
    }

    static int countInside(double[] t, Interval w) {
        int n = 0;
        for (double v : t) if (w.normalized().contains(v)) n++;
        return n;
    }

    static Line linearFit(double[] t, double[] hf, int[] idx) {
        double sx = 0, sy = 0, sxx = 0, sxy = 0;
        int n = 0;
        for (int i = idx[0]; i < idx[1]; i++) {
            if (Double.isNaN(hf[i])) continue;
            sx += t[i];
            sy += hf[i];
            sxx += t[i] * t[i];
            sxy += t[i] * hf[i];
            n++;
        }
        if (n < 2) return new Line(0, 0);
        double den = n * sxx - sx * sx;
        if (Math.abs(den) < 1e-18) return new Line(sy / n, 0);
        double b = (n * sxy - sx * sy) / den;
        double a = (sy - b * sx) / n;
        return new Line(a, b);
    }

    static double rmse(double[] t, double[] hf, int[] idx, Line line) {
        double acc = 0;
        int n = 0;
        for (int i = idx[0]; i < idx[1]; i++) {
            double r = hf[i] - line.y(t[i]);
            acc += r * r;
            n++;
        }
        return n == 0 ? Double.POSITIVE_INFINITY : Math.sqrt(acc / n);
    }

    // ------------------------------------------------------------- peak/onset

    static int peakIndex(double[] t, double[] hf, int[] idx, BaselineFit base,
                         HeatFlowConvention convention, TransitionType type) {
        // For crystallisation (exotherm on cooling) the extremum flips with the
        // convention; glass transition has no single peak (step).
        boolean seekMax;
        if (type == TransitionType.CRYSTALLISATION) {
            seekMax = convention == HeatFlowConvention.ENDO_DOWN; // exo points up
        } else {
            seekMax = convention == HeatFlowConvention.ENDO_UP;   // endo (melting) up
        }
        int best = idx[0];
        double bestV = -Double.MAX_VALUE;
        if (!seekMax) bestV = Double.MAX_VALUE;
        for (int i = idx[0]; i < idx[1]; i++) {
            double v = hf[i] - base.yAt(t[i]);
            if ((seekMax && v > bestV) || (!seekMax && v < bestV)) {
                bestV = v;
                best = i;
            }
        }
        return best;
    }

    /**
     * Onset = intersection of the leading-side tangent (max slope through the
     * steepest points) with the extrapolated baseline; endset analogously on
     * the trailing side. Values stay in K. A side without a defined slope gets
     * NaN and a diagnostic rather than a fabricated boundary.
     */
    static double[] onsetEndset(double[] t, double[] hf, int[] idx, BaselineFit base,
                                int peakIdx, HeatFlowConvention convention,
                                List<Diagnostic> diag) {
        double leading = tangentIntersection(t, hf, idx[0], peakIdx, base, convention, true, diag);
        double trailing = tangentIntersection(t, hf, peakIdx, idx[1] - 1, base, convention, false, diag);
        return new double[]{leading, trailing};
    }

    private static double tangentIntersection(double[] t, double[] hf, int from, int to,
                                              BaselineFit base, HeatFlowConvention convention,
                                              boolean leading, List<Diagnostic> diag) {
        int sign = convention == HeatFlowConvention.ENDO_UP ? 1 : -1;
        double bestSlope = 0;
        int bi = from;
        int bj = Math.min(from + 1, to);
        for (int i = from; i < to; i++) {
            for (int j = i + 1; j <= Math.min(to, i + 4); j++) {
                double dt = t[j] - t[i];
                if (Math.abs(dt) < 1e-12) continue;
                double slope = (hf[j] - hf[i]) / dt;
                double directed = leading ? sign * slope : -sign * slope;
                if (directed > bestSlope) {
                    bestSlope = directed;
                    bi = i;
                    bj = j;
                }
            }
        }
        double dt = t[bj] - t[bi];
        if (Math.abs(dt) < 1e-12) {
            diag.add(new Diagnostic(Severity.WARNING, Code.NO_LEGAL_RESULT,
                    (leading ? "onset" : "endset") + " tangent undefined", null));
            return Double.NaN;
        }
        double m = (hf[bj] - hf[bi]) / dt;
        double a = hf[bi] - m * t[bi];
        Line baseLine = base.model() == BaselineModel.LINEAR_TANGENT ? base.left() : base.left();
        if (Math.abs(m - baseLine.b()) < 1e-15) return Double.NaN;
        return (baseLine.a() - a) / (m - baseLine.b());
    }

    // -------------------------------------------------------------- integrate

    /**
     * True trapezoid area in mW*K over the (possibly non-uniform) temperature
     * grid — no resampling, so no information is discarded "to make the plot".
     * The sign follows the event direction given by the instrument convention;
     * a reversed heat-flow convention yields the opposite sign instead of a
     * silent magnitude-only answer.
     */
    static double trapezoidAreaMWK(double[] t, double[] hf, int[] idx, BaselineFit base,
                                   HeatFlowConvention convention, TransitionType type) {
        double area = 0;
        for (int i = idx[0] + 1; i < idx[1]; i++) {
            double dt = t[i] - t[i - 1];
            if (dt == 0) continue;
            double y0 = hf[i - 1] - base.yAt(t[i - 1]);
            double y1 = hf[i] - base.yAt(t[i]);
            if (type == TransitionType.GLASS_TRANSITION) {
                // step integration vs the pre-Tg (glassy) line keeps the sign of the Cp jump
                y0 = hf[i - 1] - base.left().y(t[i - 1]);
                y1 = hf[i] - base.left().y(t[i]);
            }
            area += 0.5 * (y0 + y1) * dt;
        }
        double orient = convention == HeatFlowConvention.ENDO_UP ? 1.0 : -1.0;
        // Crystallisation is exothermic: report positive latent heat regardless of deflection.
        if (type == TransitionType.CRYSTALLISATION) area = -area;
        return area * orient;
    }

    /**
     * Build the enthalpy error budget. Each term carries the id of the source
     * record it comes from, so the UI can walk from an error bar to the exact
     * calibration / mass / tangent version it depends on.
     */
    static List<UncertaintySource> uncertaintyBudget(Segment segment, Metadata md,
                                                      TangentSelection tangent, BaselineFit base,
                                                      int[] idx, double areaMWK,
                                                      double rateKPerMin, double massG) {
        List<UncertaintySource> out = new ArrayList<>();

        // Baseline placement: half the local fit RMSE across the integrated width.
        double width = segment.raw().temperature()[idx[1] - 1] - segment.raw().temperature()[idx[0]];
        double baselineJ = (base.rmseMW() * width) / rateKPerMin * 60.0 / 1000.0;
        out.add(new UncertaintySource(tangent.id(), "tangent",
                baselineJ / massG, "fit residual RMSE over integrated width"));

        // Scan-rate calibration is assumed good to 1% of the declared rate.
        double rateJ = Math.abs(areaMWK / (rateKPerMin * 1.01) * 60.0 / 1000.0
                - areaMWK / rateKPerMin * 60.0 / 1000.0);
        out.add(new UncertaintySource(md.id(), "scanRate", rateJ / massG,
                "1% scan-rate calibration band"));

        // Mass: 0.1 mg balance resolution (or larger when the mass is small).
        double massAbs = md.mass().value();
        double balanceMg = Math.max(0.1, massAbs * 0.002);
        double enthalpyAbs = Math.abs(areaMWK / rateKPerMin * 60.0 / 1000.0);
        out.add(new UncertaintySource(md.id(), "mass",
                enthalpyAbs * balanceMg / (massAbs * massG),
                "balance resolution " + Quantity.fmt(balanceMg) + " mg"));

        // Temperature calibration shifts boundaries; take 0.2 K worth of peak height.
        out.add(new UncertaintySource(md.id(), "temperatureCalibration",
                0.2 * Math.abs(base.rmseMW()) / rateKPerMin * 60.0 / 1000.0 / massG,
                "0.2 K calibration uncertainty across boundary"));
        return List.copyOf(out);
    }
}
