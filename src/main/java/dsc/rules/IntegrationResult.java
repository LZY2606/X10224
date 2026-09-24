package dsc.rules;

import java.util.List;

/**
 * 一次相变积分。status=INVALID 时 enthalpyJPerG 为 null（空区间等），
 * 诊断与误差来源仍给出。
 */
public record IntegrationResult(String transitionId, String intervalId, String segmentId,
                                String eventClass, String thermalSign,
                                Double enthalpyJPerG, Double cpShiftJPerGK,
                                List<Feature> features,
                                List<ErrorSource> errorSources,
                                double temperatureUncertaintyK,
                                List<String> diagnostics,
                                String ruleVersion,
                                String baselineCandidateId,
                                String massVersion) {
}
