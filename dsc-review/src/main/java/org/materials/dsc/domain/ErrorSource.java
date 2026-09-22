package org.materials.dsc.domain;

/** 焓变误差来源的一项，带稳定身份与单位。 */
public record ErrorSource(
        String id,
        String code,
        String label,
        double contributionJoulePerGram,
        String detail) {
}
