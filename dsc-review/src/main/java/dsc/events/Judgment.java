package dsc.events;

import java.util.Map;

/**
 * Machine-readable judgment emitted by the rules layer. Judgments are the
 * durable, queryable form of statements such as:
 * "heat-flow sign cannot be guessed from the file name",
 * "heating and cooling segments are not mixed",
 * "multiple legal baselines are retained",
 * "all mass-normalized derivations expired after the mass revision".
 */
public record Judgment(String code, Severity severity, String subjectId, String message,
                       String ruleVersion, Map<String, Object> evidence) {

    public enum Severity { INFO, SUSPECT, REJECTED }

    public static Judgment of(String code, Severity severity, String subjectId, String message,
                              String ruleVersion, Map<String, Object> evidence) {
        return new Judgment(code, severity, subjectId, message, ruleVersion, evidence);
    }
}
