package dsc.model;

/** 样品质量的一个版本。每次修订产生新版本，旧版本保留。 */
public final class MassRecord {
    public String versionId;
    public String createdEvent;
    public Quantity mass;
    public String note;

    public MassRecord() {}

    public MassRecord(String versionId, String createdEvent, Quantity mass, String note) {
        this.versionId = versionId;
        this.createdEvent = createdEvent;
        this.mass = mass;
        this.note = note;
    }
}
