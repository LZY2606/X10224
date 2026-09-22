package dsc.model;

import dsc.units.Quantity;

import java.util.List;

public record MassRecord(String id, int version, Quantity mass, String note, String eventId) {
}
