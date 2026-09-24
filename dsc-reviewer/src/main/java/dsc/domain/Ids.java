package dsc.domain;

/**
 * Stable, human-readable identifiers. Identifiers are never reused and never
 * change across export / delete / import. Versioned entities carry a separate
 * monotonically increasing version, so the identity ({@code baseId}) survives
 * revisions while each revision gets its own {@code (baseId, version)} node.
 */
public final class Ids {
    private Ids() {
    }

    public static String of(String kind, int seq) {
        return kind + "-" + String.format("%04d", seq);
    }

    /** A particular revision node of a versioned entity, e.g. {@code mass-0002#v3}. */
    public static String rev(String baseId, int version) {
        return baseId + "#v" + version;
    }

    public static String baseOf(String revisionId) {
        int hash = revisionId.indexOf('#');
        return hash < 0 ? revisionId : revisionId.substring(0, hash);
    }
}
