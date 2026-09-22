package dsc.rules;

import dsc.model.Events;
import dsc.model.Series;
import dsc.model.SegmentDirection;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Rules about segments: direction mixing, cycle switching, overlap, reversed flow. */
public final class SegmentRules {

    private SegmentRules() {}

    /**
     * A baseline/integration interval must stay inside one segment of one
     * cycle. Heating and cooling segments are never mixed.
     */
    public static Map<String, Object> checkIntervalScope(
            String intervalId, List<String> segmentIds,
            List<SegmentDirection> directions, List<Integer> cycles) {
        var dirs = new java.util.HashSet<>(directions);
        var cyc = new java.util.HashSet<>(cycles);
        if (dirs.size() > 1 || cyc.size() > 1) {
            return Map.of(
                    "type", Events.SEGMENT_MIX_REJECTED,
                    "intervalId", intervalId,
                    "segmentIds", segmentIds,
                    "reason", "heating and cooling segments (or different cycles) are never mixed into one baseline/integration");
        }
        return null;
    }

    /** Detect a cycle switch between consecutive segments of one sample. */
    public static Map<String, Object> detectCycleSwitch(int fromCycle, int toCycle,
                                                        SegmentDirection from, SegmentDirection to) {
        if (fromCycle == toCycle && from == to) return null;
        return Map.of(
                "type", Events.CYCLE_SWITCH,
                "fromCycle", fromCycle, "toCycle", toCycle,
                "fromDirection", from.name(), "toDirection", to.name());
    }

    /**
     * Reversed heat flow: the dominant excursion sign contradicts the declared
     * convention for a known transition type. Values are kept as recorded;
     * only a diagnostic event is emitted.
     */
    public static Map<String, Object> detectReversedHeatFlow(Series s, String signConvention,
                                                             SegmentDirection dir, double t0, double t1) {
        if (signConvention == null) return null;
        double dev = 0;
        int n = 0;
        double mid = (t0 + t1) / 2;
        for (int i = 0; i < s.size(); i++) {
            double t = s.temp[i];
            if (t < t0 || t > t1) continue;
            dev += s.heatFlow[i] - s.at(mid < t ? t1 : t0);
            n++;
        }
        if (n == 0) return null;
        // For a heating melting peak we expect the endothermic direction.
        boolean expectEndoUp = signConvention.equals("endo_up") && dir == SegmentDirection.HEATING;
        boolean expectExoUp = signConvention.equals("exo_up") && dir == SegmentDirection.HEATING;
        boolean reversed = (expectEndoUp && dev < 0) || (expectExoUp && dev > 0);
        if (!reversed) return null;
        return Map.of(
                "type", Events.HEATFLOW_REVERSED,
                "direction", dir.name(),
                "signConvention", signConvention,
                "reason", "dominant heat-flow excursion opposes the declared sign convention; values kept as recorded");
    }

    /**
     * Overlapping transitions: two significant local maxima of |deviation|
     * inside one interval. Both candidate decompositions are preserved.
     */
    public static List<double[]> detectOverlap(Series s, BaselineEngine.Candidate base,
                                               double t0, double t1) {
        record Peak(double t, double mag) {}
        List<Peak> peaks = new ArrayList<>();
        for (int i = 1; i + 1 < s.size(); i++) {
            double t = s.temp[i];
            if (t <= t0 || t >= t1) continue;
            double dPrev = Math.abs(s.heatFlow[i - 1] - base.at(s.temp[i - 1]));
            double dCur = Math.abs(s.heatFlow[i] - base.at(t));
            double dNext = Math.abs(s.heatFlow[i + 1] - base.at(s.temp[i + 1]));
            if (dCur >= dPrev && dCur > dNext) peaks.add(new Peak(t, dCur));
        }
        if (peaks.size() < 2) return List.of();
        peaks.sort((a, b) -> Double.compare(b.mag(), a.mag()));
        double strongest = peaks.get(0).mag();
        List<Peak> significant = peaks.stream().filter(p -> p.mag() > 0.3 * strongest).toList();
        if (significant.size() < 2) return List.of();
        List<double[]> splits = new ArrayList<>();
        for (int i = 0; i + 1 < significant.size(); i++) {
            double a = significant.get(i).t();
            double b = significant.get(i + 1).t();
            double lo = Math.min(a, b), hi = Math.max(a, b);
            // valley between the two peaks
            double valleyT = lo, valleyDev = Double.MAX_VALUE;
            for (int k = 0; k < s.size(); k++) {
                double t = s.temp[k];
                if (t <= lo || t >= hi) continue;
                double d = Math.abs(s.heatFlow[k] - base.at(t));
                if (d < valleyDev) { valleyDev = d; valleyT = t; }
            }
            splits.add(new double[]{t0, valleyT});
            splits.add(new double[]{valleyT, t1});
        }
        return splits;
    }
}
