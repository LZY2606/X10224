package dsc.domain;

import dsc.model.SamplePoint;

import java.util.ArrayList;
import java.util.List;

/**
 * 段级检查：扫描方向、等间隔性。
 * 方向只能来自仪器头或数据本身，绝不从文件名推断。
 */
public final class SegmentClassifier {

    public record DirectionResult(String direction, String basis) {}

    public static DirectionResult inferDirection(List<SamplePoint> pts, String headerDirection) {
        if ("heating".equalsIgnoreCase(headerDirection)
                || "cooling".equalsIgnoreCase(headerDirection)) {
            return new DirectionResult(headerDirection.toLowerCase(),
                    "instrument-header:Direction=" + headerDirection.toLowerCase());
        }
        if (pts.size() < 2) return new DirectionResult("unknown", "data:points<2");
        SamplePoint first = pts.get(0);
        SamplePoint last = pts.get(pts.size() - 1);
        if (!first.tempOk() || !last.tempOk()) {
            return new DirectionResult("unknown", "data:endpoint-temperature-unconvertible");
        }
        double net = last.tempC() - first.tempC();
        double eps = 0.5;
        if (net > eps) {
            return new DirectionResult("heating",
                    String.format("data:net-dT=%.2fC(over %d points)", net, pts.size()));
        }
        if (net < -eps) {
            return new DirectionResult("cooling",
                    String.format("data:net-dT=%.2fC(over %d points)", net, pts.size()));
        }
        return new DirectionResult("unknown", "data:net-dT≈0");
    }

    /**
     * 等间隔温度检查。返回是否等间隔（相对容差 1e-3），
     * 非等间隔时给出最偏离的间隔，供误差来源引用。
     */
    public record SpacingResult(boolean equallySpaced, double meanStepC, double maxRelDev,
                                int badGapIndex, double badGapC) {}

    public static SpacingResult checkSpacing(List<SamplePoint> pts) {
        List<Double> steps = new ArrayList<>();
        for (int i = 1; i < pts.size(); i++) {
            SamplePoint a = pts.get(i - 1);
            SamplePoint b = pts.get(i);
            if (a.tempOk() && b.tempOk()) {
                double d = b.tempC() - a.tempC();
                if (Math.abs(d) > 1e-9) steps.add(Math.abs(d));
            }
        }
        if (steps.size() < 2) return new SpacingResult(true, 0, 0, -1, 0);
        double mean = steps.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        double maxDev = 0;
        int badIdx = -1;
        double badGap = 0;
        for (int i = 0; i < steps.size(); i++) {
            double rel = Math.abs(steps.get(i) - mean) / mean;
            if (rel > maxDev) {
                maxDev = rel;
                badIdx = i;
                badGap = steps.get(i);
            }
        }
        return new SpacingResult(maxDev <= 1e-3, mean, maxDev, badIdx, badGap);
    }

    /**
     * 检测序列内循环段切换：温度方向符号发生显著翻转时返回切换点索引，
     * 用于把同一文件中的多个循环切分成独立段。
     */
    public static List<Integer> findCycleSwitchPoints(List<SamplePoint> pts) {
        List<Integer> switches = new ArrayList<>();
        int sign = 0;
        for (int i = 1; i < pts.size(); i++) {
            if (!pts.get(i).tempOk() || !pts.get(i - 1).tempOk()) continue;
            double d = pts.get(i).tempC() - pts.get(i - 1).tempC();
            int s = d > 0.1 ? 1 : d < -0.1 ? -1 : 0;
            if (s != 0 && sign != 0 && s != sign) switches.add(i);
            if (s != 0) sign = s;
        }
        return switches;
    }
}
