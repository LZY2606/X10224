package dsc.domain;

/**
 * The five non-curve source records that must be archived separately from the
 * instrument curve: sample mass, scan rate, atmosphere, temperature
 * calibration, and the analyst-selected tangents. Each carries its own stable
 * identity and unit; each revision becomes a new version rather than mutating
 * the old one.
 */
public record Metadata(
        String id,
        Quantity mass,
        Quantity scanRate,
        String atmosphere,
        String temperatureCalibration,
        String note
) {
}
