package dsc.engine;

import dsc.domain.Feature;
import dsc.domain.FeatureKind;
import dsc.domain.Ids;
import dsc.domain.ScanSegment;

import java.util.ArrayList;
import java.util.List;

/**
 * 在单个扫描段内识别玻璃化（台阶）与峰（结晶/熔融）。
 * 峰的起止采用经典切线法：前/后缘最大斜率切线与基线交点。
 * 相邻峰区间重叠时标记 {@code overlapping}，基线阶段生成分离候选。
 */
public final class FeatureDetector {

    private static final int SMOOTH_HALF = 3;
    private static final int GUARD = 4;

    private FeatureDetector() {
    }

    public record Detection(String kind, int center, boolean peakSignPositive) {
    }

    /** 原始检测（未算基线/焓变）。hints 来自仪器档案中的事件提示，仅用于交叉核对。 */
    public static List<Feature> detect(String runId, double[][] points, int hfCol,
                                       ScanSegment segment, List<String> hints,
                                       long eventSeq) {
        List<Feature> features = new ArrayList<>();
        double[] y = Signal.smooth(Signal.column(points, hfCol), SMOOTH_HALF);
        double[] d = Signal.smooth(Signal.derivative(points, hfCol), SMOOTH_HALF);
        double noise = Signal.noiseLevel(y, segment.startIndex(),
                Math.min(segment.startIndex() + 20, segment.endIndex()));
        double threshold = Math.max(noise * 6, 1e-4);

        List<int[]> peaks = findExtrema(y, segment, threshold);
        List<int[]> steps = findSteps(d, segment, threshold);

        int rank = 0;
        List<int[]> ordered = new ArrayList<>();
        for (int[] step : steps) {
            ordered.add(new int[]{step[0], 0});
        }
        for (int[] peak : peaks) {
            ordered.add(new int[]{peak[0], peak[1] > 0 ? 1 : -1});
        }
        ordered.sort((a, b) -> Integer.compare(a[0], b[0]));

        for (int[] det : ordered) {
            int center = det[0];
            boolean isStep = det[1] == 0;
            FeatureKind kind;
            if (isStep) {
                kind = FeatureKind.GLASS;
            } else {
                kind = classifyPeak(runId, hints, features.size(), segment, det[1] > 0);
            }
            int[] bounds = isStep
                    ? stepBounds(d, center, segment, threshold)
                    : peakBounds(y, d, center, segment, det[1] > 0, threshold);
            int onsetIdx = bounds[0];
            int endIdx = bounds[1];
            if (endIdx - onsetIdx < 2) {
                continue;
            }
            boolean overlapNext = false;
            for (int[] other : ordered) {
                if (other[0] == center) {
                    continue;
                }
                int[] ob = other[1] == 0
                        ? stepBounds(d, other[0], segment, threshold)
                        : peakBounds(y, d, other[0], segment, other[1] > 0, threshold);
                if (ob[0] < endIdx && ob[1] > onsetIdx) {
                    overlapNext = true;
                }
            }
            List<String> diagnostics = new ArrayList<>();
            if (overlapNext) {
                diagnostics.add("OVERLAPPING_TRANSITIONS:检测到重叠转折，积分必须保留分离基线候选");
            }
            String id = Ids.feature(runId, kind, rank);
            String status = overlapNext ? "ambiguous" : "detected";
            features.add(new Feature(
                    id, runId, kind, rank, segment.index(), segment.heating(),
                    points[onsetIdx][1], points[center][1], points[endIdx][1],
                    y[center] - 0.5 * (y[onsetIdx] + y[endIdx]),
                    points[onsetIdx][hfCol], points[center][hfCol], points[endIdx][hfCol],
                    List.of(), null, null, status, diagnostics, eventSeq));
            rank++;
        }
        return features;
    }

