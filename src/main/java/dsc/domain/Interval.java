package dsc.domain;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * A temperature window selected by the reviewer (draggable on the curve page).
 * Coordinates are stored in the canonical temperature basis (degC), while {@link #basis}
 * records the coordinate frame used by the UI so values are never silently re-interpreted.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public final class Interval {
    public String id;
    public String segmentId;
    public String eventType;
    public double fromT;
    public double toT;
    public String basis = Unit.CELSIUS;
    public String createdByEvent;

    public Interval() {
    }

    public Interval(String id, String segmentId, String eventType,
                    double fromT, double toT, String createdByEvent) {
        this.id = id;
        this.segmentId = segmentId;
        this.eventType = eventType;
        this.fromT = Math.min(fromT, toT);
        this.toT = Math.max(fromT, toT);
        this.createdByEvent = createdByEvent;
    }

    public boolean isEmpty() {
        return toT <= fromT;
    }
}
