package dsc.core;

/**
 * 已参数化的基线。多个合理基线作为并列候选保留，互不覆盖。
 * LINEAR:    hf = a + b*T
 * SIGMOIDAL: 在左右切线之间做 logistic 过渡（玻璃化/重叠转折常用）。
 */
public record Baseline(BaselineType type, double a, double b, double k, double tMid,
                       double leftA, double leftB, double rightA, double rightB) {

    public static Baseline linear(double a, double b) {
        return new Baseline(BaselineType.LINEAR, a, b, 0, 0, a, b, a, b);
    }

    public static Baseline sigmoidal(double leftA, double leftB, double rightA, double rightB,
                                     double k, double tMid) {
        return new Baseline(BaselineType.SIGMOIDAL, 0, 0, k, tMid, leftA, leftB, rightA, rightB);
    }

    public double at(double tC) {
        return switch (type) {
            case LINEAR -> a + b * tC;
            case SIGMOIDAL -> {
                double w = 1.0 / (1.0 + Math.exp(-k * (tC - tMid)));
                double left = leftA + leftB * tC;
                double right = rightA + rightB * tC;
                yield left * (1 - w) + right * w;
            }
        };
    }
}
