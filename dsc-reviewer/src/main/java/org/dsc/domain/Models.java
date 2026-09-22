package org.dsc.domain;

import java.util.List;

/**
 * 核心领域模型。所有领域量携带单位或坐标基准；原始表示在 SourceRecord.raw 中保留。
 * 本文件只放不可变值对象，派生层（engine 包）负责规范化与计算。
 */
public final class Models {
    private Models() {}

    /** 扫描方向。只能来自仪器头/元数据，禁止按文件名或数据形状猜测。 */
    public enum ScanDirection { HEATING, COOLING, UNKNOWN }

    /**
     * 热流符号约定：ExoUp 表示放热峰向上（y 增大），EndoUp 表示吸热峰向上。
     * 方向判定（吸热/放热）必须基于该仪器约定，而不是文件名。
     */
    public enum HeatFlowConvention { EXO_UP, ENDO_UP, UNKNOWN }

    /** 热事件语义类型。加热段与冷却段各自独立归类，不跨段混用。 */
    public enum TransitionKind {
        GLASS_TRANSITION, CRYSTALLIZATION, MELTING, UNCLASSIFIED
    }

    /** 单个采样点。temperature 单位恒为 K（规范化后），heatFlow 单位恒为 W/g（规范化后）。 */
    public record Point(int index, double timeS, double temperature, double heatFlow) {}

    /**
     * 温度区间（归一化 K 坐标）。区间必须完全落在单一 segment 内，
     * 加热/冷却不混用由 engine 在求值时校验。
     */
    public record TempWindow(double tStartK, double tEndK) {
        public boolean contains(double tK) { return tK >= tStartK && tK <= tEndK; }
        public double widthK() { return tEndK - tStartK; }
    }

    /** 质量值带单位版本，质量修订生成新版本，旧版本不就地覆盖。 */
    public record MassValue(double massMg, String rawText, String unit, int version) {}

    /** 扫描速率，K/min（绝对值），保留原始文本与单位。 */
    public record ScanRate(double rateKPerMin, String rawText, String unit) {}

    /** 气氛条件。 */
    public record Atmosphere(String gas, double flowMlPerMin, String rawText) {}

    /** 温度标定记录（标定温度、样品示值、来源）。 */
    public record TempCalibration(String standard, double refK, double indicatedK, String rawText) {}

    /** 仪器序列：一个加热或冷却段，包含规范化后的点列与其方向元数据。 */
    public record Segment(
            String segmentId,
            int seqNo,
            ScanDirection direction,
            HeatFlowConvention convention,
            ScanRate scanRate,
            List<Point> points,
            boolean nonUniformSpacing,
            List<String> diagnostics
    ) {}

    /** 同一样品履历：样品下可有多个加热/冷却序列（循环段），按 seqNo 排序。 */
    public record SampleHistory(
            String sampleId,
            String sampleName,
            String material,
            List<Segment> segments
    ) {}

    /** 用户选定的切线：两个锚定区间（在某一 segment 内），用于线性基线。 */
    public record TangentSelection(
            String segmentId,
            TempWindow leftWindow,
            TempWindow rightWindow,
            String rationale,
            int version
    ) {}

    /** 基线候选（多个合理基线并列保留，直到人工裁定）。 */
    public record BaselineCandidate(
            String baselineId,
            String eventId,
            String segmentId,
            TempWindow window,
            String method,          // LINEAR_TANGENT / LINEAR_TWO_POINT / CONSTANT
            double slope,           // (W/g)/K
            double intercept,       // W/g @ 0K
            double rmse,
            int anchorPointCount,
            boolean accepted
    ) {
        public double valueAt(double tK) { return slope * tK + intercept; }
    }

    /** 相变事件结果：onset/peak/endset 与焓变及其误差来源。 */
    public record TransitionResult(
            String resultId,
            String eventId,
            String segmentId,
            String baselineId,
            TransitionKind kind,
            double onsetK,
            double peakK,
            double endsetK,
            double enthalpyJg,
            double enthalpyUncertaintyJg,
            List<String> errorSources,
            List<String> diagnostics
    ) {}

    /** 派生状态标记：质量修订后归一化派生过期。 */
    public enum DerivationStatus { CURRENT, STALE, FAILED }

    /** 无法转换/规范化时留在原位置的诊断值。 */
    public record ParseDiagnostic(
            String location,
            String rawValue,
            String expectedUnit,
            String reason
    ) {}
}
