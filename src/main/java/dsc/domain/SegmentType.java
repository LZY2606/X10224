package dsc.domain;

/** Segment direction derived from the sign of the measured scan rate, not from names. */
public enum SegmentType {
    HEATING, COOLING, ISOTHERMIC, UNKNOWN;

    public static SegmentType fromRate(double rateKPerMin) {
        if (rateKPerMin > 0.01) return HEATING;
        if (rateKPerMin < -0.01) return COOLING;
        return ISOTHERMIC;
    }
}
