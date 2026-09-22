package org.materials.dsc.domain;

import java.util.List;

/** 规范化序列节点载荷。specificHeatFlow 在质量不可转换时为 null。 */
public record SeriesPayload(
        String runId,
        String sampleId,
        String signConvention,
        Double massMilligram,
        String massNodeId,
        Double calibrationOffsetKelvin,
        List<CanonicalPoint> points,
        List<String> diagnostics) {
}
