package dsc.model;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 追加式事件。身份由 (sampleId, seq, type, key) 稳定派生。
 * payload 保留输入片段；basis 保存机器可读取的判断依据；
 * affects 列出受影响对象（段/窗口/派生节点）。
 */
public final class Event {
    public String eventId;
    public int seq;
    public String sampleId;
    public String type;
    public String actor;
    public String createdAt;
    public String baseRevision;            // 乐观并发：基于哪个 source revision
    public Map<String, Object> payload = new LinkedHashMap<>();
    public Map<String, Object> basis = new LinkedHashMap<>();
    public java.util.List<String> affects = new java.util.ArrayList<>();

    public Event() {}

    public String key() {
        return sampleId + "#" + seq + "#" + type + "#" + String.valueOf(payload.get("key"));
    }
}
