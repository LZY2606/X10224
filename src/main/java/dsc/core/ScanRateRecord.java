package dsc.core;

/** Archived programmed scan rate (with its own stable identity). */
public record ScanRateRecord(String id, String segmentId, Qty rate,
                             String recordedAt, String note) {
}
