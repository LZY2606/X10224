package dsc.model;

/** 温度标定，独立存档、独立版本。offsetC 加到原始温度上。 */
public record Calibration(String sampleId, int version, double offsetC, String rawNote) {}
