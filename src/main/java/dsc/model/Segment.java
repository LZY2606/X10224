package dsc.model;

/** One ramped or isothermal segment of a thermal cycle. Heating and cooling never mix. */
public final class Segment {
    public final String id;
    public final String sampleId;
    public final String cycleId;
    public final String label;
    public final int order;
    public final Types.ScanDirection direction;

    public Segment(String id, String sampleId, String cycleId, String label, int order,
                   Types.ScanDirection direction) {
        this.id = id;
        this.sampleId = sampleId;
        this.cycleId = cycleId;
        this.label = label;
        this.order = order;
        this.direction = direction;
    }
}
