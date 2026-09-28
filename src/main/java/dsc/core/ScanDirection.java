package dsc.core;

/** Heating/cooling is decided from measured temperature trend, never from a file name. */
public enum ScanDirection {
    HEATING, COOLING, ISOTHERMAL, UNKNOWN;

    public ScanDirection inverse() {
        return switch (this) {
            case HEATING -> COOLING;
            case COOLING -> HEATING;
            default -> this;
        };
    }
}
