package dsc.model;

/**
 * 基线候选。status: candidate | accepted | rejected。
 * 多个合理基线并存保留，拒绝不等于删除。
 */
public record Baseline(
        String id,
        String regionId,
        int version,
        String type,        // linear | tangent
        double tlo, double thi,
        double slope, double intercept,
        String status
) {
    public double y(double t) { return slope * t + intercept; }
}
