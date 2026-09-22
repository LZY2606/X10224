package dsc.model;

/** 诊断：无法转换/空区间/方向未知等，附在原位置，不为画图丢值。 */
public record Diagnostic(String code, String message, String location) {}
