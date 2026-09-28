package dsc.domain;

/** Analysis window in temperature coordinates, scoped to one segment. */
public record Interval(String segmentId, double fromT, double toT) {
    public boolean isEmpty() { return toT <= fromT; }
    public boolean contains(double t) { return t >= fromT && t <= toT; }
    public boolean overlaps(Interval o) {
        return segmentId.equals(o.segmentId) && !(toT <= o.fromT || o.toT <= fromT);
    }
}
