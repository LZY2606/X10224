package org.materials.dsc.domain;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A glass transition, crystallization or melt inside one segment.  Events
 * belong to exactly one directed segment; overlapping events are flagged but
 * never silently merged.  All candidate baselines and their integrations are
 * retained alongside the chosen one.
 */
public final class ThermalEvent {
    public String id;
    public String segmentId;
    public String name;
    public ThermalEventType type = ThermalEventType.UNKNOWN;
    public Interval window;
    public boolean overlapping;
    public String overlapWith = "";
    public String status = "provisional"; // provisional | confirmed | rejected
    public final List<BaselineCandidate> baselines = new ArrayList<>();
    public String selectedBaselineId;
    public IntegrationResult currentIntegration;

    public ThermalEvent() {
    }

    public ThermalEvent(String id, String segmentId, String name,
                        ThermalEventType type, Interval window) {
        this.id = id;
        this.segmentId = segmentId;
        this.name = name;
        this.type = type;
        this.window = window;
    }

    public BaselineCandidate selected() {
        for (BaselineCandidate b : baselines) {
            if (b.id.equals(selectedBaselineId)) {
                return b;
            }
        }
        return null;
    }

    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("segmentId", segmentId);
        m.put("name", name);
        m.put("type", type.code());
        m.put("windowStartC", window.startC);
        m.put("windowEndC", window.endC);
        m.put("overlapping", overlapping);
        m.put("overlapWith", overlapWith);
        m.put("status", status);
        m.put("selectedBaselineId", selectedBaselineId);
        List<Map<String, Object>> bl = new ArrayList<>();
        for (BaselineCandidate b : baselines) {
            bl.add(b.toMap());
        }
        m.put("baselines", bl);
        m.put("currentIntegration", currentIntegration == null ? null : currentIntegration.toMap());
        return m;
    }
}
