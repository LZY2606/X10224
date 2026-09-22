package dsc.model;

/** 样品质量，独立存档、独立版本。 */
public record MassRecord(String sampleId, int version, Quantity mass) {}
