package dsc.model;

public enum NodeStatus {
    /** Inputs match the versions used for the derivation. */
    VALID,
    /** At least one dependency changed; the result is retained but expired. */
    STALE,
    /** Several legal answers exist; none may be silently collapsed to one. */
    MULTIPLE,
    /** Inputs insufficient/inconsistent (empty interval, reversed flow...). */
    INDETERMINATE,
    /** Source node replaced by a newer version (kept for history). */
    SUPERSEDED
}
