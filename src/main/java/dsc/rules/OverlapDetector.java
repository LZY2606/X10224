package dsc.rules;

import java.util.ArrayList;
import java.util.List;

/**
 * Rule OVERLAP-1: windows of the same segment may overlap (e.g. a melting
 * doublet). Overlapping transitions are flagged — never silently merged;
 * every legal candidate stays available for the reviewer to compare.
 */
public final class OverlapDetector {

    private OverlapDetector() {}

    public record Overlap(String a, String b, double loTK, double hiTK) {}

    public record Window(String transitionId, double loTK, double hiTK) {}

    public static List<Overlap> findOverlaps(List<Window> windows) {
        List<Overlap> out = new ArrayList<>();
        for (int i = 0; i < windows.size(); i++) {
            for (int j = i + 1; j < windows.size(); j++) {
                Window a = windows.get(i);
                Window b = windows.get(j);
                double lo = Math.max(a.loTK, b.loTK);
                double hi = Math.min(a.hiTK, b.hiTK);
                if (lo < hi - 1e-9) {
                    out.add(new Overlap(a.transitionId(), b.transitionId(), lo, hi));
                }
            }
        }
        return out;
    }
}
