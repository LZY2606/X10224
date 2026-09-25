package dsc.store;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * 事件日志行。事件是机器可读的反例档案载体：
 * 每个判定（方向、分段隔离、多基线保留、质量换版过期、空区间等）都落为一条事件。
 */
public record StoredEvent(long eventId,
                          String historyId,
                          String eventType,
                          String actor,
                          String createdAt,
                          JsonNode payload,
                          Long parentEventId) {
}
