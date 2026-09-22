package org.materials.dsc.domain;

import java.util.List;

/**
 * 一个热转折（玻璃化/结晶/熔融）。全部落在单一加热或冷却段内。
 * onset/endset 用切线相交法；重叠转折时 overlap=true，基线多解全部保留。
 */
public record PhaseTransition(
        String id,
        String windowId,
        String segmentId,
        String label,
        String family,
        double onsetKelvin,
        double peakKelvin,
        double endsetKelvin,
        List<BaselineCandidate> baselineCandidates,
        EnthalpyResult enthalpy,
        boolean overlap,
        List<String> diagnostics,
        String status) {

    /** 当前被采纳的基线候选（可能没有——多解未裁定时为 null）。 */
    public BaselineCandidate acceptedCandidate() {
        return baselineCandidates.stream().filter(BaselineCandidate::accepted).findFirst().orElse(null);
    }
}
