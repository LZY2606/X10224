package dsc.rules;

/**
 * 焓变误差来源（机器可读，界面逐项读回）。
 */
public record ErrorSource(String code, double magnitudeJPerG, String explanation) {
}
