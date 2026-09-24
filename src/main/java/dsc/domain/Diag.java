package dsc.domain;

/** 机器可读诊断：稳定 code、严重级别、人读说明、来源引用。 */
public record Diag(String code, String severity, String message, String ref) {
    public static Diag info(String code, String message, String ref) {
        return new Diag(code, "INFO", message, ref);
    }

    public static Diag warn(String code, String message, String ref) {
        return new Diag(code, "WARN", message, ref);
    }

    public static Diag error(String code, String message, String ref) {
        return new Diag(code, "ERROR", message, ref);
    }
}
