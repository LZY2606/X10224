package dsc.domain;

import java.util.List;

/**
 * 一段加热/冷却扫描，属于某个样品履历与循环。
 * signConvention: 仪器热流正方向约定 "endo_up"（吸热为正）或 "exo_up"（放热为正），
 * 与文件名无关；缺失时方向判定为未定。
 */
public record Segment(
        String id,
        int cycle,
        String instrument,
        String declaredDirection,   // 文件名/表头里的说法，仅作记录不参与判定
        String signConvention,      // endo_up / exo_up / unknown
        double rateKPerMin,         // 带符号
        Direction direction,
        List<SamplePoint> points
) {
    public int pointCount() { return points == null ? 0 : points.size(); }
}
