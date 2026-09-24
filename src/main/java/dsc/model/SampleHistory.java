package dsc.model;

import java.util.ArrayList;
import java.util.List;

/** 同一样品履历：加热与冷却段（可跨循环）放在一起，但分析时不跨段混用。 */
public final class SampleHistory {
    private final String id;
    private final String name;
    private final List<Segment> segments = new ArrayList<>();

    public SampleHistory(String id, String name) {
        this.id = id;
        this.name = name;
    }

    public String id() { return id; }
    public String name() { return name; }
    public List<Segment> segments() { return segments; }

    public Segment segment(String id) {
        for (Segment s : segments) if (s.id().equals(id)) return s;
        return null;
    }
}
