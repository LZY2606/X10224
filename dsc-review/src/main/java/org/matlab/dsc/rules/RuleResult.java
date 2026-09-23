package org.matlab.dsc.rules;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.matlab.dsc.domain.Diagnostic;

/**
 * Outcome of running a rule: structured payload plus the machine-readable
 * judgment basis codes and diagnostics. Rules never mutate their inputs.
 */
public class RuleResult {
    private Map<String, Object> payload = Map.of();
    private final List<String> basis = new ArrayList<>();
    private final List<Diagnostic> diagnostics = new ArrayList<>();
    private boolean rejected;

    public RuleResult payload(Map<String, Object> payload) {
        this.payload = payload;
        return this;
    }

    public RuleResult basis(String code) {
        if (!basis.contains(code)) {
            basis.add(code);
        }
        return this;
    }

    public RuleResult diagnostic(Diagnostic d) {
        diagnostics.add(d);
        return this;
    }

    public RuleResult rejected() {
        this.rejected = true;
        return this;
    }

    public Map<String, Object> payload() {
        return payload;
    }

    public List<String> basis() {
        return basis;
    }

    public List<Diagnostic> diagnostics() {
        return diagnostics;
    }

    public boolean rejected() {
        return rejected;
    }
}
