package dsc.core;

/** 规范化后的曲线点：时间 s、温度 °C、热流 mW。 */
public record DscPoint(double timeS, double tempC, double heatFlowMw) {}
