package dsc.model;

/** Transition family. GLASS: step (baseline shift); CRYSTAL / MELT: peaks. */
public enum TransitionKind {
    GLASS("glass"),
    CRYSTALLIZATION("crystallization"),
    MELT("melt"),
    UNKNOWN("unknown");

    private final String code;

    TransitionKind(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }

    public boolean isStep() {
        return this == GLASS;
    }

    public static TransitionKind fromCode(String s) {
        for (TransitionKind k : values()) {
            if (k.code.equalsIgnoreCase(s) || k.name().equalsIgnoreCase(s)) {
                return k;
            }
        }
        return UNKNOWN;
    }
}
