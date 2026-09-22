package org.materials.dsc.domain;

/** 段节点载荷。加热/冷却方向显式存储，分析永不跨段混用。 */
public record SegmentPayload(
        ScanSegment segment,
        String runId,
        int pointCount,
        List<String> diagnostics) {
}
