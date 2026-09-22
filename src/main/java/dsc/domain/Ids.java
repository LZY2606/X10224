package dsc.domain;

/**
 * 稳定身份：语义型 id 由内容槽位推导，删除数据库再导入后仍保持。
 * 版本通过 {@code v<n>} 区分；同一语义槽位的不同版本 id 相同。
 */
public final class Ids {

    private Ids() {
    }

    public static String source(String sampleId, String runId, SourceKind kind) {
        return "src:" + sampleId + ":" + (runId == null ? "-" : runId) + ":" + kind.code();
    }

    public static String feature(String runId, FeatureKind kind, int rank) {
        return "feat:" + runId + ":" + kind.code() + ":" + rank;
    }

    public static String candidate(String featureId, String method, int index) {
        return "cand:" + featureId.replace("feat:", "") + ":" + method + ":" + index;
    }

    public static String metric(String featureId, String metric) {
        return "metric:" + featureId.replace("feat:", "") + ":" + metric;
    }

    public static String dep(String featureId, String facet) {
        return featureId + "#" + facet;
    }

    public static String run(String sampleId, String sequence) {
        return "run:" + sampleId + ":" + sequence;
    }
}
