package dsc.domain;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 不可变事件。每个事件都有稳定身份 seq（存储分配，全序）+ id（内容稳定的业务身份）。
 * 判定依据以 reasons 写成机器可读取结构（judgment 事件）。
 */
public record Event(
        long seq,
        String id,
        String type,
        String parentId,
        String baseVersion,
        String author,
        String timestamp,
        Map<String, Object> payload,
        Map<String, Object> reasons
) {
    public Event {
        payload = payload == null ? Map.of() : new LinkedHashMap<>(payload);
        reasons = reasons == null ? Map.of() : new LinkedHashMap<>(reasons);
    }

    public static Event create(long seq, String id, String type, String parentId, String baseVersion,
                               String author, String timestamp,
                               Map<String, Object> payload, Map<String, Object> reasons) {
        return new Event(seq, id, type, parentId, baseVersion, author, timestamp, payload, reasons);
    }

    @SuppressWarnings("unchecked")
    public String str(String key) {
        Object v = payload.get(key);
        return v == null ? null : v.toString();
    }

    public double num(String key) {
        Object v = payload.get(key);
        if (v instanceof Number n) {
            return n.doubleValue();
        }
        return v == null ? Double.NaN : Double.parseDouble(v.toString());
    }
}
