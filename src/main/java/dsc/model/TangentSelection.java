package dsc.model;

/** 用户选定的切线锚点，独立存档。 */
public record TangentSelection(String regionId, int version, double leftT, double rightT) {}
