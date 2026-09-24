package dsc.model;

/**
 * Heating vs cooling is decided from the temperature axis itself (its monotonic drift),
 * never from a file name. MIXED means the segment changes direction (a cycle boundary).
 */
public enum ScanDirection {
    HEATING, COOLING, MIXED, UNKNOWN;

    /** Returns true only when both directions are definite and equal. Heating and cooling must never mix. */
    public boolean compatibleWith(ScanDirection other) {
        if (this == UNKNOWN || other == UNKNOWN) {
            return true;
        }
        if (this == MIXED || other == MIXED) {
            return false;
        }
        return this == other;
    }
}
