package dsc.core;

/**
 * Archived temperature calibration. {@code offset} is added to archived
 * temperatures (affine shift in K, same numeric value in degC); a revision
 * shifts every temperature-derived result.
 */
public record TemperatureCalibration(String id, String version, Qty offset,
                                     String recordedAt, String note) {
}
