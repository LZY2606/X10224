package org.materials.dsc.domain;

/** 样品履历（case）：同一材料加热/冷却序列放在同一份履历中。 */
public record CaseInfo(String id, String name, String material, String createdAt, String note) {
}
