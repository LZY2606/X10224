package dsc.model;

import java.util.List;

/**
 * 一次扫描内的一个温度程序段（加热/冷却/恒温）。
 * declaredMode 来自元数据声明，真实方向由派生层按数据判定。
 */
public record Segment(
        String id,
        String runId,
        int order,
        ScanMode declaredMode,
        List<Point> points,
        List<Diagnostic> diagnostics) {

    public List<Point> validPoints() {
        return points.stream().filter(Point::valid).toList();
    }
}
