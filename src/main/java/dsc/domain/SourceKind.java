package dsc.domain;

/**
 * The separately archived direct sources. Each kind is stored independently so a change
 * to one source only invalidates the dependents that actually consume it.
 */
public enum SourceKind {
    INSTRUMENT_OUTPUT("instrument_output", "仪器输出"),
    SAMPLE_MASS("sample_mass", "样品质量"),
    SCAN_RATE("scan_rate", "扫描速率"),
    ATMOSPHERE("atmosphere", "气氛"),
    TEMPERATURE_CALIBRATION("temperature_calibration", "温度标定"),
    USER_TANGENT("user_tangent", "用户选定的切线");

    public final String code;
    public final String label;

    SourceKind(String code, String label) {
        this.code = code;
        this.label = label;
    }

    public static SourceKind fromCode(String code) {
        for (SourceKind k : values()) {
            if (k.code.equals(code)) {
                return k;
            }
        }
        throw new IllegalArgumentException("unknown source kind: " + code);
    }
}
