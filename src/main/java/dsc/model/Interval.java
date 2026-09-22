package dsc.model;

public record Interval(
        String intervalId,
        String segmentId,
        double startK,
        double endK,
        String kind,
        int version,
        String createdByEvent,
        String supersededByEvent
) {
    public static final String GLASS = "glass";
    public static final String PEAK = "peak";
}
