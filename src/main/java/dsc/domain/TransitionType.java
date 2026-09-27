package dsc.domain;

public enum TransitionType {
    GLASS("glass_transition"),
    CRYSTALLIZATION("crystallization"),
    MELTING("melting"),
    UNRESOLVED("unresolved_overlap");

    private final String code;

    TransitionType(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }

    public static TransitionType fromCode(String code) {
        for (TransitionType t : values()) {
            if (t.code.equalsIgnoreCase(code)) {
                return t;
            }
        }
        throw new IllegalArgumentException("unknown transition type: " + code);
    }
}
