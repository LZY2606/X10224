package dsc.domain;

import java.util.Map;

/**
 * 事件日志中的一条机器可读取事件。
 * 判定依据（吸放热方向、扫描段隔离、基线多解、质量过期等）都落为结构化事件。
 */
public record Event(
        long seq,
        String type,
        String actor,
        String branch,
        long baseSeq,
        String targetId,
        String runId,
        String featureId,
        String judgeCode,
        String summary,
        Map<String, String> payload,
        String status
) {
    public static final String STATUS_APPLIED = "applied";
    public static final String STATUS_REJECTED = "rejected";
    public static final String STATUS_CONFLICT = "conflict";
    public static final String STATUS_DEFERRED = "deferred";
}
