package dsc.rules;

/**
 * Temperature calibration is an affine correction T_true = a * T_indicated + b.
 * It is an independently archived source; changing it invalidates every derived
 * temperature (onset/peak/endset) and every tangent-anchored baseline.
 */
public final class Calibration {

    private Calibration() {
    }

    public static double apply(double indicatedT, double a, double b) {
        return a * indicatedT + b;
    }

    public static boolean identity(double a, double b) {
        return a == 1.0 && b == 0.0;
    }
}
