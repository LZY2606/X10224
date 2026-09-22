package dsc.domain;

/**
 * 一条可重算基线。前后切线各自做最小二乘直线；在 [onset,endset] 之间以连接
 * 两端点的直线作为基线（玻璃化台阶同理，台阶前后两条切线之间按标准外推）。
 *
 * 多个合理基线并存（并列基线）：每个结果有稳定 candidateId，采纳状态由
 * 人工决定事件单独记录，计算结果本身不可变、不互相覆盖。
 */
public final class BaselineResult {
    public final String candidateId;
    public final String segmentId;
    public final String tangentId;
    public final TransitionType type;

    public final double preSlope;   // W/g per K
    public final double preIntercept;
    public final double postSlope;
    public final double postIntercept;
    public final double onsetK;
    public final double endsetK;
    public final double integrationStartK;
    public final double integrationEndK;
    public final double onsetY;
    public final double endsetY;
    public final double midpointK;
    public final double stepHeightWg; // 玻璃化台阶高（峰基线为 0 参考线差不使用）
    public final String status;       // OK | AMBIGUOUS | ERROR
    public final String diagnostic;

    public BaselineResult(String candidateId, String segmentId, String tangentId, TransitionType type,
                          double preSlope, double preIntercept, double postSlope, double postIntercept,
                          double onsetK, double endsetK, double onsetY, double endsetY,
                          double midpointK, double stepHeightWg,
                          double integrationStartK, double integrationEndK,
                          String status, String diagnostic) {
        this.candidateId = candidateId;
        this.segmentId = segmentId;
        this.tangentId = tangentId;
        this.type = type;
        this.preSlope = preSlope;
        this.preIntercept = preIntercept;
        this.postSlope = postSlope;
        this.postIntercept = postIntercept;
        this.onsetK = onsetK;
        this.endsetK = endsetK;
        this.integrationStartK = integrationStartK;
        this.integrationEndK = integrationEndK;
        this.onsetY = onsetY;
        this.endsetY = endsetY;
        this.midpointK = midpointK;
        this.stepHeightWg = stepHeightWg;
        this.status = status;
        this.diagnostic = diagnostic;
    }

    /** 基线在任意温度处的值：区间内为 onset→endset 连接线，区间外用对应切线外推。 */
    public double integrationLowK() {
        return Math.min(integrationStartK, integrationEndK);
    }

    public double integrationHighK() {
        return Math.max(integrationStartK, integrationEndK);
    }

    public double baselineAt(double tempK) {
        if (tempK < onsetK) {
            return preSlope * tempK + preIntercept;
        }
        if (tempK > endsetK) {
            return postSlope * tempK + postIntercept;
        }
        double frac = endsetK == onsetK ? 0 : (tempK - onsetK) / (endsetK - onsetK);
        return onsetY + frac * (endsetY - onsetY);
    }

    public boolean usable() {
        return !"ERROR".equals(status);
    }
}
