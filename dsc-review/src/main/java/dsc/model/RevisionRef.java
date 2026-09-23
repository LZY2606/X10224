package dsc.model;

/** Reference to a specific revision of an input or derivation (dependency edge). */
public record RevisionRef(String kind, String id, int revision) {
    public static RevisionRef of(String kind, String id, int revision) {
        return new RevisionRef(kind, id, revision);
    }
}
