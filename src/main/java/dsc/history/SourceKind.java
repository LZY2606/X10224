package dsc.history;

/**
 * The six direct inputs are stored in independent archives. Changing one only
 * invalidates derived nodes that depend on it.
 */
public enum SourceKind {
    INSTRUMENT_OUTPUT("instrument-output", "仪器输出"),
    SAMPLE_MASS("sample-mass", "样品质量"),
    SCAN_RATE("scan-rate", "扫描速率"),
    ATMOSPHERE("atmosphere", "气氛"),
    TEMPERATURE_CALIBRATION("temperature-calibration", "温度标定"),
    TANGENT_SELECTION("tangent-selection", "用户选定的切线");

    private final String id;
    private final String label;

    SourceKind(String id, String label) {
        this.id = id;
        this.label = label;
    }

    public String id() {
        return id;
    }

    public String label() {
        return label;
    }

    public static SourceKind fromId(String id) {
        for (SourceKind k : values()) {
            if (k.id.equals(id)) {
                return k;
            }
        }
        throw new IllegalArgumentException("未知来源类型: " + id);
    }
}
