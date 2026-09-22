package dsc.domain;

/**
 * 一个转折的完整特征：onset（前切线与峰侧最大斜率线交点）、peak（极值温度）、
 * endset（后切线交点）。方向未决时 signResolved=false，焓符号不得确定。
 */
public final class TransitionResult {
    public final String transitionId;
    public final String segmentId;
    public final String candidateId;
    public final TransitionType type;

    public final double onsetK;
    public final double peakK;
    public final double endsetK;
    public final double peakAmplitudeWg;
    /** 内部约定下峰的方向：+1 吸热，-1 放热，0 方向未决。 */
    public final int heatSign;
    public final boolean signResolved;
    public final String status;
    public final String diagnostic;

    public TransitionResult(String transitionId, String segmentId, String candidateId, TransitionType type,
                            double onsetK, double peakK, double endsetK, double peakAmplitudeWg,
                            int heatSign, boolean signResolved, String status, String diagnostic) {
        this.transitionId = transitionId;
        this.segmentId = segmentId;
        this.candidateId = candidateId;
        this.type = type;
        this.onsetK = onsetK;
        this.peakK = peakK;
        this.endsetK = endsetK;
        this.peakAmplitudeWg = peakAmplitudeWg;
        this.heatSign = heatSign;
        this.signResolved = signResolved;
        this.status = status;
        this.diagnostic = diagnostic;
    }
}
