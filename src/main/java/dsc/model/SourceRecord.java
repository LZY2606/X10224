package dsc.model;

import java.util.Map;

/**
 * A separately archived direct input. Each slot ({@link Types.SourceKind}) is stored as its
 * own record with stable identity so that a revision can be scoped precisely and so that
 * nothing is guessed from file names.
 */
public final class SourceRecord {
    public final String id;
    public final String sampleId;
    public final String segmentId;
    public final Types.SourceKind kind;
    public final String label;
    /** Original textual representation as received (CSV body, "5.10 mg", "N2 50 mL/min", ...). */
    public final String rawText;
    public final String unit;
    public final String reference;
    public final String origin;
    /** Version number within (sampleId, kind, segmentId); 1 based, strictly increasing. */
    public final int version;
    public final Map<String, String> metadata;

    public SourceRecord(String id, String sampleId, String segmentId, Types.SourceKind kind,
                        String label, String rawText, String unit, String reference,
                        String origin, int version, Map<String, String> metadata) {
        this.id = id;
        this.sampleId = sampleId;
        this.segmentId = segmentId;
        this.kind = kind;
        this.label = label;
        this.rawText = rawText;
        this.unit = unit;
        this.reference = reference;
        this.origin = origin;
        this.version = version;
        this.metadata = metadata;
    }

    public Physical physical() {
        return new Physical(rawText, unit, reference);
    }
}
