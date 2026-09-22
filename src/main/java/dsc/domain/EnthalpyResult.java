package dsc.domain;

import java.util.List;

/**
 * 焓变积分结果。对非等间隔温度使用基于时间的梯形积分：
 *   ΔH = ∫ (y − baseline) dt，时间由相邻温度差/扫描速率得到；
 * 速率按段扫描方向取绝对值。结果单位 J/g。
 *
 * 误差来源逐项保留（不只给一个合成不确定度），界面显示“焓变误差来源”。
 */
public final class EnthalpyResult {
    public final String integrationId;
    public final String transitionId;
    public final String candidateId;
    public final String segmentId;

    public final double onsetK;
    public final double endsetK;
    public final double enthalpyJg;     // 已按方向给符号；方向未决时为 NaN
    public final double magnitudeJg;   // 绝对量
    public final boolean signResolved;
    public final String heatDirection; // ENDOTHERMIC | EXOTHERMIC | UNRESOLVED
    public final List<ErrorContribution> errorSources;
    public final String status;        // OK | EMPTY_INTERVAL | CROSSES_SEGMENTS | MASS_MISSING | ERROR
    public final String diagnostic;

    public static final class ErrorContribution {
        public final String code;
        public final String label;
        public final double contributionJg;
        public final String basis;

        public ErrorContribution(String code, String label, double contributionJg, String basis) {
            this.code = code;
            this.label = label;
            this.contributionJg = contributionJg;
            this.basis = basis;
        }
    }

    public EnthalpyResult(String integrationId, String transitionId, String candidateId, String segmentId,
                          double onsetK, double endsetK, double enthalpyJg, double magnitudeJg,
                          boolean signResolved, String heatDirection, List<ErrorContribution> errorSources,
                          String status, String diagnostic) {
        this.integrationId = integrationId;
        this.transitionId = transitionId;
        this.candidateId = candidateId;
        this.segmentId = segmentId;
        this.onsetK = onsetK;
        this.endsetK = endsetK;
        this.enthalpyJg = enthalpyJg;
        this.magnitudeJg = magnitudeJg;
        this.signResolved = signResolved;
        this.heatDirection = heatDirection;
        this.errorSources = List.copyOf(errorSources);
        this.status = status;
        this.diagnostic = diagnostic;
    }
}
