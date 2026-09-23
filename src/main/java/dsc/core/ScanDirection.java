package dsc.core;

/** Declared thermal direction of a segment. Determined by metadata, never by file name. */
public enum ScanDirection {
    HEATING("heating"),
    COOLING("cooling"),
    MIXED("mixed"),
    UNKNOWN("unknown");

    private final String token;
    ScanDirection(String token) { this.token = token; }
    public String token() { return token; }

    public static ScanDirection fromToken(String raw) {
        if (raw == null) return UNKNOWN;
        return switch (raw.trim().toLowerCase()) {
            case "heating", "heat", "up" -> HEATING;
            case "cooling", "cool", "down" -> COOLING;
            case "mixed" -> MIXED;
            default -> UNKNOWN;
        };
    }
}
