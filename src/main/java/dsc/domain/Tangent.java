package dsc.domain;

/**
 * User-selected tangent: two endpoints on one segment's curve, given by
 * temperature coordinates (degC basis). Tangents are kept as separate sources.
 */
public final class Tangent {
    public final String id;
    public final String segmentId;
    public final double t1, y1, t2, y2;
    public final String note;

    public Tangent(String id, String segmentId, double t1, double y1, double t2, double y2, String note) {
        this.id = id;
        this.segmentId = segmentId;
        this.t1 = t1; this.y1 = y1; this.t2 = t2; this.y2 = y2;
        this.note = note;
    }

    public double slope() { return (y2 - y1) / (t2 - t1); }
    public double intercept() { return y1 - slope() * t1; }
    public double valueAt(double t) { return slope() * t + intercept(); }
}
