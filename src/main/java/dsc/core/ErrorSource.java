package dsc.core;

/** 焓变/特征温度的误差来源，随分析结果一起读回，供复核界面展示。 */
public record ErrorSource(String code, String detail, double magnitude, String unit) {
    public static final String NON_UNIFORM_SPACING = "NON_UNIFORM_SPACING";
    public static final String REVERSED_CONVENTION = "REVERSED_CONVENTION";
    public static final String OVERLAPPING_TRANSITION = "OVERLAPPING_TRANSITION";
    public static final String MASS_REVISION = "MASS_REVISION";
    public static final String CALIBRATION_OFFSET = "CALIBRATION_OFFSET";
    public static final String BASELINE_AMBIGUITY = "BASELINE_AMBIGUITY";
    public static final String TANGENT_SPARSE = "TANGENT_SPARSE";
    public static final String UNKNOWN_DIRECTION = "UNKNOWN_DIRECTION";
}
