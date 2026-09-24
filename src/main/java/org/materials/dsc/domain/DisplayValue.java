package org.materials.dsc.domain;

import java.util.List;

/**
 * 界面上每个显示值都带稳定身份：valueId 在重算与导出/再导入后保持不变，
 * provenance 列出直接来源，errors 为焓变等结果的误差来源分量。
 */
public record DisplayValue(String valueId, String name, double value, String unit,
                           double uncertainty, List<ErrorComponent> errors,
                           List<String> provenance, String derivedFrom,
                           String ruleVersion, String computedEventSeq, boolean stale) {

    public static DisplayValue of(String valueId, String name, double value, String unit,
                                  List<String> provenance, String derivedFrom,
                                  String ruleVersion, String eventSeq) {
        return new DisplayValue(valueId, name, value, unit, 0.0, List.of(), provenance,
                derivedFrom, ruleVersion, eventSeq, false);
    }
}
