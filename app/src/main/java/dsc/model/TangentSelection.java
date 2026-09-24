package dsc.model;

/**
 * A user-selected tangent window, archived separately from instrument outputs.
 * Onset/endset tangents are fitted in [t1,t2] and [t3,t4] (°C, normalized).
 */
public final class TangentSelection {
    private final String tangentId;
    private final String segmentId;
    private final double onsetT1;
    private final double onsetT2;
    private final double endsetT1;
    private final double endsetT2;
    private final String note;

    public TangentSelection(String tangentId, String segmentId,
                            double onsetT1, double onsetT2, double endsetT1, double endsetT2,
                            String note) {
        this.tangentId = tangentId;
        this.segmentId = segmentId;
        this.onsetT1 = onsetT1;
        this.onsetT2 = onsetT2;
        this.endsetT1 = endsetT1;
        this.endsetT2 = endsetT2;
        this.note = note == null ? "" : note;
    }

    public String tangentId() { return tangentId; }
    public String segmentId() { return segmentId; }
    public double onsetT1() { return onsetT1; }
    public double onsetT2() { return onsetT2; }
    public double endsetT1() { return endsetT1; }
    public double endsetT2() { return endsetT2; }
    public String note() { return note; }
}
