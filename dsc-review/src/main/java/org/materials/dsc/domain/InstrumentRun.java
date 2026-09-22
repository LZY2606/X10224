package org.materials.dsc.domain;

import java.util.List;

/**
 * 一次仪器输出（原始文件）。exoUp 直接来自仪器头元数据，绝不从文件名猜测。
 * scanDirection 来自显式字段 heating/cooling；文件名不参与判定。
 */
public record InstrumentRun(
        String id,
        String sampleId,
        String fileName,
        List<SeriesPoint> points,
        Quantity scanRate,
        Atmosphere atmosphere,
        boolean exoUp,
        String directionSource,
        String createdAtIso,
        List<String> diagnostics) {

    /** 显式的扫描方向：heating / cooling / unknown。 */
    public String declaredDirection() {
        return directionSource == null ? "unknown" : directionSource;
    }
}
