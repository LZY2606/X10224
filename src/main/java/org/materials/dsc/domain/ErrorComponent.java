package org.materials.dsc.domain;

/** 焓变误差预算的一个来源分量。 */
public record ErrorComponent(String code, String label, double contribution, String unit,
                             String sourceRef) {
}
