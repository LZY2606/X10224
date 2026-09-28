package dsc.domain;

/**
 * One segment of one sample's thermal history. Heating and cooling segments
 * are stored separately and never mixed during derivation.
 */
public final class Segment {
    public final String id;
    public final String historyId;
    public final int order;
    public final Direction direction;
    public final Quantity scanRate;
    public final String atmosphere;

    public Segment(String id, String historyId, int order, Direction direction,
                  Quantity scanRate, String atmosphere) {
        this.id = id;
        this.historyId = historyId;
        this.order = order;
        this.direction = direction;
        this.scanRate = scanRate;
        this.atmosphere = atmosphere;
    }
}
