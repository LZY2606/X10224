package org.materials.dsc.domain;

import java.util.List;

/**
 * 温度标定：T_corrected = slope * T_measured + offset（offset 单位 °C）。
 * 标定来源（标样）单独记录，属于直接来源。
 */
public record TemperatureCalibration(String id, double slope, double offsetCelsius,
                                     String standardRef, String note,
                                     String eventSeq, String at, List<String> pointRefs) {
    public double applyCelsius(double measuredCelsius) {
        return slope * measuredCelsius + offsetCelsius;
    }
}
