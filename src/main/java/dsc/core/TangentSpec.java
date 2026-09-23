package dsc.core;

/**
 * User-selected tangent windows (in Kelvin on the segment). These come from the
 * tangent-selection archive, not from the curve itself.
 */
public record TangentSpec(double leftLoK, double leftHiK,
                          double rightLoK, double rightHiK,
                          String sourceLogicalId) {

    public boolean valid() {
        return leftLoK < leftHiK && leftHiK <= rightLoK && rightLoK < rightHiK;
    }
}
