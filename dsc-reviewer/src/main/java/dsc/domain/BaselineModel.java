package dsc.domain;

/**
 * The family of baseline constructions we know how to recompute.
 *
 * LINEAR_TANGENT  : one straight line through two analyst-picked tangent
 *                   regions (peaks: crystallisation / melting).
 * STEP_TANGENT    : two tangent lines joined at Tg, exposing the step height
 *                   ΔCp used for glass-transition analysis.
 */
public enum BaselineModel {
    LINEAR_TANGENT,
    STEP_TANGENT
}
