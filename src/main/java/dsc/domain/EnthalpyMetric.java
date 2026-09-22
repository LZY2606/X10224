package dsc.domain;

import java.util.List;

/**
 * 焓变及其误差来源。所有量携带单位；误差是可解释的分项之和而非黑箱数字。
 */
public record EnthalpyMetric(
        String id,
        String featureId,
        double valueJPerG,
        double totalUncertaintyJPerG,
        List<ErrorContribution> errorBudget,
        String signNote
) {

    public record ErrorContribution(String code, String label, double jPerG, String detail) {
    }
}
