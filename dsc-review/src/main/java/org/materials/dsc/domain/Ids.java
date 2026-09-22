package org.materials.dsc.domain;

/**
 * 稳定身份：所有显示值都有确定的、与内容/父级相关的 ID。
 * 导出→删除本地数据库→导入后身份不变，因此顺序与来源关系可以重建。
 */
public final class Ids {

    private Ids() {
    }

    public static String runId(String sampleId, String fileName) {
        return "run-" + sampleId + "-" + Digest.shortHash(fileName);
    }

    public static String segmentId(String runId, int cycle, String direction) {
        return "seg-" + runId + "-c" + cycle + "-" + direction;
    }

    public static String windowId(String segmentId, int seq) {
        return "win-" + segmentId + "-" + seq;
    }

    public static String transitionId(String windowId) {
        return "tr-" + windowId;
    }

    public static String baselineCandidateId(String windowId, String modelKey) {
        return "bc-" + windowId + "-" + modelKey;
    }

    public static String massId(String sampleId, int seq) {
        return "mass-" + sampleId + "-v" + seq;
    }

    public static String calibrationId(String runId, int seq) {
        return "cal-" + runId + "-v" + seq;
    }

    public static String tangentId(String segmentId, int seq) {
        return "tan-" + segmentId + "-" + seq;
    }
}
