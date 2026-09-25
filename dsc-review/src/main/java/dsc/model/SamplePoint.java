package dsc.model;

/**
 * 一个原始采样点：温度与热流均保留原始表示（rawText），
 * 规范化值在派生层计算。index 是仪器输出中的原始行号。
 */
public record SamplePoint(int index,
                          Quantity temperature,
                          Quantity heatFlow,
                          double tempKelvin,
                          double heatFlowMilliWatt) {

    public static SamplePoint of(int index, Quantity temperature, Quantity heatFlow) {
        double tK = Units.toKelvin(temperature.value(), temperature.unit());
        double hf = Units.heatFlowToMilliWatt(heatFlow.value(), heatFlow.unit());
        return new SamplePoint(index, temperature, heatFlow, tK, hf);
    }
}
