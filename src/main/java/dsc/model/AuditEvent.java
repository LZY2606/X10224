package dsc.model;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * 机器可读取的审计事件。pointerSnapshot 记录事件应用后的当前指针全集，
 * 回到旧事件时恢复当时依赖版本与可见结论，而不是用最新结果覆盖历史。
 */
public record AuditEvent(
        long seq,
        String id,
        String type,
        String actor,
        String comment,
        JsonNode payload,
        JsonNode pointerSnapshot,
        String at,
        String idempotencyKey) {
}
