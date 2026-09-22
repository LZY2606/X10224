package dsc.core;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 机器可读取的审阅事件。判断依据写进 basis 字段，来源指针写进 refs。
 * 事件只增不改；回到旧事件 = 把事件流截断到该 seq 后重放。
 */
public final class ProjectEvent {
    public final long seq;
    public final String type;
    public final String actor;
    public final String basis;
    public final Map<String, Object> payload;
    public final String timestamp;

    public ProjectEvent(long seq, String type, String actor, String basis,
                        Map<String, Object> payload, String timestamp) {
        this.seq = seq;
        this.type = type;
        this.actor = actor;
        this.basis = basis;
        this.payload = payload == null ? Map.of() : Map.copyOf(payload);
        this.timestamp = timestamp;
    }

    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("seq", seq);
        m.put("type", type);
        m.put("actor", actor);
        m.put("basis", basis);
        m.put("payload", payload);
        m.put("timestamp", timestamp);
        return m;
    }

    // ---- 事件类型（机器可读） ----
    public static final String SOURCE_ARCHIVED = "SOURCE_ARCHIVED";
    public static final String MASS_REVISED = "MASS_REVISED";
    public static final String DERIVATION_STARTED = "DERIVATION_STARTED";
    public static final String DERIVATION_COMPLETED = "DERIVATION_COMPLETED";
    public static final String DERIVATION_FAILED = "DERIVATION_FAILED";
    public static final String STALE_PROPAGATED = "STALE_PROPAGATED";
    public static final String BASELINE_PROPOSED = "BASELINE_PROPOSED";
    public static final String CANDIDATE_ACCEPTED = "CANDIDATE_ACCEPTED";
    public static final String CANDIDATE_REJECTED = "CANDIDATE_REJECTED";
    public static final String CONFLICTING_DECISION = "CONFLICTING_DECISION";
    public static final String INTERVAL_ADJUSTED = "INTERVAL_ADJUSTED";
    public static final String SEGMENT_SWITCH = "SEGMENT_SWITCH";
    public static final String BOUNDARY_TRIGGERED = "BOUNDARY_TRIGGERED";
}
