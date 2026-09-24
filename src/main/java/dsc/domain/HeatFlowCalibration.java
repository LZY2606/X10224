package dsc.domain;

/**
 * 热流标定：HF_calibrated = gain * HF + offset（gain 无量纲，offset mW）。
 * 不改变吸放热符号约定；符号换算在规范化阶段按 signConvention 处理。
 */
public record HeatFlowCalibration(String id, double gain, double offsetMw, String standard, String note) {
    public double apply(double mw) { return gain * mw + offsetMw; }
}
