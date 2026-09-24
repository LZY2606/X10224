package org.materials.dsc.domain;

/**
 * 仪器热流符号约定。规范域约定：放热(exo)为正、吸热(endo)为负（mW）。
 * 仪器文件必须显式声明约定；仅靠文件名不允许判断吸放热方向。
 */
public enum SignConvention {
    EXO_UP("exo-up", 1.0),
    EXO_DOWN("exo-down", -1.0),
    UNKNOWN("unknown", 0.0);

    private final String token;
    private final double toCanonicalFactor;

    SignConvention(String token, double factor) {
        this.token = token;
        this.toCanonicalFactor = factor;
    }

    public String token() {
        return token;
    }

    public double canonicalFactor() {
        return toCanonicalFactor;
    }

    public static SignConvention parse(String s) {
        if (s == null) {
            return UNKNOWN;
        }
        return switch (s.trim().toLowerCase(java.util.Locale.ROOT)) {
            case "exo-up", "exo up", "exotherm-up", "放热向上" -> EXO_UP;
            case "exo-down", "exo down", "exotherm-down", "endo-up", "放热向下" -> EXO_DOWN;
            default -> UNKNOWN;
        };
    }
}
