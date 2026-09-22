package dsc.model;

/**
 * 吸放热方向约定。只能来自仪器输出/元数据，禁止从文件名猜测。
 */
public enum HeatFlowSign {
    ENDO_UP("endo-up"),
    ENDO_DOWN("endo-down"),
    UNKNOWN("unknown");

    private final String token;

    HeatFlowSign(String token) {
        this.token = token;
    }

    public String token() {
        return token;
    }

    public static HeatFlowSign fromToken(String raw) {
        if (raw == null) {
            return UNKNOWN;
        }
        String s = raw.trim().toLowerCase();
        return switch (s) {
            case "endo-up", "endo_up", "endotherm-up", "exo-down", "exo_down" -> ENDO_UP;
            case "endo-down", "endo_down", "endotherm-down", "exo-up", "exo_up" -> ENDO_DOWN;
            default -> UNKNOWN;
        };
    }
}
