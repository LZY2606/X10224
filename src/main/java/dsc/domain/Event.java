package dsc.domain;

import java.util.List;
import java.util.Map;

/**
 * Immutable audit entry. Basis codes are machine-readable so replays and the
 * review UI can explain why a transition, branch or decision existed without
 * parsing prose.
 */
public record Event(
        long seq,
        String type,
        String actor,
        long at,
        List<String> basis,
        List<String> targets,
        Map<String, Object> detail) {
}
