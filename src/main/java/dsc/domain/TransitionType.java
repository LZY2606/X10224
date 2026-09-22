package dsc.domain;

/** 热转折类型。玻璃化为台阶，结晶与熔融为峰。 */
public enum TransitionType {
    GLASS("glass", "玻璃化（台阶）"),
    CRYSTALLIZATION("crystallization", "结晶（放热峰）"),
    MELTING("melting", "熔融（吸热峰）"),
    UNKNOWN("unknown", "未分类转折");

    public final String code;
    public final String label;

    TransitionType(String code, String label) {
        this.code = code;
        this.label = label;
    }

    public boolean isStep() {
        return this == GLASS;
    }
}
