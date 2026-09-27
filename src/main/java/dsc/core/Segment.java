package dsc.core;

import java.util.List;

/** A contiguous heating or cooling run. Heating and cooling are never mixed. */
public record Segment(int index, Kind kind, int cycle, List<DscPoint> points) {

    public enum Kind { HEATING, COOLING }

    public String label() {
        return kind + "#" + cycle;
    }

    public double startTempC() {
        return points.get(0).tempC();
    }

    public double endTempC() {
        return points.get(points.size() - 1).tempC();
    }

    /** Mean measured scan rate in K/min over this segment. */
    public double measuredRateKPerMin() {
        double dt = points.get(points.size() - 1).timeS() - points.get(0).timeS();
        double dT = endTempC() - startTempC();
        return dt == 0 ? 0 : dT / dt * 60.0;
    }
}
