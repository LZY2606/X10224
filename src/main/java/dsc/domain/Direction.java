package dsc.domain;

import java.util.Locale;

/** Heating/cooling direction. Derived from explicit metadata, never from a file name. */
public enum Direction {
    HEATING, COOLING, UNKNOWN;

    public static Direction parse(String raw) {
        if (raw == null) return UNKNOWN;
        String s = raw.trim().toLowerCase(Locale.ROOT);
        return switch (s) {
            case "heating", "heat", "up", "升温" -> HEATING;
            case "cooling", "cool", "down", "降温" -> COOLING;
            default -> UNKNOWN;
        };
    }
}
