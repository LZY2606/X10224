package org.materials.dsc.domain;

/**
 * One leg of a thermal cycle (e.g. "heat #1", "cool #1", "heat #2").
 * Direction is determined from the data axis, not the name; heating and
 * cooling are never analyzed as one merged sequence.
 */
public final class Segment {
    public String id;
    public String historyId;
    public String ordinal;
    public String label;
    public String cycleTag;
    public ScanDirection declaredDirection;

    public Segment(String id, String historyId, String ordinal, String label,
                   String cycleTag, ScanDirection declaredDirection) {
        this.id = id;
        this.historyId = historyId;
        this.ordinal = ordinal;
        this.label = label;
        this.cycleTag = cycleTag;
        this.declaredDirection = declaredDirection;
    }
}
