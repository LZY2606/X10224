package dsc.model;

import com.fasterxml.jackson.annotation.JsonValue;

/** 基线模型种类。多个合理基线必须并列保留差异。 */
public enum BaselineModel {
    LINEAR("linear"),
    SIGMOID("sigmoid"),
    PIECEWISE("piecewise");

    private final String code;

    BaselineModel(String code) {
        this.code = code;
    }

    @JsonValue
    public String code() {
        return code;
    }

    public static BaselineModel fromCode(String s) {
        for (BaselineModel b : values()) if (b.code.equals(s)) return b;
        throw new IllegalArgumentException(s);
    }
}
