package dsc.core;

/**
 * One archived sample-mass record. A new mass version invalidates every
 * mass-normalized derivation that depends on the previous version.
 */
public record MassVersion(String id, String sampleId, int version,
                          Qty mass, String recordedAt, String note) {
}
