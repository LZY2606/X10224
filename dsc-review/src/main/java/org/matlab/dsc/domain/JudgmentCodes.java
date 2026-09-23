package org.matlab.dsc.domain;

/**
 * Machine-readable judgment basis codes shared by rules, replay and UI.
 * The three headline semantics have dedicated codes:
 *  DIRECTION_NOT_FILENAME  - sign comes from instrument convention + scan
 *                            direction metadata, never a filename guess
 *  HEAT_COOL_NOT_MIXED     - heating and cooling segments stay separate
 *  MULTI_BASELINE_KEPT     - all legitimate baselines retained with deltas
 *  MASS_REVISION_STALES    - mass revision expires every normalised derivative
 */
public final class JudgmentCodes {
    private JudgmentCodes() {
    }

    public static final String DIRECTION_NOT_FILENAME = "DIRECTION_NOT_FILENAME";
    public static final String HEAT_COOL_NOT_MIXED = "HEAT_COOL_NOT_MIXED";
    public static final String REVERSE_HEATFLOW_FLIPPED = "REVERSE_HEATFLOW_FLIPPED";
    public static final String NONUNIFORM_GRID_TRAPZ = "NONUNIFORM_GRID_TRAPEZOID";
    public static final String OVERLAP_MULTI_CANDIDATE = "OVERLAP_MULTI_CANDIDATE";
    public static final String CYCLE_SEGMENT_SWITCH = "CYCLE_SEGMENT_SWITCH";
    public static final String MASS_REVISION_STALES = "MASS_REVISION_STALES";
    public static final String MULTI_BASELINE_KEPT = "MULTI_BASELINE_KEPT";
    public static final String EMPTY_INTERVAL_REJECTED = "EMPTY_INTERVAL_REJECTED";
    public static final String MIXED_DIRECTION_REJECTED = "MIXED_DIRECTION_REJECTED";
    public static final String UNCONVERTIBLE_VALUE_KEPT = "UNCONVERTIBLE_VALUE_KEPT";
    public static final String BASELINE_ADOPTED = "BASELINE_ADOPTED";
    public static final String BASELINE_REJECTED = "BASELINE_REJECTED";
    public static final String PARAM_REVISED = "PARAM_REVISED";
    public static final String LOCAL_SOURCE_PATCHED = "LOCAL_SOURCE_PATCHED";
    public static final String CONFLICTING_DECISION = "CONFLICTING_DECISION";
    public static final String STILL_VALID = "STILL_VALID";
    public static final String STALE_NEEDS_RECOMPUTE = "STALE_NEEDS_RECOMPUTE";
    public static final String CONCURRENT_SAVE_CONFLICT = "CONCURRENT_SAVE_CONFLICT";
    public static final String HISTORY_VIEW_FROZEN = "HISTORY_VIEW_FROZEN";
    public static final String IDENTITY_PRESERVED_EXPORT = "IDENTITY_PRESERVED_EXPORT";
}
