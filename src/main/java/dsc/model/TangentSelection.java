package dsc.model;

public record TangentSelection(
        String id,
        String intervalId,
        String side,
        double tStartK,
        double tEndK,
        int version,
        String eventId
) {
    public static final String LEFT = "left";
    public static final String RIGHT = "right";
}
