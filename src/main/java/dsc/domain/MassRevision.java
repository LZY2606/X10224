package dsc.domain;

/** Mass is versioned; a new revision invalidates all mass-normalized derivatives. */
public final class MassRevision {
    public final String id;
    public final int version;
    public final Quantity mass;
    public final String note;

    public MassRevision(String id, int version, Quantity mass, String note) {
        this.id = id;
        this.version = version;
        this.mass = mass;
        this.note = note;
    }
}
