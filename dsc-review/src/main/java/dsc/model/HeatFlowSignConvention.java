package dsc.model;

/**
 * 热流符号约定。DSC 仪器存在两种主流约定：
 * exo-up（放热向上，结晶为正峰）与 endo-up（吸热向上，熔融为正峰）。
 * 约定必须来自仪器输出档案，焓变符号在积分时据此解释，禁止看文件名。
 */
public enum HeatFlowSignConvention {
    EXO_UP("exo-up"),
    ENDO_UP("endo-up");

    private final String id;

    HeatFlowSignConvention(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    public static HeatFlowSignConvention parse(String raw) {
        if (raw == null) {
            throw new IllegalArgumentException("heat-flow sign convention metadata missing");
        }
        return switch (raw.trim().toLowerCase()) {
            case "exo-up", "exo_up", "exoup", "exotherm-up" -> EXO_UP;
            case "endo-up", "endo_up", "endoup", "endotherm-up" -> ENDO_UP;
            default -> throw new IllegalArgumentException("unknown heat-flow sign convention: " + raw);
        };
    }

    /**
     * 把仪器原始热流归一到“吸热为正”的内部约定。
     * exo-up 仪器需要取反。
     */
    public double toEndoPositive(double heatFlowMilliWatt) {
        return this == EXO_UP ? -heatFlowMilliWatt : heatFlowMilliWatt;
    }
}
