package dsc.domain;

/** Scan direction is read from instrument metadata, never inferred from file names. */
public enum Direction {
    HEATING("加热"), COOLING("冷却");
    public final String label;
    Direction(String label) { this.label = label; }
}
