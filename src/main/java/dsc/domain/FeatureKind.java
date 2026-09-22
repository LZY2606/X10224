package dsc.domain;

/** 玻璃化、结晶（含冷结晶）与熔融。 */
public enum FeatureKind {
    GLASS("glass", "玻璃化转变"),
    CRYSTALLIZATION("crystallization", "结晶"),
    MELT("melt", "熔融");

    private final String code;
    private final String label;

    FeatureKind(String code, String label) {
        this.code = code;
        this.label = label;
    }

    public String code() {
        return code;
    }

    public String label() {
        return label;
    }

    public static FeatureKind fromCode(String code) {
        for (FeatureKind kind : values()) {
            if (kind.code.equals(code)) {
                return kind;
            }
        }
        throw new IllegalArgumentException("未知转折类型: " + code);
    }
}
