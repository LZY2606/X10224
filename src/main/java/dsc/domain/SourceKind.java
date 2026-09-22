package dsc.domain;

/**
 * 仪器输出、样品质量、扫描速率、气氛、温度标定与用户选定的切线分别存档。
 */
public enum SourceKind {
    INSTRUMENT_OUTPUT("instrument_output", "仪器输出"),
    MASS("mass", "样品质量"),
    SCAN_RATE("scan_rate", "扫描速率"),
    ATMOSPHERE("atmosphere", "气氛"),
    TEMP_CALIBRATION("temp_calibration", "温度标定"),
    TANGENT_SELECTION("tangent_selection", "用户选定的切线");

    private final String code;
    private final String label;

    SourceKind(String code, String label) {
        this.code = code;
        this.label = label;
    }

    public String code() {
        return code;
    }

    public String label() {
        return label;
    }

    public static SourceKind fromCode(String code) {
        for (SourceKind kind : values()) {
            if (kind.code.equals(code)) {
                return kind;
            }
        }
        throw new IllegalArgumentException("未知来源类型: " + code);
    }
}
