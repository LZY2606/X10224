package dsc.model;

/** Thermal direction of a scan segment. Derived from data, never from a file name. */
public enum Direction {
    HEATING("heating"),
    COOLING("cooling"),
    NEUTRAL("isothermal");

    private final String label;
    Direction(String label) { this.label = label; }
    public String label() { return label; }

    public static Direction fromSlope(double dT) {
        if (dT > 0) return HEATING;
        if (dT < 0) return COOLING;
        return NEUTRAL;
    }
}
