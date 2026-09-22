package dsc.model;

/**
 * 诊断：无法转换/不合法/多解等都留在原位置，不静默丢弃。
 */
public record Diagnostic(String code, String severity, String message, String refId, String rawFragment) {

    public static Diagnostic error(String code, String message) {
        return new Diagnostic(code, "ERROR", message, null, null);
    }

    public static Diagnostic warn(String code, String message) {
        return new Diagnostic(code, "WARN", message, null, null);
    }

    public static Diagnostic info(String code, String message) {
        return new Diagnostic(code, "INFO", message, null, null);
    }

    public Diagnostic withRef(String id) {
        return new Diagnostic(code, severity, message, id, rawFragment);
    }

    public Diagnostic withFragment(String fragment) {
        return new Diagnostic(code, severity, message, refId, fragment);
    }
}
