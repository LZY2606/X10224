package dsc.model;

import java.util.List;

/**
 * 温度程序中的一段（一次加热或一次冷却）。同一样品履历内可有多个段，
 * 加热段与冷却段的基线、相变分别计算，绝不混用。
 */
public record Segment(String segmentId,
                      int cycle,
                      ScanDirection direction,
                      Quantity scanRate,
                      double scanRateKPerMin,
                      HeatFlowSignConvention signConvention,
                      String atmosphere,
                      List<SamplePoint> points) {

    public Segment {
        points = List.copyOf(points);
    }

    /** 单调温度校验：加热段必须整体升温，冷却段整体降温，允许等温保持。 */
    public boolean temperatureMonotonic() {
        int expected = direction.sign();
        if (expected == 0) {
            return true;
        }
        for (int i = 1; i < points.size(); i++) {
            double delta = points.get(i).tempKelvin() - points.get(i - 1).tempKelvin();
            if (expected > 0 && delta < -1e-9) {
                return false;
            }
            if (expected < 0 && delta > 1e-9) {
                return false;
            }
        }
        return true;
    }
}
