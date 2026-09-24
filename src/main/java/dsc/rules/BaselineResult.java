package dsc.rules;

import java.util.List;

/**
 * 一个区间上的基线集合（可能多解）。
 * selectedCandidateId 为 null 表示尚无人工选择——保留全部合法候选。
 */
public record BaselineResult(String intervalId, String segmentId,
                             double t0K, double t1K,
                             List<BaselineCandidate> candidates,
                             String selectedCandidateId,
                             List<String> diagnostics,
                             String ruleVersion) {
}
