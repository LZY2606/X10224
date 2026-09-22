package dsc.rule;

import dsc.model.ScanMode;
import java.util.ArrayList;
import java.util.List;

/**
 * 自动转折探测：仅作为初始种子，用户可在曲线页拖动覆盖。
 * 判据简单且确定（无随机量），便于做成可重复案例。
 */
public final class FeatureDetector {

    public record DetectedFeature(String segmentId, String kind, FeatureWindow window,
                                  double strength, String note) {
    }

    private FeatureDetector() {
    }

    public static List<DetectedFeature> detect(String segmentId, ScanMode mode, List<XY> curve,
                                               double anchorBandK) {
        List<DetectedFeature> out = new ArrayList<>();
        if (curve.size() < 12) {
            return out;
        }
        List<XY> smooth = CurveMath.movingAverage(curve, 2);
        double[] dy = new double[smooth.size()];
        double range = smooth.stream().mapToDouble(XY::y).max().orElse(0)
                - smooth.stream().mapToDouble(XY::y).min().orElse(0);
        if (range <= 0) {
            return out;
        }
        for (int i = 1; i < smooth.size(); i++) {
            dy[i] = (smooth.get(i).y() - smooth.get(i - 1).y())
                    / (smooth.get(i).x() - smooth.get(i - 1).x());
        }

        List<int[]> peakRegions = findDeviationRegions(smooth, mode, 0.10 * range);
        for (int[] r : peakRegions) {
            double lo = smooth.get(r[0]).x();
            double hi = smooth.get(r[1]).x();
            List<XY> inside = CurveMath.slice(smooth, lo, hi);
            boolean overlapping = Baselines.looksOverlapping(inside);
            double strength = peakStrength(smooth, r, mode);
            out.add(new DetectedFeature(segmentId,
                    overlapping ? "overlapping-peaks" : "peak",
                    new FeatureWindow(segmentId, lo, hi), strength,
                    overlapping ? "检出重叠双峰，将并列生成合并/分裂基线候选" : "峰类转折"));
        }

        List<int[]> stepRegions = findStepRegions(smooth, dy, range, segmentId, mode);
        for (int[] r : stepRegions) {
            double lo = smooth.get(r[0]).x();
            double hi = smooth.get(r[1]).x();
            double strength = stepStrength(smooth, r);
            out.add(new DetectedFeature(segmentId, "glass-transition",
                    new FeatureWindow(segmentId, lo, hi), strength, "台阶状转折，按玻璃化处理"));
        }
        return out;
    }

    private static List<int[]> findDeviationRegions(List<XY> smooth, ScanMode mode, double threshold) {
        List<int[]> out = new ArrayList<>();
        int n = smooth.size();
        int start = -1;
        for (int i = 2; i < n - 2; i++) {
            double localBase = 0.5 * (smooth.get(Math.max(0, i - 3)).y()
                    + smooth.get(Math.min(n - 1, i + 3)).y());
            double dev = smooth.get(i).y() - localBase;
            boolean hit = mode == ScanMode.HEATING ? dev > threshold : dev < -threshold;
            if (hit && start < 0) {
                start = Math.max(0, i - 2);
            }
            if (!hit && start >= 0) {
                int end = Math.min(n - 1, i + 1);
                if (end - start >= 2) {
                    out.add(new int[]{start, end});
                }
                start = -1;
            }
        }
        if (start >= 0 && n - 1 - start >= 2) {
            out.add(new int[]{start, n - 1});
        }
        return mergeNearby(out, 2);
    }

    private static List<int[]> findStepRegions(List<XY> smooth, double[] dy, double range,
                                               String segmentId, ScanMode mode) {
        List<int[]> out = new ArrayList<>();
        int n = smooth.size();
        int run = 0;
        int start = -1;
        double sign = mode == ScanMode.HEATING ? 1 : 1;
        for (int i = 3; i < n - 3; i++) {
            double localSlope = dy[i];
            double baseSlope = 0.5 * (dy[Math.max(1, i - 3)] + dy[Math.min(n - 2, i + 3)]);
            boolean elevated = Math.abs(localSlope - baseSlope) * (smooth.get(i).x()
                    - smooth.get(Math.max(0, i - 2)).x()) > 0.04 * range;
            if (elevated) {
                if (start < 0) {
                    start = i - 2;
                }
                run++;
            } else if (start >= 0) {
                if (run >= 3) {
                    out.add(new int[]{start, Math.min(n - 1, i + 2)});
                }
                start = -1;
                run = 0;
            }
        }
        List<int[]> merged = mergeNearby(out, 3);
        List<int[]> filtered = new ArrayList<>();
        for (int[] r : merged) {
            double widthK = smooth.get(r[1]).x() - smooth.get(r[0]).x();
            if (widthK > 2 && widthK < 40) {
                filtered.add(r);
            }
        }
        return filtered.size() > 3 ? List.of() : filtered;
    }

    private static double peakStrength(List<XY> smooth, int[] r, ScanMode mode) {
        double lo = smooth.get(r[0]).y();
        double hi = smooth.get(r[1]).y();
        double extreme = lo;
        for (int i = r[0]; i <= r[1]; i++) {
            extreme = mode == ScanMode.HEATING
                    ? Math.max(extreme, smooth.get(i).y())
                    : Math.min(extreme, smooth.get(i).y());
        }
        double base = 0.5 * (lo + hi);
        return Math.abs(extreme - base);
    }

    private static double stepStrength(List<XY> smooth, int[] r) {
        double before = smooth.get(Math.max(0, r[0] - 3)).y();
        double after = smooth.get(Math.min(smooth.size() - 1, r[1] + 3)).y();
        return Math.abs(after - before);
    }

    private static List<int[]> mergeNearby(List<int[]> regions, int gapPts) {
        List<int[]> out = new ArrayList<>();
        for (int[] r : regions) {
            if (out.isEmpty() || r[0] - out.get(out.size() - 1)[1] > gapPts) {
                out.add(new int[]{r[0], r[1]});
            } else {
                int[] last = out.get(out.size() - 1);
                last[1] = Math.max(last[1], r[1]);
            }
        }
        return out;
    }
}
