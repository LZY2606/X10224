package dsc.core;

/** 仪器输出曲线上的一个采样点。时间 t 单位 s，temp 单位 °C，hf 为仪器原始热流（原始单位）。 */
public final class TracePoint {
    public final double t;
    public final double temp;
    public final double hf;
    public TracePoint(double t, double temp, double hf) {
        this.t = t;
        this.temp = temp;
        this.hf = hf;
    }
}
