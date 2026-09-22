package dsc.domain;

import java.util.List;

/**
 * 一条合理基线候选。多个合理基线同时保留差异，采纳其中一条不会删除其余候选。
 *
 * <p>线性候选 ({@code tangent/pre-tangent/overlap})：baseline(t) = a*t + b。
 * 阶跃候选 ({@code step})：使用 {@code params} 中的左右切线与中点、宽度，
 * 由 {@code dsc.engine.Integrator#baselineAt} 求值。
 */
public record BaselineCandidate(
        String id,
        String featureId,
        String method,
        String label,
        double a,
        double b,
        double[] params,
        List<double[]> anchors,
        double residualRms,
        boolean adopted,
        String rationale
) {
    public BaselineCandidate withAdoption(boolean adopted, String rationale) {
        return new BaselineCandidate(id, featureId, method, label, a, b, params, anchors,
                residualRms, adopted, rationale);
    }

    public boolean linear() {
        return !"step".equals(method);
    }
}
