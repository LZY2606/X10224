package dsc.domain;

/**
 * 温度标定：T_calibrated = gain * T + offset（offset 单位 °C）。
 */
public record TempCalibration(String id, double gain, double offsetC, String standard, String note) {
    public double apply(double celsius) { return gain * celsius + offsetC; }
}
