package dsc.model;

/**
 * User-chosen tangent anchor windows, stored separately from instrument output.
 * Anchors reference temperature coordinates (not raw indices) so the choice
 * survives re-import and non-uniform grids.
 */
public record TangentSelection(String tangentId, String regionId, String createdBy,
                               double leftLoCelsius, double leftHiCelsius,
                               double rightLoCelsius, double rightHiCelsius,
                               String mode, String note) {
    public static final String MODE_TWO_SIDED = "two-sided";
    public static final String MODE_PARALLEL = "parallel";
}
