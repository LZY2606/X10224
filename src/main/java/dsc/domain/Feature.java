package dsc.domain;

import java.util.List;

/**
 * 一次相变/热转折结果：玻璃化、结晶或熔融。
 * 带稳定身份、所用扫描段、onset/peak/endset、并列基线候选与焓变。
 */
public record Feature(
        String id,
        String runId,
        FeatureKind kind,
        int rank,
        int segmentIndex,
        boolean heating,
        double onsetT,
        double peakT,
        double endsetT,
        double peakDeviation,
        double onsetPower,
        double peakPower,
        double endsetPower,
        List<BaselineCandidate> baselines,
        String adoptedBaselineId,
        EnthalpyMetric enthalpy,
        String status,
        List<String> diagnostics,
        long computedAtEvent
) {

    public Feature withEnthalpy(EnthalpyMetric metric) {
        return new Feature(id, runId, kind, rank, segmentIndex, heating,
                onsetT, peakT, endsetT, peakDeviation, onsetPower, peakPower, endsetPower,
                baselines, adoptedBaselineId, metric, status, diagnostics, computedAtEvent);
    }

    public Feature withBaselines(List<BaselineCandidate> candidates, String adoptedId) {
        return new Feature(id, runId, kind, rank, segmentIndex, heating,
                onsetT, peakT, endsetT, peakDeviation, onsetPower, peakPower, endsetPower,
                candidates, adoptedId, enthalpy, status, diagnostics, computedAtEvent);
    }
}
