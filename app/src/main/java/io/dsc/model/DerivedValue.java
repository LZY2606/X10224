package io.dsc.model;

/**
 * One displayed number with a stable identity. Survives export/delete/reimport.
 * validity is recomputed by the dependency graph; stale values keep their old
 * identity but are marked EXPIRED rather than overwritten in place.
 */
public record DerivedValue(
        StableId id,
        String label,
        double value,
        String unit,
        String displayUnit,
        String displayValue,
        String rawRepresentation,
        Validity validity,
        String expiredBecause,
        Derivation derivation
) {
    public enum Validity { CURRENT, SUPERSEDED, EXPIRED, UNRESOLVED }

    public DerivedValue withValidity(Validity validity, String because) {
        return new DerivedValue(id, label, value, unit, displayUnit, displayValue, rawRepresentation,
                validity, because, derivation);
    }
}
