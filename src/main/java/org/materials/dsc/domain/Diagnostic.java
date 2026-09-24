package org.materials.dsc.domain;

/** 无法规范化的原始值诊断；原始值保留在原位置，不为画图而丢弃。 */
public record Diagnostic(String id, String severity, String code, String message,
                         String sourceRef, String atEventSeq) {
    public static Diagnostic warn(String id, String code, String message, String sourceRef) {
        return new Diagnostic(id, "warning", code, message, sourceRef, null);
    }

    public static Diagnostic error(String id, String code, String message, String sourceRef) {
        return new Diagnostic(id, "error", code, message, sourceRef, null);
    }
}
