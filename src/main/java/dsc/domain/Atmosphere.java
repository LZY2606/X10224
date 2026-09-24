package dsc.domain;

/** 气氛与流量，分别存档；无法转换的流量留在原值并附诊断。 */
public record Atmosphere(String id, String gas, RawValue flowMlPerMin, String note) {}
