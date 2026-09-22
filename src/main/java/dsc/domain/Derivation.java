package dsc.domain;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * One derivation step: a baseline candidate or a thermal result, versioned independently.
 * Only a fully successful derivation can become current; the record keeps the fingerprint
 * of every direct input so the dependency graph can decide validity/invalidation.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public final class Derivation {
    public String id;
    public String kind;
    public int version = 1;
    public String supersedes;
    public String targetId;
    public java.util.List<String> inputs;
    public String inputsFingerprint;
    public String rulesetVersion;
    public Status status = Status.VALID;
    public String invalidReason;
    public String eventId;
    public InstantView computedAt;

    public enum Status {
        VALID,
        STALE,
        FAILED,
        EMPTY
    }

    public static final class InstantView {
        public String iso;

        public InstantView() {
        }

        public InstantView(String iso) {
            this.iso = iso;
        }
    }

    public Derivation() {
    }
}
