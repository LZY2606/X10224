package dsc.domain;

public enum BaselineKind {
    /** Straight line joining two user-selected tangents (typical peak). */
    LINEAR_TANGENT("linear_tangent"),
    /** Independent pre/post tangent slopes extended under the event (Tg step). */
    STEP_TANGENT("step_tangent"),
    /** Shared tangent across overlapped transitions (one legal partition). */
    SHARED_TANGENT("shared_tangent");

    private final String code;

    BaselineKind(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }

    public static BaselineKind fromCode(String code) {
        for (BaselineKind k : values()) {
            if (k.code.equalsIgnoreCase(code)) {
                return k;
            }
        }
        throw new IllegalArgumentException("unknown baseline kind: " + code);
    }
}
