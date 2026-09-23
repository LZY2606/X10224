package dsc.model;

/** Supported baseline construction methods. */
public enum BaselineType {
    LINEAR_TANGENT,  // straight line between two tangent anchor regions
    SIGMOID_STEP,    // step-shaped baseline for glass transitions
    POLYNOMIAL       // higher-order fit for overlapping-event backgrounds
}
