package org.materials.dsc.domain;

/** 样品质量版本：每次修订产生新版本，旧版本保留。 */
public record MassRecord(String id, int version, Quantity mass, String note, String eventSeq, String at) {
}
