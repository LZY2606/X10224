package dsc.model;

import dsc.qty.Qty;

/**
 * The separately archived scalar inputs. "Separately archived" means each is a
 * versioned object with its own provenance; a revision to one does not rewrite
 * the others.
 */
public sealed interface SourceRecord
        permits SourceRecord.Mass, SourceRecord.ScanRate, SourceRecord.Atmosphere, SourceRecord.Calibration {

    String id();

    int version();

    String sampleId();

    record Mass(String id, int version, String sampleId, Qty value, String reason, String importedAt)
            implements SourceRecord {}

    record ScanRate(String id, int version, String sampleId, int cycleIndex, Direction direction,
                    Qty value, String importedAt) implements SourceRecord {}

    record Atmosphere(String id, int version, String sampleId, String gas, Qty flowRate,
                      String importedAt) implements SourceRecord {}

    /** Temperature calibration anchor, e.g. an indium melting check on ITS-90. */
    record Calibration(String id, int version, String sampleId, String standard,
                       Qty nominal, Qty measured, String basis, String importedAt)
            implements SourceRecord {}
}
