package dsc.domain;

import java.util.List;
import java.util.Map;

/**
 * 某个事件（历史版本）下的完整可见结论：来源、扫描段、转折、判定、候选。
 * 回到旧事件时恢复当时依赖版本和可见结论，而不是用最新结果覆盖历史。
 */
public record ViewState(
        long atEventSeq,
        String branch,
        List<SourceDoc> sources,
        List<RunView> runs,
        List<Event> events,
        List<String> openConflicts,
        List<DiagnosticView> diagnostics
) {

    public record RunView(
            String runId,
            String sequence,
            String declaredDirection,
            String signConvention,
            List<ScanSegment> segments,
            double[][] points,
            List<Feature> features,
            List<String> diagnostics
    ) {
    }

    public record DiagnosticView(String runId, String featureId, String code,
                                 String severity, String message, String sourceChain) {
    }

    public Feature findFeature(String featureId) {
        for (RunView run : runs) {
            for (Feature feature : run.features()) {
                if (feature.id().equals(featureId)) {
                    return feature;
                }
            }
        }
        return null;
    }

    public Map<String, String> sourceChainOf(String featureId) {
        return Map.of();
    }
}
