package dsc.model;

import java.util.List;

/**
 * One proposed baseline under a specific integration window for one
 * segment. Several candidates may be simultaneously legitimate; all are
 * retained with their differences, the adopted one merely carries
 * {@link Status#ADOPTED}.
 */
public record BaselineCandidate(
        String id,
        String studyId,
        String segmentId,
        int version,
        Method method,
        double leftTK,
        double rightTK,
        double leftHF,
        double rightHF,
        double slopeWPerK,
        double interceptW,
        Status status,
        String decidedBy,
        List<String> diagnostics,
        List<String> dependsOn,
        int createdSeq,
        int decidedSeq
) {

    public enum Method {
        /** Straight line joining the two window edges. */
        CHORD("端点连线"),
        /** Two fitted tangents (pre/post event); the reviewer sets the anchors. */
        TWO_TANGENT("双切线"),
        /** Step baseline for a glass transition (pre/post glass levels). */
        STEP("台阶基线");

        private final String label;

        Method(String label) { this.label = label; }

        public String label() { return label; }
    }

    public enum Status {
        PROPOSED("候选"),
        ADOPTED("采纳"),
        NOT_ADOPTED("未采纳"),
        SUPERSEDED("已被新版替代"),
        REJECTED("否定");

        private final String label;

        Status(String label) { this.label = label; }

        public String label() { return label; }
    }

    /** Heat flow of the baseline line at temperature t (K). */
    public double baselineAt(double tK) {
        return slopeWPerK * tK + interceptW;
    }

    public BaselineCandidate with(Status newStatus, String who, int seq) {
        return new BaselineCandidate(id, studyId, segmentId, version, method, leftTK, rightTK,
                leftHF, rightHF, slopeWPerK, interceptW, newStatus, who, diagnostics,
                dependsOn, createdSeq, seq);
    }

    public BaselineCandidate withVersion(int v) {
        return new BaselineCandidate(id, studyId, segmentId, v, method, leftTK, rightTK,
                leftHF, rightHF, slopeWPerK, interceptW, status, decidedBy, diagnostics,
                dependsOn, createdSeq, decidedSeq);
    }
}
