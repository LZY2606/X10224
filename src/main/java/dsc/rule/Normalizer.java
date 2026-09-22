package dsc.rule;

import dsc.model.Diagnostic;
import dsc.model.HeatFlowSign;
import dsc.model.Point;
import dsc.model.ScanMode;
import dsc.model.Segment;
import java.util.ArrayList;
import java.util.List;

/**
 * 方向规范化。吸放热方向只能来自仪器输出元数据，缺失时保持不确定状态；
 * 加热/冷却按数据实际走向判定。所有后续规则只消费统一的“吸热向上”曲线。
 */
public final class Normalizer {

    public record NormalizedSegment(
            String segmentId,
            ScanMode observedMode,
            boolean signFlipped,
            List<XY> curve,
            List<Point> droppedPoints,
            List<Diagnostic> diagnostics) {
    }

    public record NormalizationResult(List<NormalizedSegment> segments, List<Diagnostic> diagnostics) {
    }

    private Normalizer() {
    }

    public static NormalizationResult normalize(List<Segment> segments, HeatFlowSign declaredSign) {
        List<Diagnostic> root = new ArrayList<>();
        List<NormalizedSegment> out = new ArrayList<>();

        if (declaredSign == HeatFlowSign.UNKNOWN || declaredSign == null) {
            root.add(Diagnostic.warn("HEATFLOW_SIGN_UNKNOWN",
                    "仪器输出未声明吸放热方向约定；不得从文件名猜测，全部派生挂起等待人工指定"));
        }

        for (Segment seg : segments) {
            List<Diagnostic> diag = new ArrayList<>(seg.diagnostics());
            List<Point> dropped = new ArrayList<>();
            List<Point> valid = seg.validPoints();

            ScanMode observed = observeMode(valid);
            if (observed != seg.declaredMode() && seg.declaredMode() != ScanMode.UNKNOWN) {
                diag.add(Diagnostic.warn("SCAN_MODE_MISMATCH",
                        "段 " + seg.id() + " 元数据声明 " + seg.declaredMode().token()
                                + "，数据实际走向为 " + observed.token()).withRef(seg.id()));
            }

            List<XY> raw = new ArrayList<>();
            for (Point p : valid) {
                raw.add(new XY(p.temperatureK(), p.heatFlowW()));
            }
            CurveMath.SortedCurve sorted = CurveMath.sortAndDedupe(raw);
            if (!sorted.wasMonotonicInSourceOrder()) {
                diag.add(Diagnostic.warn("NON_MONOTONIC_TEMPERATURE",
                        "段 " + seg.id() + " 存在温度回退/重复点（反向序列），已按温度重排，原始点序未修改")
                        .withRef(seg.id()));
            }

            boolean signFlipped = declaredSign == HeatFlowSign.ENDO_DOWN;
            List<XY> curve = new ArrayList<>();
            for (XY p : sorted.points()) {
                curve.add(new XY(p.x(), signFlipped ? -p.y() : p.y()));
            }

            for (Point p : seg.points()) {
                if (!p.valid()) {
                    dropped.add(p);
                    diag.add(Diagnostic.warn("UNPARSEABLE_POINT_RETAINED",
                            "第 " + p.index() + " 行无法解析，已留在原始输出中且不参与画图/积分: "
                                    + p.parseError()).withRef(seg.id()));
                }
            }
            if (observed == ScanMode.COOLING) {
                diag.add(Diagnostic.info("COOLING_SEGMENT",
                        "段 " + seg.id() + " 为冷却段；吸热向上归一化后放热结晶峰为负向，"
                                + "且不与加热段混用").withRef(seg.id()));
            }
            out.add(new NormalizedSegment(seg.id(), observed, signFlipped, curve, dropped, diag));
            root.addAll(diag);
        }
        return new NormalizationResult(out, root);
    }

    /** 按原始采集顺序对温度做线性拟合，斜率决定真实加热/冷却方向。 */
    public static ScanMode observeMode(List<Point> pointsInAcquisitionOrder) {
        if (pointsInAcquisitionOrder.size() < 2) {
            return ScanMode.UNKNOWN;
        }
        List<XY> seqVsTemp = new ArrayList<>();
        for (Point p : pointsInAcquisitionOrder) {
            seqVsTemp.add(new XY(p.index(), p.temperatureK()));
        }
        double slope = CurveMath.fitLine(seqVsTemp).slope();
        double tol = 1e-9;
        if (slope > tol) {
            return ScanMode.HEATING;
        }
        if (slope < -tol) {
            return ScanMode.COOLING;
        }
        return ScanMode.ISOTHERMAL;
    }
}
