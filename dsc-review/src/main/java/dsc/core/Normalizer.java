package dsc.core;

import dsc.model.Calibration;
import dsc.model.DeriveDiagnostic;
import dsc.model.Direction;
import dsc.model.MassRecord;
import dsc.model.NormalizedCurve;
import dsc.model.NormalizedPoint;
import dsc.model.RawPoint;
import dsc.model.Segment;
import dsc.model.SignConvention;

import java.util.ArrayList;
import java.util.List;

/**
 * 派生层第一步：
 *  - 温度施加标定平移（°C 基准）；
 *  - 热流统一到 exo-up 的 mW（依据仪器头符号约定，符号“反向”时显式翻转并留痕）；
 *  - 除以当前质量版本得到 J/(g·K)，质量缺失/失效则该量不产生（诊断保留，mW 仍给出）；
 *  - 非等间隔温度允许并显式标记；坏点保留诊断、不参与插值。
 */
public final class Normalizer {
    private Normalizer() {}

    public record Output(NormalizedCurve curve, List<DeriveDiagnostic> diagnostics) {}

    public static Output normalize(String nodeId, Segment seg, MassRecord mass, Calibration cal, String sampleId) {
        NormalizedCurve curve = new NormalizedCurve();
        curve.nodeId = nodeId;
        curve.segmentId = seg.segmentId;
        curve.direction = seg.declaredDirection;
        curve.sign = seg.declaredSign;
        curve.massVersion = mass == null ? null : mass.versionId;
        curve.calibrationVersion = cal == null ? null : cal.versionId;
        List<DeriveDiagnostic> diags = new ArrayList<>();

        double offset = cal != null && cal.offsetC != null && cal.offsetC.valid() ? cal.offsetC.value : 0.0;
        double signFactor = seg.declaredSign.normalizeFactor();
        Double massG = mass != null && mass.mass != null && mass.mass.valid() ? mass.mass.value : null;
        if (massG == null) {
            diags.add(new DeriveDiagnostic(
                    "diag-norm-mass-" + seg.segmentId, "mass_missing", "warning", seg.segmentId, null,
                    "mass unavailable or invalid; J/(g·K) and J/g not produced for this segment",
                    "normalization requires a positive mass version; raw mW retained"));
        }

        if (seg.declaredSign == SignConvention.ENDO_UP) {
            diags.add(new DeriveDiagnostic(
                    "diag-sign-" + seg.segmentId, "reversed_heatflow", "info", seg.segmentId, null,
                    "instrument channel is endo-up; heat flow sign flipped to internal exo-up coordinates",
                    "sign_convention=endo_up from instrument header; filename not consulted"));
        }
        if (seg.declaredSign == SignConvention.UNSPECIFIED) {
            diags.add(new DeriveDiagnostic(
                    "diag-sign-" + seg.segmentId, "sign_unspecified", "warning", seg.segmentId, null,
                    "endo/exo orientation unresolved; thermal sense of transitions reported as ambiguous",
                    "neither endo_up nor exo_up declared by instrument header"));
        }
        if (seg.declaredDirection == Direction.UNKNOWN) {
            diags.add(new DeriveDiagnostic(
                    "diag-dir-" + seg.segmentId, "direction_unspecified", "warning", seg.segmentId, null,
                    "heating/cooling unknown; this segment is never mixed with declared segments",
                    "direction must come from instrument metadata"));
        }

        List<RawPoint> usable = seg.usablePoints();
        List<Double> spacings = new ArrayList<>();
        for (RawPoint p : usable) {
            double t = p.tempC + offset;
            double hf = p.hfMw * signFactor;
            double jgk = massG != null && seg.rate != null && seg.rate.valid()
                    ? (hf / 1000.0) / (seg.rate.value / 60.0) / massG
                    : Double.NaN;
            curve.points.add(new NormalizedPoint(p.index, t, hf, jgk));
        }
        for (RawPoint p : seg.points) {
            if (!p.usable()) {
                curve.dropped.add("point#" + p.index + " retained-as-raw: " + p.diagnostic);
                diags.add(new DeriveDiagnostic(
                        "diag-point-" + seg.segmentId + "-" + p.index, "bad_point", "info",
                        seg.segmentId, null,
                        "point#" + p.index + " excluded from derivation but original token retained",
                        p.diagnostic));
            }
        }
        for (int i = 1; i < curve.points.size(); i++) {
            spacings.add(Math.abs(curve.points.get(i).tempC - curve.points.get(i - 1).tempC));
        }
        double mean = spacings.stream().mapToDouble(Double::doubleValue).average().orElse(Double.NaN);
        curve.meanSpacingK = Double.isNaN(mean) ? null : mean;
        boolean equi = true;
        if (!Double.isNaN(mean) && mean > 0) {
            for (double sp : spacings) {
                if (Math.abs(sp - mean) / mean > 0.25) { equi = false; break; }
            }
        }
        curve.equidistant = equi;
        if (!equi) {
            double min = spacings.stream().mapToDouble(Double::doubleValue).min().orElse(0);
            double max = spacings.stream().mapToDouble(Double::doubleValue).max().orElse(0);
            diags.add(new DeriveDiagnostic(
                    "diag-spacing-" + seg.segmentId, "non_equidistant", "info", seg.segmentId, null,
                    String.format("non-equidistant temperature grid: mean %.3f K, range %.3f..%.3f K; trapezoid rule used",
                            mean, min, max),
                    "spacing deviation >25% of mean; integration uses trapezoids per actual interval"));
        }
        curve.sourceHash = IdsHash.forCurve(seg, mass, cal);
        return new Output(curve, diags);
    }
}
