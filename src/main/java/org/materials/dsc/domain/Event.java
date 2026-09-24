package org.materials.dsc.domain;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * 事件溯源的原子事件。seq 在一个 case 内单调递增；
 * payload 携带该事件的机器可读判定（候选、状态、受影响对象）。
 */
public record Event(long seq, String caseId, String type, String at,
                    String actor, String ruleVersion, JsonNode payload) {

    public static final String CASE_CREATED = "case-created";
    public static final String OUTPUT_ARCHIVED = "instrument-output-archived";
    public static final String MASS_RECORDED = "mass-recorded";
    public static final String MASS_REVISED = "mass-revised";
    public static final String RATE_RECORDED = "rate-recorded";
    public static final String ATMOSPHERE_RECORDED = "atmosphere-recorded";
    public static final String CALIBRATION_RECORDED = "temperature-calibration-recorded";
    public static final String SEGMENTS_DERIVED = "segments-derived";
    public static final String TRANSITIONS_DETECTED = "transitions-detected";
    public static final String BASELINES_DERIVED = "baselines-derived";
    public static final String BASELINE_ADOPTED = "baseline-adopted";
    public static final String BASELINE_REJECTED = "transition-rejected";
    public static final String TANGENT_SELECTED = "tangent-selected";
    public static final String ANALYSIS_DERIVED = "analysis-derived";
    public static final String DERIVED_INVALIDATED = "derived-invalidated";
    public static final String INTERVAL_ADJUSTED = "interval-adjusted";
    public static final String CASE_IMPORTED = "case-imported";
    public static final String DIAGNOSTIC = "diagnostic-raised";
    public static final String CASE_NOTE = "case-note";
}
