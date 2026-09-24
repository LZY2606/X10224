package dsc.domain;

/**
 * 分析区间，固定在单一扫描段上（绝不跨加热/冷却段）。
 * 曲线上可拖动修改，每次修改产生新版本。
 */
public record AnalysisWindow(
        String id,
        String segmentId,
        int version,
        double fromC,
        double toC,
        String actor,
        String comment
) {}
