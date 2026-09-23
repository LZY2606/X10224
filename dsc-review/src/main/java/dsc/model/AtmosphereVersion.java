package dsc.model;

/** One versioned atmosphere statement for a sequence. */
public record AtmosphereVersion(String atmosphereId, String sequenceId, String gas, double flowMlPerMin, int revision) {}
