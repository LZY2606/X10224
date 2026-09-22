package org.materials.dsc.domain;

/**
 * 仪器输出的一个原始点。timeSec 由原始时间换算得到；temperatureKelvin 可能为 null
 * （不可换算时保留原字符串诊断）；rawHeatFlowMilliwatt 是未做方向翻转的原始信号。
 */
public record SeriesPoint(
        int index,
        String rawTime,
        Double timeSec,
        String rawTemperature,
        Double temperatureKelvin,
        String rawHeatFlow,
        Double rawHeatFlowMilliwatt) {
}
