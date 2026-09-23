package dsc.core;

import java.util.List;

/**
 * All legal baseline candidates produced for one interval under one rule
 * version, plus the user-selected candidate id (which may be null).
 */
public record BaselineSet(String intervalId, List<BaselineCandidate> candidates,
                          String selectedId, boolean ambiguous) {
    public BaselineSet { candidates = List.copyOf(candidates); }

    public BaselineCandidate byId(String id) {
        return candidates.stream().filter(c -> c.id().equals(id))
                .findFirst().orElse(null);
    }
}
