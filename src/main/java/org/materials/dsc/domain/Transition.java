package org.materials.dsc.domain;

import java.util.List;

/**
 * 一个热转折：只属于单一扫描段（加热/冷却不混用）。
 * onset/peak/endset 是稳定身份的显示值；overlapWith 记录与相邻转折的重叠关系。
 */
public record Transition(String id, String traceId, String segmentId,
                         TransitionKind kindHint, TransitionKind resolvedKind,
                         Direction segmentDirection, ThermalSign thermalSign,
                         int loIndex, int peakIndex, int hiIndex,
                         DisplayValue onset, DisplayValue peak, DisplayValue endset,
                         List<String> overlapWith, List<String> baselineCandidateIds,
                         String adoptedBaselineId,
                         String status, String eventSeq, String ruleVersion,
                         List<String> diagnostics) {

    public static final String STATUS_DETECTED = "detected";
    public static final String STATUS_OVERLAPPED = "overlapped";
    public static final String STATUS_ADOPTED = "adopted";
    public static final String STATUS_REJECTED = "rejected";
}
