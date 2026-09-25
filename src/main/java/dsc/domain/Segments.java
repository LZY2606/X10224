package dsc.domain;

import java.util.ArrayList;
import java.util.List;

/**
 * Splits a raw run into heating/cooling segments at temperature turning
 * points (cycle switching). Segment kind is derived from the measured
 * temperature program, never from the file name. Heating and cooling points
 * are never mixed into one segment.
 */
public final class Segments {

    public enum Kind { HEATING, COOLING }

    public static final class Segment {
        public final Kind kind;
        public final int seq;
        public final int fromIndex; // inclusive, into InstrumentFile.points
        public final int toIndex;   // inclusive

        public Segment(Kind kind, int seq, int fromIndex, int toIndex) {
            this.kind = kind;
            this.seq = seq;
            this.fromIndex = fromIndex;
            this.toIndex = toIndex;
        }

        public int size() {
            return toIndex - fromIndex + 1;
        }
    }

    /** Minimum points on one side of a turning point before a split is accepted. */
    public static final int MIN_SPAN = 5;

    public static List<Segment> split(InstrumentFile f) {
        List<Segment> out = new ArrayList<>();
        List<Integer> idx = new ArrayList<>();
        for (int i = 0; i < f.points.size(); i++) {
            if (f.points.get(i).usable()) idx.add(i);
        }
        if (idx.size() < 2 * MIN_SPAN) {
            if (!idx.isEmpty()) {
                Kind k = kindOf(f, idx.get(0), idx.get(idx.size() - 1));
                out.add(new Segment(k, 0, idx.get(0), idx.get(idx.size() - 1)));
            }
            return out;
        }
        int segStart = idx.get(0);
        int direction = 0; // +1 heating, -1 cooling
        int seq = 0;
        int run = 0;
        for (int k = 1; k < idx.size(); k++) {
            double d = f.points.get(idx.get(k)).tempC - f.points.get(idx.get(k - 1)).tempC;
            int s = d > 1e-9 ? 1 : (d < -1e-9 ? -1 : 0);
            if (s == 0) continue;
            if (direction == 0) {
                direction = s;
                run = 1;
                continue;
            }
            if (s == direction) {
                run++;
            } else {
                // potential turning point: only split if the new direction persists
                int ahead = 0;
                for (int j = k; j < idx.size() && ahead < MIN_SPAN; j++) {
                    double dd = f.points.get(idx.get(j)).tempC - f.points.get(idx.get(j - 1)).tempC;
                    if ((dd > 1e-9 ? 1 : (dd < -1e-9 ? -1 : 0)) == s) ahead++;
                    else break;
                }
                if (ahead >= MIN_SPAN && run >= MIN_SPAN) {
                    out.add(new Segment(direction > 0 ? Kind.HEATING : Kind.COOLING,
                            seq++, segStart, idx.get(k - 1)));
                    segStart = idx.get(k - 1);
                    direction = s;
                    run = 1;
                }
            }
        }
        out.add(new Segment(direction > 0 ? Kind.HEATING : Kind.COOLING,
                seq, segStart, idx.get(idx.size() - 1)));
        return out;
    }

    private static Kind kindOf(InstrumentFile f, int a, int b) {
        double d = f.points.get(b).tempC - f.points.get(a).tempC;
        return d >= 0 ? Kind.HEATING : Kind.COOLING;
    }
}
