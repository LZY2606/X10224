package dsc.model;

/**
 * Free parameters of a baseline candidate. polynomialDegree applies only to
 * POLYNOMIAL; sigmoidWidthC only to SIGMOID_STEP.
 */
public record BaselineParams(double leftStartC, double leftEndC, double rightStartC, double rightEndC,
                             int polynomialDegree, double sigmoidWidthC, BaselineType type) {
    public static BaselineParams tangent(double a, double b, double c, double d) {
        return new BaselineParams(a, b, c, d, 1, 0.0, BaselineType.LINEAR_TANGENT);
    }
}
