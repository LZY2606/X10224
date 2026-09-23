package dsc.model;

/**
 * Thermal direction of a scan segment. It must be decided from measured
 * data / instrument metadata — never from a file name.
 */
public enum Direction {
    HEATING("加热"),
    COOLING("冷却"),
    /** Direction could not be uniquely decided: the segment stays quarantined. */
    AMBIGUOUS("方向未定");

    private final String label;

    Direction(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
