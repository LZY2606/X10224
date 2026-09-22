package dsc.engine;

import dsc.domain.ScanSegment;

import java.util.ArrayList;
import java.util.List;

/**
 * 按实测时间-温度轨迹切分扫描段：加热/冷却方向由回归斜率决定，
 * 不读取文件名或声明字段。循环段切换在此处自然拆成多段。
 */
public final class Segmenter {

    private static final int MIN_POINTS = 5;

    private Segmenter() {
    }

    /**
     * @param points 列：[t(s), T(K), heatflow(W)...]，温度已规范化
     */
    public static List<ScanSegment> segments(double[][] points) {
        List<ScanSegment> out = new ArrayList<>();
        int n = points.length;
        if (n < MIN_POINTS) {
            return out;
        }
        int runStart = 1;
        int prevSign = sign(slope(points, 0, 2));
        for (int i = 1; i < n - 1; i++) {
            int sign = sign(slope(points, i, 2));
            if (sign == 0) {
                continue;
            }
            if (prevSign == 0) {
                prevSign = sign;
            }
            if (sign != prevSign) {
                maybeAdd(out, points, runStart - 1, i - 1);
                runStart = i;
                prevSign = sign;
            }
        }
        maybeAdd(out, points, Math.max(0, runStart - 1), n - 1);
        return out;
    }

    private static void maybeAdd(List<ScanSegment> out, double[][] points, int lo, int hi) {
        int count = hi - lo + 1;
        if (count < MIN_POINTS) {
            return;
        }
        double dt = points[hi][0] - points[lo][0];
        double slope = dt == 0 ? 0 : (points[hi][1] - points[lo][1]) / dt;
        boolean heating = slope >= 0;
        boolean nonuniform = nonuniform(points, lo, hi);
        int index = out.size();
        out.add(new ScanSegment(index, lo, hi, points[lo][1], points[hi][1],
                heating, Math.abs(slope), nonuniform));
    }

    /** 邻域 5 点回归斜率。 */
    static double slope(double[][] p, int center, int half) {
        int lo = Math.max(0, center - half);
        int hi = Math.min(p.length - 1, center + half);
        double sx = 0, sy = 0, sxx = 0, sxy = 0;
        int n = hi - lo + 1;
        for (int i = lo; i <= hi; i++) {
            double x = p[i][0];
            double y = p[i][1];
            sx += x;
            sy += y;
            sxx += x * x;
            sxy += x * y;
        }
        double denom = n * sxx - sx * sx;
        return denom == 0 ? 0 : (n * sxy - sx * sy) / denom;
    }

    private static int sign(double v) {
        double noise = 1e-9;
        if (v > noise) {
            return 1;
        }
        if (v < -noise) {
            return -1;
        }
        return 0;
    }

    /**
     * 非等间隔判定：温度步长的变异系数超过 25%，或存在时间间隔异常点。
     * 等间隔仪器数据的典型 CV 接近 0。
     */
    static boolean nonuniform(double[][] points, int lo, int hi) {
        int m = hi - lo;
        if (m < 3) {
            return false;
        }
        double[] dT = new double[m];
        double mean = 0;
        for (int i = lo; i < hi; i++) {
            dT[i - lo] = Math.abs(points[i + 1][1] - points[i][1]);
            mean += dT[i - lo];
        }
        mean /= m;
        if (mean <= 0) {
            return false;
        }
        double var = 0;
        double maxGap = 0;
        for (double v : dT) {
            var += (v - mean) * (v - mean);
            maxGap = Math.max(maxGap, v);
        }
        double cv = Math.sqrt(var / m) / mean;
        return cv > 0.25 || maxGap > 3.0 * mean;
    }
}
