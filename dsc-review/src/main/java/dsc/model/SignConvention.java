package dsc.model;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * 仪器热流符号约定。exo_up：放热向上（放热峰为正偏转）；endo_up：吸热向上。
 * 吸放热方向必须来自仪器输出中的显式声明，文件名不参与判断。
 */
public enum SignConvention {
    EXO_UP("exo_up"),
    ENDO_UP("endo_up"),
    UNSPECIFIED("unspecified");

    private final String code;

    SignConvention(String code) {
        this.code = code;
    }

    @JsonValue
    public String code() {
        return code;
    }

    public static SignConvention fromCode(String s) {
        if (s == null || s.isBlank()) return UNSPECIFIED;
        return switch (s.trim().toLowerCase().replace(" ", "")) {
            case "exo_up", "exoup", "exothermup", "exo-up" -> EXO_UP;
            case "endo_up", "endoup", "endothermup", "endo-up" -> ENDO_UP;
            default -> UNSPECIFIED;
        };
    }

    /** 统一到内部 exo-up 坐标时需要乘的因子。 */
    public double normalizeFactor() {
        return this == ENDO_UP ? -1.0 : 1.0;
    }
}
