package dsc.model;

/** 派生层诊断：反向热流/非等间隔/重叠/循环段/坏点/空区间等情形留痕。 */
public final class DeriveDiagnostic {
    public String diagId;
    public String code;
    public String severity;      // info | warning | error
    public String segmentId;
    public String windowId;
    public String message;
    public String basis;

    public DeriveDiagnostic() {}

    public DeriveDiagnostic(String diagId, String code, String severity, String segmentId, String windowId, String message, String basis) {
        this.diagId = diagId;
        this.code = code;
        this.severity = severity;
        this.segmentId = segmentId;
        this.windowId = windowId;
        this.message = message;
        this.basis = basis;
    }
}
