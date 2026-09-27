package dsc.model;

import com.fasterxml.jackson.annotation.JsonValue;

/** 相变/松弛事件种类。吸/放由符号结合方向判定，不按文件名。 */
public enum TransitionKind {
    GLASS("glass"),
    CRYSTALLIZATION("crystallization"),
    MELTING("melting"),
    UNKNOWN("unknown");

    private final String code;

    TransitionKind(String code) {
        this.code = code;
    }

    @JsonValue
    public String code() {
        return code;
    }

    public static TransitionKind fromCode(String s) {
        for (TransitionKind k : values()) if (k.code.equals(s)) return k;
        throw new IllegalArgumentException(s);
    }
}
