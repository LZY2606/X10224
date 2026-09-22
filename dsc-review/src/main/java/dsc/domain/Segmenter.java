package dsc.domain;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Splits an instrument run into heating / cooling / isothermal segments from
 * the measured temperature series. A run with a dwell contains two same-kind
 * scan segments separated by an isothermal one, which is what real
 * heat/cool/heat cycle histories look like.
 */
public final class Segmenter {

    private Segmenter() {
    }

    public static final double ISO_K = 0.25;

    public record Segment(
            String runId,
            int index,
            String segmentId,
            ScanDirection direction,
            int fromRow,
            int toRow,
            double tStartK,
            double tEndK,
            double meanRateKPerS
    ) {
        public Map<String, Object> toMap() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("segmentId", segmentId);
            m.put("runId", runId);
            m.put("index", index);
            m.put("direction", direction.code());
            m.put("fromRow", fromRow);
            m.put("toRow", toRow);
            m.put("tStartK", tStartK);
            m.put("tEndK", tEndK);
            m.put("meanRateKPerS", meanRateKPerS);
            return m;
        }
    }

    public static List<Segment> segments(String runId, double[] timeS, Double[] tempK) {
        List<Segment> out = new ArrayList<>();
        int n = tempK.length;
        if (n == 0) {
            return out;
        }
        int start = 0;
        ScanDirection current = null;
        double cumulative = 0.0;
        for (int i = 1; i <= n; i++) {
            boolean end = i == n;
            ScanDirection step = null;
            double delta = 0.0;
            if (!end) {
                if (tempK[i] != null && tempK[i - 1] != null) {
                    delta = tempK[i] - tempK[i - 1];
                    step = Math.abs(delta) < ISO_K
                            ? ScanDirection.ISOTHERMAL
                            : delta > 0 ? ScanDirection.HEATING : ScanDirection.COOLING;
                } else {
                    step = ScanDirection.ISOTHERMAL;
                }
            }
            if (current == null && !end) {
                current = step;
                cumulative = delta;
            }
            boolean boundary = end;
            if (!end && step != current) {
                // Single-point blips do not open a new segment; require the
                // cumulative trend since the last boundary to confirm.
                cumulative += delta;
                if (step == ScanDirection.ISOTHERMAL || current == ScanDirection.ISOTHERMAL
                        || Math.abs(cumulative) >= 2.0 * ISO_K) {
                    boundary = true;
                }
            } else if (!end) {
                cumulative += delta;
            }
            if (boundary) {
                int endRow = i - 1;
                Segment seg = build(runId, out.size(), current == null ? step : current,
                        start, endRow, timeS, tempK);
                if (seg != null) {
                    out.add(seg);
                }
                start = i;
                current = end ? null : step;
                cumulative = end ? 0.0 : delta;
            }
        }
        return out;
    }

    private static Segment build(String runId, int index, ScanDirection dir,
                                 int fromRow, int toRow, double[] timeS, Double[] tempK) {
        if (dir == null || toRow <= fromRow) {
            return null;
        }
        Double t0 = firstValid(tempK, fromRow, toRow, +1);
        Double t1 = firstValid(tempK, toRow, fromRow, -1);
        if (t0 == null || t1 == null) {
            return null;
        }
        double dt = Math.max(1e-9, timeS[toRow] - timeS[fromRow]);
        double rate = (t1 - t0) / dt;
        String id = runId + "/seg" + index;
        return new Segment(runId, index, id, dir, fromRow, toRow, t0, t1, rate);
    }

    private static Double firstValid(Double[] values, int from, int limit, int step) {
        for (int i = from; step > 0 ? i <= limit : i >= limit; i += step) {
            if (values[i] != null) {
                return values[i];
            }
        }
        return null;
    }
}
