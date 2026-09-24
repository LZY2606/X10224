package dsc.model;

/**
 * A versioned sample mass. Each revision has its own stable id; normalized mass in grams
 * drives normalization, and every derived value records which mass version it used.
 */
public final class MassRecord {
    private final String massId;
    private final int revision;
    private final RawField mass;
    private final String note;
    private final String replacedMassId;

    public MassRecord(String massId, int revision, RawField mass, String note, String replacedMassId) {
        this.massId = massId;
        this.revision = revision;
        this.mass = mass;
        this.note = note == null ? "" : note;
        this.replacedMassId = replacedMassId;
    }

    public String massId() { return massId; }
    public int revision() { return revision; }
    public RawField mass() { return mass; }
    public String note() { return note; }
    public String replacedMassId() { return replacedMassId; }

    public double grams() {
        if (mass.normalized() == null) {
            throw new MassUnconvertibleException("Mass " + massId + " is unconvertible: " + mass.raw());
        }
        return mass.normalized();
    }

    public static final class MassUnconvertibleException extends RuntimeException {
        public MassUnconvertibleException(String m) { super(m); }
    }
}
