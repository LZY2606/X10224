package dsc.domain;

/** Entity kinds (atoms and versioned derivations) and well-known field keys. */
public final class Kinds {
    private Kinds() {
    }

    // Source atoms (separately archived, never overwritten in place).
    public static final String SAMPLE = "sample";
    public static final String MEASUREMENT = "meas";
    public static final String CURVE = "curve";
    public static final String MASS = "mass";
    public static final String RATE = "rate";
    public static final String ATMOSPHERE = "atmo";
    public static final String CALIBRATION = "calib";
    public static final String TANGENT = "tang";

    // Versioned derived / decision entities.
    public static final String RANGE = "range";
    public static final String NORMALIZED = "norm";
    public static final String BASELINE = "base";
    public static final String TRANSITION = "trans";

    // Lifecycle states.
    public static final String ST_ACTIVE = "ACTIVE";
    public static final String ST_SUPERSEDED = "SUPERSEDED";
    public static final String ST_STALE = "STALE";
    public static final String ST_FAILED = "FAILED";
    public static final String ST_REJECTED = "REJECTED";

    // Well-known atom payload keys (all carry a unit/coordinate datum).
    public static final String SEGMENT_TYPE = "segmentType";       // HEATING | COOLING
    public static final String DIRECTION_KNOWN = "directionKnown";
    public static final String SIGN_CONVENTION = "signConvention"; // EXO_UP | ENDO_UP | UNKNOWN
    public static final String RATE_VALUE = "rateValue";
    public static final String RATE_UNIT = "rateUnit";
    public static final String CYCLE_INDEX = "cycleIndex";
    public static final String MASS_MG = "massMg";
    public static final String CALIB_OFFSET_C = "offsetCelsius";
    public static final String HF_UNIT = "hfUnit";
}
