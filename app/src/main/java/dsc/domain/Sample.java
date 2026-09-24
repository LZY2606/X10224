package dsc.domain;

/** A sample history (履历): heating and cooling DSC segments live together here. */
public class Sample {
    public String id;
    public String code;        // human code, e.g. PEEK-047
    public String material;
    public String note;
    public long createdAt;
    public long revision;

    public Sample() {}
    public Sample(String id, String code, String material, String note, long createdAt) {
        this.id = id; this.code = code; this.material = material; this.note = note;
        this.createdAt = createdAt; this.revision = 1;
    }
}
