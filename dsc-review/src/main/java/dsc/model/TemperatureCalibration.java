package dsc.model;

import java.util.Map;

/**
 * 温度标定档案：以标准物质相变温度为锚点。
 * 温度轴修正在派生层应用为 offset（必要时 slope），原始点不被改写。
 */
public record TemperatureCalibration(String calibrationId,
                                     double offsetKelvin,
                                     double slope,
                                     Map<String, Double> referencePeaksKelvin,
                                     String note) {

    public double apply(double tempKelvin) {
        return offsetKelvin + slope * tempKelvin;
    }
}
