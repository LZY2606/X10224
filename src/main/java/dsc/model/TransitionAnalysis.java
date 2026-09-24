package dsc.model;

import java.util.List;

/**
 * 一次相变分析结果（独立版本）。所有显示值带稳定身份：
 * onset/peak/endset/enthalpy 均绑定 segmentId + baselineId + 输入版本。
 */
public record TransitionAnalysis(
        String id,
        String segmentId,
        String baselineId,
        String massVersionId,
        String eventType,            // glass-transition | crystallization | melting
        double onsetC,
        double peakC,
        double endsetC,
        double enthalpyJPerG,
        List<String> errorSources,   // 焓变误差来源
        List<String> diagnostics,
        String status,               // ok | warning | invalid
        List<String> legalAlternatives // 无法唯一决定时保留的全部合法候选 id
) {
    public TransitionAnalysis {
        errorSources = errorSources == null ? List.of() : List.copyOf(errorSources);
        diagnostics = diagnostics == null ? List.of() : List.copyOf(diagnostics);
        legalAlternatives = legalAlternatives == null ? List.of() : List.copyOf(legalAlternatives);
    }
}
