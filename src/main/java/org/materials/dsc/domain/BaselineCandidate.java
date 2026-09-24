package org.materials.dsc.domain;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * One legitimate baseline option for an event.  Several mathematically
 * reasonable baselines may coexist; the system keeps their differences and
 * must never collapse them to a single answer.  A candidate becomes current
 * only by an explicit decision; rejected/unselected candidates stay stored.
 */
public final class BaselineCandidate {
    public String id;
    public String eventId;
    public String method;
    public String label;
    /** line fit y = slope*x + intercept in (°C, W) over the integrated window */
    public double slope;
    public double intercept;
    public double fitRmsResidualW;
    /** degrees of freedom / anchor point counts used for fit */
    public int preAnchorCount;
    public int postAnchorCount;
    public String sourceTangentId;
    public boolean selected;
    public String statusReason = "";
    /** ordered list of upstream source ids this candidate was computed from */
    public final List<String> inputs = new ArrayList<>();

    public double valueAt(double tempC) {
        return slope * tempC + intercept;
    }

    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("eventId", eventId);
        m.put("method", method);
        m.put("label", label);
        m.put("slope", slope);
        m.put("intercept", intercept);
        m.put("fitRmsResidualW", fitRmsResidualW);
        m.put("preAnchorCount", preAnchorCount);
        m.put("postAnchorCount", postAnchorCount);
        m.put("sourceTangentId", sourceTangentId);
        m.put("selected", selected);
        m.put("statusReason", statusReason);
        m.put("inputs", inputs);
        return m;
    }
}
