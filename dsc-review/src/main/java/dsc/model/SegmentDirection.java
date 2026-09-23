package dsc.model;

/** Thermal direction of a scan segment. Determined from metadata + data monotonicity. */
public enum SegmentDirection {
    HEATING, COOLING, MIXED, UNKNOWN
}
