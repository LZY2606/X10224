package dsc.domain;

/**
 * Thermal direction, derived from measured data (temperature vs. time),
 * never inferred from a file name.
 */
public enum Direction {
    HEATING("heating"),
    COOLING("cooling"),
    UNKNOWN("unknown");

    private final String code;

    Direction(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }

    public static Direction fromCode(String code) {
        for (Direction d : values()) {
            if (d.code.equalsIgnoreCase(code)) {
                return d;
            }
        }
        return UNKNOWN;
    }
}
