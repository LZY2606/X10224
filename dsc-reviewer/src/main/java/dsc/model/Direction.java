package dsc.model;

/** Segment direction is declared metadata; it is never inferred from a file name. */
public enum Direction {
    HEATING, COOLING;

    public static Direction parse(String s) {
        if (s == null) {
            throw new IllegalArgumentException("missing scan direction");
        }
        return switch (s.trim().toLowerCase(java.util.Locale.ROOT)) {
            case "heating", "heat", "up" -> HEATING;
            case "cooling", "cool", "down" -> COOLING;
            default -> throw new IllegalArgumentException("unknown scan direction: " + s);
        };
    }
}
