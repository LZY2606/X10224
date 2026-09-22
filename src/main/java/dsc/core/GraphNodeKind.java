package dsc.core;

/**
 * 依赖图节点族。来源层（*）独立存档且只增不改；派生层每次重算产生新版本。
 */
public enum GraphNodeKind {
    SRC_INSTRUMENT("instrument_output", false),
    SRC_MASS("mass", false),
    SRC_RATE("scan_rate", false),
    SRC_ATMOSPHERE("atmosphere", false),
    SRC_CALIBRATION("temperature_calibration", false),
    SRC_TANGENT("tangent", false),
    DERIVED_SEGMENT_CURVE("segment_curve", true),
    DERIVED_BASELINE("baseline", true),
    DERIVED_TRANSITION("transition", true),
    DERIVED_ENTHALPY("enthalpy", true);

    public final String code;
    public final boolean derived;

    GraphNodeKind(String code, boolean derived) {
        this.code = code;
        this.derived = derived;
    }

    public static GraphNodeKind fromCode(String code) {
        for (GraphNodeKind k : values()) {
            if (k.code.equals(code)) {
                return k;
            }
        }
        throw new IllegalArgumentException("unknown node kind: " + code);
    }
}
