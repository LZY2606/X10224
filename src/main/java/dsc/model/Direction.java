package dsc.model;

/**
 * Segment scan direction. Heating and cooling segments must never be mixed
 * inside one integration or baseline window.
 */
public enum Direction {
    HEATING, COOLING;

    public Direction opposite() {
        return this == HEATING ? COOLING : HEATING;
    }

    public static Direction parse(String raw) {
        if (raw == null) {
            throw new IllegalArgumentException("missing scan direction");
        }
        String s = raw.trim().toLowerCase();
        // Only explicit instrument metadata decides the direction; file names
        // such as "cooling_run_2.xlsx" are deliberately not consulted.
        switch (s) {
            case "heating":
            case "heat":
            case "up":
            case "+":
                return HEATING;
            case "cooling":
            case "cool":
            case "down":
            case "-":
                return COOLING;
            default:
                throw new IllegalArgumentException("unrecognised scan direction: " + raw);
        }
    }
}
