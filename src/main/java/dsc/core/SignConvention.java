package dsc/core;

/**
 * 热流符号约定。DSC 仪器对“吸热为正/负”有两种输出约定。
 * 绝不能靠文件名或方向推断；必须读取仪器输出中的显式 signConvention 字段。
 */
public enum SignConvention {
    EXO_UP("exoUp", "放热向上(放热峰为正)"),
    ENDO_UP("endoUp", "吸热向上(吸热峰为正)"),
    UNKNOWN("unknown", "未知");

    public final String code;
    public final String label;

    SignConvention(String code, String label) { this.code = code; this.label = label; }

    public static SignConvention fromMeta(String raw) {
        if (raw == null) return UNKNOWN;
        return switch (raw.trim().toLowerCase()) {
            case "exoup", "exo-up", "exo_up", "exo positive", "放热向上" -> EXO_UP;
            case "endoup", "endo-up", "endo_up", "endo positive", "吸热向上" -> ENDO_UP;
            default -> UNKNOWN;
        };
    }

    /**
     * 把原始热流换算到内部约定 endoUp（吸热为正，单位 mW）。
     * exoUp 数据需要整体取反。
     */
    public double normalizeHeatFlow(double rawHeatFlow) {
        return this == EXO_UP ? -rawHeatFlow : rawHeatFlow;
    }
}
