package dsc.domain;

/** One scan segment within a sample history. Heating and cooling never share an interval. */
public class Segment {
    public String id;
    public String sampleId;
    public String cycleId;
    public int order;          // acquisition order within the history
    public Enums.SegmentRole role;
    public Enums.Direction declaredDirection; // from metadata, never the file name
    public Enums.SignConvention signConvention;
    public String tUnit;
    public String hfUnit;
    public double tStartRaw, tEndRaw;
    public int pointCount;
    public String instrumentOutputId; // source-record id (version-independent)
    public long createdAt;

    public boolean heating() { return declaredDirection == Enums.Direction.HEATING; }
    public boolean cooling() { return declaredDirection == Enums.Direction.COOLING; }
}
