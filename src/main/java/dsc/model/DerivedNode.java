package dsc.model;

import java.util.List;

/**
 * One immutable derived version. A new derivation never mutates an old node;
 * the current pointer flips only after the whole version is written.
 */
public final class DerivedNode {
    public enum Status { VALID, STALE, SUPERSEDED, CONFLICT }

    public final String id;
    public final int version;
    public final String kind;
    /** Source refs ("id@v") this version was computed from. */
    public final List<String> inputs;
    public final String params;
    public final Status status;
    public final String result;
    /** Why this node is still valid or why it must be recomputed. */
    public final String validityNote;
    public final String createdAt;

    public DerivedNode(String id, int version, String kind, List<String> inputs, String params,
                       Status status, String result, String validityNote, String createdAt) {
        this.id = id;
        this.version = version;
        this.kind = kind;
        this.inputs = inputs == null ? List.of() : inputs;
        this.params = params;
        this.status = status;
        this.result = result;
        this.validityNote = validityNote;
        this.createdAt = createdAt;
    }

    public String ref() {
        return id + "@v" + version;
    }
}
