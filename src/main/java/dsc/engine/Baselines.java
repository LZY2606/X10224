package dsc.engine;

import dsc.domain.BaselineCandidate;
import dsc.domain.Feature;
import dsc.domain.FeatureKind;
import dsc.domain.Ids;

import java.util.ArrayList;
import java.util.List;

/**
 * 为一个转折生成全部合理基线候选并保留差异：
 * <ul>
 *   <li>tangent：用户拖动区间两端的切线（线性）</li>
 *   <li>step：玻璃化的阶跃 S 形基线</li>
 *   <li>overlap：重叠转折时的局部谷点分离基线</li>
 * </ul>
 * 无法唯一决定时返回多个合法候选，绝不吞掉多解。
 */
public final class Baselines {

    private Baselines() {
    }

    public static List<BaselineCandidate> forFeature(Feature feature,
                                                     double[][] points, int hfCol,
                                                     boolean overlappingNext) {
        List<BaselineCandidate> candidates = new ArrayList<>();
        int onsetIdx = nearest(points, feature.onsetT());
        int endIdx = nearest(points, feature.endsetT());
        int[] left = window(points, onsetIdx, -1, 6);
        int[] right = window(points, endIdx, +1, 6);

        double[] leftFit = linearFit(points, left[0], left[1], hfCol);
        double[] rightFit = linearFit(points, right[0], right[1], hfCol);

        double[] anchorLine = chordFit(points, onsetIdx, endIdx, hfCol);
        double anchorRms = residualRms(points, onsetIdx, endIdx, hfCol, anchorLine);
        candidates.add(new BaselineCandidate(
                Ids.candidate(feature.id(), "tangent", 0),
                feature.id(), "tangent", "两端切线连线",
                anchorLine[0], anchorLine[1], null,
                List.of(new double[]{feature.onsetT(), points[onsetIdx][hfCol]},
                        new double[]{feature.endsetT(), points[endIdx][hfCol]}),
                anchorRms, false,
                "由用户选定区间两端切线交点构成，对重叠敏感"));

        double[] leftLine = leftFit;
        double leftRms = residualRms(points, left[0], left[1], hfCol, leftLine);
        candidates.add(new BaselineCandidate(
                Ids.candidate(feature.id(), "tangent", 1),
                feature.id(), "pre-tangent", "前段切线外推",
                leftLine[0], leftLine[1], null,
                List.of(new double[]{points[left[0]][1], points[left[0]][hfCol]},
                        new double[]{points[left[1]][1], points[left[1]][hfCol]}),
                leftRms, false,
                "仅使用前段稳定基线，适用于后缘被下一转折干扰的情形"));

        if (feature.kind() == FeatureKind.GLASS) {
            double[] step = stepFit(points, onsetIdx, endIdx, hfCol, leftFit, rightFit);
            double[] stepParams = {points[onsetIdx][1], points[endIdx][1],
                    leftFit[0], leftFit[1], rightFit[0], rightFit[1], step[0]};
            double stepRms = stepResidualRms(points, onsetIdx, endIdx, hfCol, stepParams);
            candidates.add(new BaselineCandidate(
                    Ids.candidate(feature.id(), "step", 0),
                    feature.id(), "step", "阶跃 S 形基线",
                    0, 0, stepParams,
                    List.of(new double[]{feature.onsetT(), 0},
                            new double[]{feature.endsetT(), 0}),
                    stepRms, false,
                    "玻璃化是比热台阶而非峰，阶跃模型是结构化候选，参数随候选存档"));
        }
        if (overlappingNext) {
            int valley = localMinIndex(points, endIdx, hfCol);
            double[] split = chordFit(points, onsetIdx, valley, hfCol);
            double splitRms = residualRms(points, onsetIdx, valley, hfCol, split);
            candidates.add(new BaselineCandidate(
                    Ids.candidate(feature.id(), "overlap", 0),
                    feature.id(), "overlap", "局部谷点分离基线",
                    split[0], split[1], null,
                    List.of(new double[]{points[onsetIdx][1], points[onsetIdx][hfCol]},
                            new double[]{points[valley][1], points[valley][hfCol]}),
                    splitRms, false,
                    "与下一转折重叠时在局部谷点切分，避免把邻居面积计入"));
        }
        return candidates;
    }

