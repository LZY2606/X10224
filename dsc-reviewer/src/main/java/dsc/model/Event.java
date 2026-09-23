package dsc.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.time.Instant;

/**
 * The journal entry. Events are append-only facts. The payload is a small JSON
 * document so unknown/new event kinds remain readable by older readers
 * (forward-compatible migration).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record Event(long seq, String type, JsonNode payload, String author,
                    String timestamp, String baseVersion, String idempotencyKey) {

    public static final String PROJECT_CREATED = "PROJECT_CREATED";
    public static final String TRACE_IMPORTED = "TRACE_IMPORTED";
    public static final String MASS_SET = "MASS_SET";
    public static final String RATE_SET = "RATE_SET";
    public static final String ATMOSPHERE_SET = "ATMOSPHERE_SET";
    public static final String CALIBRATION_SET = "CALIBRATION_SET";
    public static final String TANGENT_CHOSEN = "TANGENT_CHOSEN";
    public static final String WINDOW_DRAWN = "WINDOW_DRAWN";
    public static final String WINDOW_MOVED = "WINDOW_MOVED";
    public static final String BASELINES_PROPOSED = "BASELINES_PROPOSED";
    public static final String BASELINE_ADOPTED = "BASELINE_ADOPTED";
    public static final String BASELINE_REJECTED = "BASELINE_REJECTED";
    public static final String DERIVED_PUBLISHED = "DERIVED_PUBLISHED";
    public static final String CONFLICT_RESOLVED = "CONFLICT_RESOLVED";

    public static ObjectNode payload() {
        return new ObjectNode(JsonNodeFactory.instance);
    }

    public static Event create(String type, JsonNode payload, String author, String baseVersion, String key) {
        return new Event(0L, type, payload, author, Instant.now().toString(), baseVersion, key);
    }
}
