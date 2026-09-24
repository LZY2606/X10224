package dsc.domain;

/**
 * A user-draggable analysis interval bound to exactly one segment (heating and cooling are never
 * mixed inside an interval). Dragging creates a new version; prior versions remain on the branch.
 */
public class Interval {
    public String id;
    public String sampleId;
    public String branchId;
    public String segmentId;
    public int version;
    public boolean current;
    public double tStartC, tEndC;     // normalized frame, °C
    public double tStartRaw, tEndRaw; // original representation preserved
    public String rawUnit;
    public String label;
    public long createdAt;
    public String createdBy;
}
