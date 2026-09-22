package org.materials.dsc.domain;

import java.util.List;

/**
 * 循环履历中的一段：严格属于一次加热或一次冷却。段方向由数据显式给出，
 * 加热与冷却段永不混用（基线/积分都在单段内进行）。
 */
public record ScanSegment(
        String id,
        String runId,
        String sampleId,
        int cycle,
        String direction,
        int fromIndex,
        int toIndex,
        String createdAtIso) {

    public List<SeriesPoint> slice(List<SeriesPoint> points) {
        return List.copyOf(points.subList(fromIndex, toIndex + 1));
    }
}
