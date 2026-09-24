package org.materials.dsc.domain;

/**
 * 一行仪器输出的原始表示。rawTemperature/rawHeatFlow 保留原始字符串与单位；
 * normalizedT/normalizedHF 仅在转换成功时填充（°C、规范符号的 mW）。
 */
public record SampleRow(int index,
                        String rawTemperature, String tempUnit,
                        String rawHeatFlow, String hfUnit,
                        Double normalizedT, Double normalizedHF,
                        boolean nonConvertible) {
}
