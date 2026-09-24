package org.materials.dsc.domain;

/** 扫描速率（带符号语义由 direction 承担，速率本身为正数带单位）。 */
public record RateRecord(String id, Quantity rate, String rawText, String eventSeq, String at) {
}
