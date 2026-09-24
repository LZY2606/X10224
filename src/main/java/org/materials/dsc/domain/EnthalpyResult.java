package org.materials.dsc.domain;

import java.util.List;

/**
 * 相变积分结果。enthalpyJ 为总能量，specificEnthalpyJg 依赖当时的质量版本；
 * 质量修订后该归一化派生过期（stale=true），等待重算。
 */
public record EnthalpyResult(String id, String transitionId, String baselineId,
                             String traceId, String segmentId,
                             ThermalSign sign,
                             DisplayValue enthalpy,
                             DisplayValue specificEnthalpy,
                             String massRecordId,
                             double areaMilliwattCelsius, double rateCelsiusPerMin,
                             List<ErrorComponent> errorBudget,
                             String ruleVersion, String eventSeq, boolean stale,
                             String invalidatedReason) {
}