    private static FeatureKind classifyPeak(String runId, List<String> hints, int order,
                                            ScanSegment segment, boolean positive) {
        String want = null;
        for (String hint : hints) {
            String[] parts = hint.trim().split(":", 2);
            if (parts.length == 2 && parts[0].trim().equalsIgnoreCase("peak")) {
                want = parts[1].trim();
            }
        }
        if (want != null) {
            for (FeatureKind kind : FeatureKind.values()) {
                if (kind.code().startsWith(want.toLowerCase().substring(0, 4))
                        || want.toLowerCase().contains(kind.code())) {
                    return kind;
                }
            }
        }
        // 无档案提示时按形态与段方向给出最可能分类，但保留诊断说明依据。
        if (segment.heating()) {
            return positive ? FeatureKind.CRYSTALLIZATION : FeatureKind.MELT;
        }
        return FeatureKind.CRYSTALLIZATION;
    }

    private static List<int[]> findExtrema(double[] y, ScanSegment seg, double threshold) {
        List<int[]> out = new ArrayList<>();
        for (int i = seg.startIndex() + GUARD; i < seg.endIndex() - GUARD; i++) {
            boolean max = y[i] > y[i - 1] && y[i] >= y[i + 1]
                    && y[i] - localBase(y, i, -1) > threshold;
            boolean min = y[i] < y[i - 1] && y[i] <= y[i + 1]
                    && localBase(y, i, -1) - y[i] > threshold;
            if (max) {
                out.add(new int[]{i, 1});
            } else if (min) {
                out.add(new int[]{i, -1});
            }
        }
        return mergeNearby(out, 8);
    }

    private static List<int[]> findSteps(double[] d, ScanSegment seg, double threshold) {
        List<int[]> out = new ArrayList<>();
        double stepThreshold = threshold * 0.15;
        int i = seg.startIndex() + GUARD;
        while (i < seg.endIndex() - GUARD) {
            if (Math.abs(d[i] - d[Math.max(seg.startIndex(), i - 8)]) > stepThreshold
                    && sustainedShift(d, i, seg, stepThreshold)) {
                out.add(new int[]{i, 0});
                i += 12;
            } else {
                i++;
            }
        }
        return out;
    }

    private static boolean sustainedShift(double[] d, int center, ScanSegment seg, double th) {
        int lo = Math.max(seg.startIndex(), center - 8);
        int hi = Math.min(seg.endIndex() - 1, center + 8);
        double before = avg(d, lo, center - 2);
        double after = avg(d, center + 2, hi);
        return Math.abs(after - before) > th;
    }

    private static double avg(double[] d, int lo, int hi) {
        double s = 0;
        int n = 0;
        for (int i = lo; i <= hi; i++) {
            s += d[i];
            n++;
        }
        return n == 0 ? 0 : s / n;
    }

    private static int[] peakBounds(double[] y, double[] d, int center, ScanSegment seg,
                                    boolean positive, double threshold) {
        int onset = center;
        double bestSlope = 0;
        for (int i = seg.startIndex() + 2; i < center; i++) {
            double slopeMag = Math.abs(d[i]);
            if (slopeMag > bestSlope) {
                bestSlope = slopeMag;
                onset = i;
            }
        }
        int end = center;
        bestSlope = 0;
        for (int i = center + 1; i < seg.endIndex() - 2; i++) {
            double slopeMag = Math.abs(d[i]);
            if (slopeMag > bestSlope) {
                bestSlope = slopeMag;
                end = i;
            }
        }
        onset = Math.max(seg.startIndex() + 1, onset - 2);
        end = Math.min(seg.endIndex() - 1, end + 2);
        return new int[]{onset, end};
    }

    private static int[] stepBounds(double[] d, int center, ScanSegment seg, double threshold) {
        int onset = Math.max(seg.startIndex() + 1, center - 6);
        int end = Math.min(seg.endIndex() - 1, center + 6);
        return new int[]{onset, end};
    }

    private static double localBase(double[] y, int i, int dir) {
        int j = Math.max(0, i + dir * 6);
        return y[j];
    }

    private static List<int[]> mergeNearby(List<int[]> extrema, int gap) {
        List<int[]> out = new ArrayList<>();
        for (int[] e : extrema) {
            if (out.isEmpty() || Math.abs(e[0] - out.get(out.size() - 1)[0]) > gap) {
                out.add(e);
            } else if (Math.abs(e[1]) > Math.abs(out.get(out.size() - 1)[1])) {
                out.set(out.size() - 1, e);
            }
        }
        return out;
    }
}
