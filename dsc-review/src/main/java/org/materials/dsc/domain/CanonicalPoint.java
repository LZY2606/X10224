package org.materials.dsc.domain;

/**
 * 派生层的规范化点。canonicalHeatFlowMilliwatt 已是“吸热为正”；
 * specificHeatFlowMilliwattPerGram 依赖质量版本，质量修订后必须重算。
 */
public record CanonicalPoint(
        int rawIndex,
        double timeSec,
        double temperatureKelvin,
        double rawHeatFlowMilliwatt,
        double canonicalHeatFlowMilliwatt,
        Double specificHeatFlowMilliwattPerGram) {
}
