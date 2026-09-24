package io.dsc.model;

/**
 * Stable identifier for a domain object. Ids are user/algorithm generated at
 * birth and never change; kind prevents collisions between object families.
 * Export/delete/reimport preserves id, branch and ordering.
 */
public record StableId(String kind, String value) {
    public StableId {
        if (kind == null || kind.isBlank() || value == null || value.isBlank()) {
            throw new IllegalArgumentException("stable id requires kind and value");
        }
        kind = kind.trim();
        value = value.trim();
    }

    public static StableId of(String kind, String value) {
        return new StableId(kind, value);
    }

    public String full() {
        return kind + ":" + value;
    }

    @Override public String toString() {
        return full();
    }

    public static StableId parse(String full) {
        int i = full.indexOf(':');
        if (i <= 0) throw new IllegalArgumentException("not a stable id: " + full);
        return new StableId(full.substring(0, i), full.substring(i + 1));
    }
}
