package dsc.domain;

import dsc.model.HeatFlowSignConvention;
import dsc.model.SamplePoint;
import dsc.model.Segment;
import dsc.model.TemperatureCalibration;

import java.util.Arrays;

/**
 * 派生层：对单个扫描段应用温度标定与热流符号约定，
 * 得到内部坐标基准——温度 K（标定后）、热流 mW（吸热为正）。
 * 加热段温度升序，冷却段内部反转存储以便统一算法，但保留原始顺序索引。
 */
public final class CalibratedSeries {

    private final Segment segment;
    private final TemperatureCalibration calibration;
    private final double[] tempK;
    private final double[] heatMilliWattEndoPos;
    private final int[] originalOrder;
    private final boolean ascending;

    private CalibratedSeries(Segment segment,
                             TemperatureCalibration calibration,
                             double[] tempK,
                             double[] heat,
                             int[] originalOrder,
                             boolean ascending) {
        this.segment = segment;
        this.calibration = calibration;
        this.tempK = tempK;
        this.heatMilliWattEndoPos = heat;
        this.originalOrder = originalOrder;
        this.ascending = ascending;
    }

    public static CalibratedSeries of(Segment segment, TemperatureCalibration calibration) {
        int n = segment.points().size();
        double[] t = new double[n];
        double[] h = new double[n];
        int[] order = new int[n];
        boolean asc = segment.direction().sign() >= 0;
        HeatFlowSignConvention conv = segment.signConvention();
        for (int i = 0; i < n; i++) {
            SamplePoint p = segment.points().get(i);
            int slot = asc ? i : n - 1 - i;
            t[slot] = calibration.apply(p.tempKelvin());
            h[slot] = conv.toEndoPositive(p.heatFlowMilliWatt());
            order[slot] = i;
        }
        return new CalibratedSeries(segment, calibration, t, h, order, asc);
    }

    public Segment segment() {
        return segment;
    }

    public TemperatureCalibration calibration() {
        return calibration;
    }

    public int size() {
        return tempK.length;
    }

    public double tempK(int i) {
        return tempK[i];
    }

    public double heat(int i) {
        return heatMilliWattEndoPos[i];
    }

    public int originalIndex(int i) {
        return originalOrder[i];
    }

    public boolean ascendingStorage() {
        return ascending;
    }

    public double minTemp() {
        return size() == 0 ? Double.NaN : tempK[0];
    }

    public double maxTemp() {
        return size() == 0 ? Double.NaN : tempK[size() - 1];
    }

    /** 温度间隔是否近似等间隔（相对中位距）。 */
    public boolean isUniformSpacing(double relTolerance) {
        if (tempK.length < 3) {
            return true;
        }
        double[] gaps = new double[tempK.length - 1];
        for (int i = 1; i < tempK.length; i++) {
            gaps[i - 1] = tempK[i] - tempK[i - 1];
            if (gaps[i - 1] <= 0) {
                return false;
            }
        }
        double[] sorted = gaps.clone();
        Arrays.sort(sorted);
        double median = sorted[sorted.length / 2];
        for (double g : gaps) {
            if (Math.abs(g - median) / median > relTolerance) {
                return false;
            }
        }
        return true;
    }

    /** 连续段内温度是否严格递增（标定后）。 */
    public boolean strictlyIncreasing() {
        for (int i = 1; i < tempK.length; i++) {
            if (tempK[i] <= tempK[i - 1]) {
                return false;
            }
        }
        return true;
    }

    /** 在温度坐标上线性插值（内部升序数组）。 */
    public double interpolateHeatAtTemp(double temp) {
        int n = tempK.length;
        if (n == 0) {
            throw new IllegalStateException("empty series");
        }
        if (temp <= tempK[0]) {
            return heatMilliWattEndoPos[0];
        }
        if (temp >= tempK[n - 1]) {
            return heatMilliWattEndoPos[n - 1];
        }
        int lo = 0;
        int hi = n - 1;
        while (hi - lo > 1) {
            int mid = (lo + hi) >>> 1;
            if (tempK[mid] <= temp) {
                lo = mid;
            } else {
                hi = mid;
            }
        }
        double f = (temp - tempK[lo]) / (tempK[hi] - tempK[lo]);
        return heatMilliWattEndoPos[lo] + f * (heatMilliWattEndoPos[hi] - heatMilliWattEndoPos[lo]);
    }
}
