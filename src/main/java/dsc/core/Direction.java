package dsc.core;

import java.util.ArrayList;
import java.util.List;

/** Scan direction is derived from the temperature/time data itself, never from file names. */
public enum Direction {
    HEATING, COOLING, ISOTHERMAL, UNKNOWN;

    /** Robust direction from median of per-sample temperature slopes. */
    public static Direction detect(List<DscMath.Point> pts) {
        if (pts.size() < 2) return UNKNOWN;
        List<Double> slopes = new ArrayList<>();
        for (int i = 1; i < pts.size(); i++) {
            double dt = pts.get(i).tSec() - pts.get(i - 1).tSec();
            if (dt <= 0) continue;
            double dT = pts.get(i).tempC() - pts.get(i - 1).tempC();
            slopes.add(dT / dt);
        }
        if (slopes.isEmpty()) return UNKNOWN;
        slopes.sort(Double::compare);
        double med = slopes.get(slopes.size() / 2);
        if (med > 1e-6) return HEATING;
        if (med < -1e-6) return COOLING;
        return ISOTHERMAL;
    }

    /** Split a run into segments wherever the scan direction flips (cycle switching).
     *  A flip is accepted only when the new direction persists for at least 3 samples. */
    public static List<List<DscMath.Point>> splitCycles(List<DscMath.Point> pts) {
        List<List<DscMath.Point>> out = new ArrayList<>();
        if (pts.isEmpty()) return out;
        List<DscMath.Point> cur = new ArrayList<>();
        int sign = 0; // +1 heating, -1 cooling
        int pendingSign = 0, pendingCount = 0;
        for (int i = 0; i < pts.size(); i++) {
            if (i > 0) {
                double dT = pts.get(i).tempC() - pts.get(i - 1).tempC();
                int s = dT > 1e-9 ? 1 : dT < -1e-9 ? -1 : 0;
                if (s != 0) {
                    if (sign == 0) { sign = s; }
                    else if (s != sign) {
                        if (pendingSign == s) pendingCount++;
                        else { pendingSign = s; pendingCount = 1; }
                        if (pendingCount >= 3) {
                            // cut: the last (pendingCount) points belong to the new segment
                            List<DscMath.Point> next = new ArrayList<>();
                            for (int k = cur.size() - pendingCount + 1; k < cur.size(); k++) next.add(cur.get(k));
                            while (pendingCount-- > 1) cur.remove(cur.size() - 1);
                            if (!cur.isEmpty()) out.add(cur);
                            cur = next;
                            sign = s;
                            pendingSign = 0; pendingCount = 0;
                        }
                    } else {
                        pendingSign = 0; pendingCount = 0;
                    }
                }
            }
            cur.add(pts.get(i));
        }
        if (!cur.isEmpty()) out.add(cur);
        return out;
    }
}
