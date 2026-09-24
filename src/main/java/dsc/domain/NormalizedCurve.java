package dsc.domain;

import java.util.List;

/** 归一化曲线（派生层）：温度 °C、热流 mW，吸热为正；不合法点保留并诊断。 */
public record NormalizedCurve(String segmentId, Direction direction,
                              List<NormalizedPoint> points, List<String> diagnostics) {
    public record Point(double tSec, double tempC, double hfMw, boolean valid, String diag) {}
}
