package dsc.domain;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;

/**
 * Append-only, immutable audit event. Everything visible in the reviewer is the result of
 * replaying events up to a point; returning to an old event restores the dependency
 * versions and visible conclusions that were current then — the newest computation never
 * overwrites history.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public final class Event {
    public long seq;
    public String id;
    public String type;
    public Instant at;
    public String actor;
    public String summary;
    /** Affected object ids with their kind/version, for impact analysis. */
    public JsonNode affected;
    public JsonNode detail;
    /** Ruleset version used when this event was produced. */
    public String rulesetVersion;
    public String ruleBasis;
    /** Case replay step that produced the event, when applicable. */
    public String caseStep;

    public Event() {
    }
}
