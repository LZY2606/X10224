package dsc.model;

import java.util.List;

/**
 * 一条基线候选。多个合理基线必须并列保留差异，
 * 一次确认不能变成不可逆覆盖：adopted 只切换指针，未选候选保留。
 * 基线建立在归一化曲线上，因此绑定 massVersionId；
 * 质量修订后该候选及其派生分析全部过期。
 */
public record BaselineCandidate(
        String id,
        String type,                 // linear | tangent | step
        String segmentId,
        String massVersionId,
        double intervalStartC,
        double intervalEndC,
        List<Double> params,         // type 相关参数（如 step 的分界温度）
        List<Double> baselineHf,     // 与段内点一一对应的基线热流（mW/mg，归一化）
        List<Double> tangentAnchors, // 用户选定切线的锚点温度（°C）
        List<String> diagnostics,
        boolean adopted
) {
    public BaselineCandidate {
        params = params == null ? List.of() : List.copyOf(params);
        baselineHf = baselineHf == null ? List.of() : List.copyOf(baselineHf);
        tangentAnchors = tangentAnchors == null ? List.of() : List.copyOf(tangentAnchors);
        diagnostics = diagnostics == null ? List.of() : List.copyOf(diagnostics);
    }
}
