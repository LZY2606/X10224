package dsc.domain;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 仪器输出曲线：保留原始文本与原始行，派生点列才做规范化。 */
public final class Trace {

    public record Point(double timeS, double tempC, double heatFlowMw) {}

    /** 无法转换的行：留在原位置（行号 + 原文）并附诊断。 */
    public record Diagnostic(int lineNo, String rawLine, String message) {}

    public final Map<String, String> metadata = new LinkedHashMap<>();
    public final List<Point> points = new ArrayList<>();
    public final List<Diagnostic> diagnostics = new ArrayList<>();
    public String rawText = "";

    /** 温度间隔是否非等间隔（允许，积分用梯形法按时间轴处理）。 */
    public boolean nonUniformTemperature() {
        if (points.size() < 3) return false;
        double d0 = Math.abs(points.get(1).tempC() - points.get(0).tempC());
        for (int i = 2; i < points.size(); i++) {
            double d = Math.abs(points.get(i).tempC() - points.get(i - 1).tempC());
            if (Math.abs(d - d0) > 1e-9 * Math.max(1.0, d0)) return true;
        }
        return false;
    }
}
