package dsc.model;

import java.util.List;

/**
 * 一个扫描段。方向（吸/放热定义）只来自文件头声明的 signConvention，
 * 绝不来自文件名。加热段与冷却段永远是不同 Segment，不混用。
 */
public record Segment(
        String id,
        String runId,
        SegmentKind kind,
        int cycle,
        String signConvention,   // "exo-up" | "endo-up" | "unknown"
        Quantity scanRate,       // K/min
        String atmosphere,
        List<DataPoint> points
) {}
