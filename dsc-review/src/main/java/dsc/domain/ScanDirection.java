package dsc.domain;

/** Segment direction from the actual temperature trend, not the file name. */
public enum ScanDirection {
    HEATING,
    COOLING,
    ISOTHERMAL;

    public String code() {
        return switch (this) {
            case HEATING -> "heating";
            case COOLING -> "cooling";
            case ISOTHERMAL -> "isothermal";
        };
    }
}
