package dsc.core;

import dsc.domain.Segment;

/** 温度窗口（K）到数组索引的映射。加热段与冷却段均覆盖整个窗口。 */
final class WindowIndex {
    final int lo;
    final int hi;

    private WindowIndex(int lo, int hi) {
        this.lo = lo;
        this.hi = hi;
    }

    static WindowIndex of(Segment seg, double tStartK, double tEndK) {
        double a = Math.min(tStartK, tEndK);
        double b = Math.max(tStartK, tEndK);
        int lo = -1;
        int hi = -1;
        for (int i = 0; i < seg.size(); i++) {
            if (!seg.good[i]) {
                continue;
            }
            if (seg.xK[i] >= a && seg.xK[i] <= b) {
                if (lo < 0) {
                    lo = i;
                }
                hi = i;
            }
        }
        return new WindowIndex(lo, hi);
    }
}
