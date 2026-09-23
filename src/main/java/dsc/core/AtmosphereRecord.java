package dsc.core;

/** Archived purge-atmosphere record. */
public record AtmosphereRecord(String id, String segmentId, String gas,
                               Qty flow, String recordedAt, String note) {
}
