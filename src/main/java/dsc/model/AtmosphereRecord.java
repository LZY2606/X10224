package dsc.model;

public record AtmosphereRecord(String id, int version, String gas, double flowRaw, String flowUnit, String eventId) {
}
