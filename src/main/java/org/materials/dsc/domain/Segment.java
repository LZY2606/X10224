package org.materials.dsc.domain;

/**
 * 扫描程序段。加热段与冷却段绝不混用：每个段方向固定，转折检测与积分只在段内进行。
 */
public record Segment(String id, String traceId, Direction direction,
                      int startIndex, int endIndex,
                      double tStartCelsius, double tEndCelsius,
                      Quantity rate, String cycleLabel) {
    public boolean containsIndex(int i) {
        return i >= startIndex && i <= endIndex;
    }

    public boolean heating() {
        return direction == Direction.HEATING;
    }

    public boolean cooling() {
        return direction == Direction.COOLING;
    }
}
