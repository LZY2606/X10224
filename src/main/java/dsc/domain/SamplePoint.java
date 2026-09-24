package dsc.domain;

/**
 * 仪器输出的单个采样点：时间、温度、热流均保留原始文本与单位。
 * 规范化值在导入解析时计算（不含标定/质量，那些是派生层的事）。
 */
public record SamplePoint(
        RawValue tSec,
        RawValue tempC,
        RawValue hfMw,
        boolean tempBad,
        boolean hfBad,
        String note
) {
    public double t() { return tSec.normalized(); }
    public double temp() { return tempC.normalized(); }
    public double hf() { return hfMw.normalized(); }
}
