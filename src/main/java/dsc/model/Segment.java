package dsc.model;

public record Segment(
        String segmentId,
        int ordinal,
        String label,
        String mode,
        String cycleTag,
        String blobId,
        String introducedAtEvent
) {
    public static final String HEATING = "heating";
    public static final String COOLING = "cooling";
}
