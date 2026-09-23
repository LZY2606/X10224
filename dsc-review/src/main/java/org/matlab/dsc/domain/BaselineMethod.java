package org.matlab.dsc.domain;

/**
 * Legitimate baseline construction families. When more than one is valid for
 * a selection, candidates are kept side by side instead of collapsed.
 */
public enum BaselineMethod {
    LINEAR_TANGENT("linear_tangent"),
    TWO_POINT_TANGENT("two_point_tangent"),
    SIGMOID_STEP("sigmoid_step"),
    PIECEWISE_TANGENT("piecewise_tangent");

    private final String wire;

    BaselineMethod(String wire) {
        this.wire = wire;
    }

    public String wire() {
        return wire;
    }
}
