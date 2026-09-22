package dsc.model;

/** 仪器原始数据点：rawT/rawHf 为导入原样，unit 为原始单位。 */
public record DataPoint(String rawT, String rawHf, String tUnit, String hfUnit) {}
