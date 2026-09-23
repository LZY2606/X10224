package org.matlab.dsc.domain;

/**
 * One sample's full thermal history: every heating and cooling cycle belongs
 * to the same history, which is the unit of review.
 */
public record SampleHistory(String id, String name, String material, String createdAt) {
}
