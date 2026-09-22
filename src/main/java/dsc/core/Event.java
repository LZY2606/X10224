package dsc.core;

import java.time.Instant;
import java.util.Map;

/**
 * 机器可读取事件（事件溯源）。每个事件携带：
 *  - type：导入 / 判定 / 派生 / 裁定(接受/拒绝/调整) / 边界触发 / 冲突
 *  - payload：结构化判断依据
 *  - deps：该事件读取的来源版本（sourceId -> version）与规则版本
 * 结果只追加不改写；回到旧事件恢复当时依赖版本与可见结论。
 */
public final class Event {
    public final long seq;
    public final String eventId;
    public final String type;
    public final Instant at;
    public final String actor;
    public final Map<String, Object> payload;
    public final Map<String, Integer> deps;   // sourceId -> version
    public final String ruleVersion;
    public final String scope;                // 受影响对象范围，如 segment/transition/baseline id

    public Event(long seq, String eventId, String type, Instant at, String actor,
                 Map<String, Object> payload, Map<String, Integer> deps,
                 String ruleVersion, String scope) {
        this.seq = seq;
        this.eventId = eventId;
        this.type = type;
        this.at = at;
        this.actor = actor;
        this.payload = payload;
        this.deps = deps;
        this.ruleVersion = ruleVersion;
        this.scope = scope;
    }
}
