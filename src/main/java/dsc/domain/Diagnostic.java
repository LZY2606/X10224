package dsc.domain;

import java.util.LinkedHashMap;
import java.util.Map;

/** Machine-readable judgement record attached to inputs, derivations or decisions. */
public final class Diagnostic {
    public final String code;     // e.g. EMPTY_INTERVAL, REVERSE_HEAT_FLOW, UNIT_UNCONVERTIBLE
    public final String severity; // INFO | WARN | BLOCK
    public final String message;
    public final String subject;  // stable id of the affected object

    public Diagnostic(String code, String severity, String message, String subject) {
        this.code = code;
        this.severity = severity;
        this.message = message;
        this.subject = subject;
    }

    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("code", code);
        m.put("severity", severity);
        m.put("message", message);
        m.put("subject", subject);
        return m;
    }

    @Override public String toString() { return code + "[" + severity + "] " + message + " -> " + subject; }
}
