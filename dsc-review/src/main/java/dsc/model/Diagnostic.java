package dsc.model;

/**
 * 导入或推导过程中产生的诊断。严重级别：
 * INFO 仅提示；WARN 值被保留但未参与某派生；ERROR 该派生无法完成。
 */
public record Diagnostic(Severity severity, String code, String message) {
    public enum Severity { INFO, WARN, ERROR }

    public static Diagnostic warn(String code, String message) {
        return new Diagnostic(Severity.WARN, code, message);
    }

    public static Diagnostic error(String code, String message) {
        return new Diagnostic(Severity.ERROR, code, message);
    }

    public static Diagnostic info(String code, String message) {
        return new Diagnostic(Severity.INFO, code, message);
    }
}
