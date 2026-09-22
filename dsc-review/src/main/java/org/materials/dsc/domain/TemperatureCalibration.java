package org.materials.dsc.domain;

/**
 * 温度标定独立存档。T_display = T_measured + offset（规范单位 K）。
 * 参数修订（offset 变化）只影响以标定为输入的派生，不影响原始点。
 */
public record TemperatureCalibration(
        String id,
        String runId,
        int seq,
        Quantity offset,
        String standard,
        String createdAtIso) {
}
