package org.materials.dsc.domain;

/** 气氛记录。 */
public record AtmosphereRecord(String id, String gas, Quantity flowRate, String rawText,
                               String eventSeq, String at) {
}