    public static double[] linearFit(double[][] points, int lo, int hi, int col) {
        double sx = 0, sy = 0, sxx = 0, sxy = 0;
        int n = hi - lo + 1;
        for (int i = lo; i <= hi; i++) {
            double x = points[i][1];
            double y = points[i][col];
            sx += x;
            sy += y;
            sxx += x * x;
            sxy += x * y;
        }
        double denom = n * sxx - sx * sx;
        double a = denom == 0 ? 0 : (n * sxy - sx * sy) / denom;
        double b = (sy - a * sx) / n;
        return new double[]{a, b};
    }

    private static double[] chordFit(double[][] points, int i, int j, int col) {
        double x1 = points[i][1], y1 = points[i][col];
        double x2 = points[j][1], y2 = points[j][col];
        double a = x2 == x1 ? 0 : (y2 - y1) / (x2 - x1);
        return new double[]{a, y1 - a * x1};
    }


    private static double[] stepFit(double[][] points, int onset, int end, int col,
                                    double[] leftFit, double[] rightFit) {
        double residual = 0;
        for (int i = onset; i <= end; i++) {
            double t = points[i][1];
            double mid = (points[onset][1] + points[end][1]) / 2;
            double half = Math.max(1e-9, (points[end][1] - points[onset][1]) / 2);
            double f = 0.5 + 0.5 * ((t - mid) / half)
                    / Math.sqrt(1 + ((t - mid) / half) * ((t - mid) / half));
            residual += points[i][col] - (leftFit[0] * t + leftFit[1]) * (1 - f)
                    - (rightFit[0] * t + rightFit[1]) * f;
        }
        return new double[]{residual / Math.max(1, end - onset + 1)};
    }

    static double stepResidualRms(double[][] points, int lo, int hi, int col, double[] p) {
        double sum = 0;
        int n = 0;
        for (int i = lo; i <= hi; i++) {
            double t = points[i][1];
            double mid = (p[0] + p[1]) / 2;
            double half = Math.max(1e-9, (p[1] - p[0]) / 2);
            double u = (t - mid) / half;
            double f = 0.5 + 0.5 * u / Math.sqrt(1 + u * u);
            double left = (p[2] * t + p[3]) * (1 - f);
            double right = (p[4] * t + p[5]) * f;
            double model = left + right + p[6];
            double r = points[i][col] - model;
            sum += r * r;
            n++;
        }
        return n == 0 ? 0 : Math.sqrt(sum / n);
    }

    private static double residualRms(double[][] points, int lo, int hi, int col, double[] line) {
        double sum = 0;
        int n = 0;
        for (int i = lo; i <= hi; i++) {
            double r = points[i][col] - (line[0] * points[i][1] + line[1]);
            sum += r * r;
            n++;
        }
        return n == 0 ? 0 : Math.sqrt(sum / n);
    }

    private static int[] window(double[][] points, int anchor, int dir, int size) {
        int lo = anchor, hi = anchor;
        if (dir < 0) {
            lo = Math.max(1, anchor - size);
            hi = Math.max(lo, anchor - 1);
        } else {
            hi = Math.min(points.length - 2, anchor + size);
            lo = Math.min(hi, anchor + 1);
        }
        return new int[]{lo, hi};
    }

    private static int nearest(double[][] points, double t) {
        int best = 0;
        double bestD = Double.MAX_VALUE;
        for (int i = 0; i < points.length; i++) {
            double d = Math.abs(points[i][1] - t);
            if (d < bestD) {
                bestD = d;
                best = i;
            }
        }
        return best;
    }

    private static int localMinIndex(double[][] points, int from, int col) {
        int best = from;
        double bestV = points[from][col];
        int limit = Math.min(points.length - 2, from + 12);
        for (int i = from; i <= limit; i++) {
            if (points[i][col] < bestV) {
                bestV = points[i][col];
                best = i;
            }
        }
        return best;
    }
}
