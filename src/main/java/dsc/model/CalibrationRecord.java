package dsc.model;

import dsc.units.Quantity;

public record CalibrationRecord(String id, int version, Quantity offsetK, Quantity referenceK, String standard,
                                String eventId) {
}
