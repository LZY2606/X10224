package dsc.model;

/**
 * 焓变/特征温度的一个误差来源（每个显示值可回溯其误差来源与量级）。
 */
public final class ErrorSource {
    public String errorId;              // 稳定身份
    public String code;                 // mass / rate / calibration / spacing / overlap / sign ...
    public String label;
    public double deltaJg;              // 对 ΔH (J/g) 的贡献量（绝对值量级）
    public double deltaK;               // 对温度 (K) 的贡献量
    public String basis;               // 机器可读依据简述

    public ErrorSource() {}

    public ErrorSource(String errorId, String code, String label, double deltaJg, double deltaK, String basis) {
        this.errorId = errorId;
        this.code = code;
        this.label = label;
        this.deltaJg = deltaJg;
        this.deltaK = deltaK;
        this.basis = basis;
    }
}
