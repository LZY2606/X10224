package dsc.domain;

import java.util.ArrayList;
import java.util.List;

/**
 * 一次加热或冷却扫描段。加热与冷却段绝不混用：每段有独立方向、速率、气氛、温度标定。
 * 温度坐标允许非等间隔（x 单调即可，不做均匀重采样）。
 *
 * 数据三层表示：
 *  - 原始字符串（rawX/rawY）逐点保留；
 *  - 规范化坐标 xK（开尔文）、yWg（W/g，内部“吸热为正”约定）；
 *  - 无法规范化的点保留原文与诊断，进入 badPoints 而不是被丢弃。
 */
public final class Segment {
    public final String segmentId;
    public final int cycleIndex;
    public final String mode;                 // "HEATING" | "COOLING"
    public final String tempUnit;             // 原始温度单位
    public final String heatFlowUnit;         // 原始热流单位
    public final String instrumentSign;       // 仪器热流方向约定，见 DirectionConvention
    public final double scanRateKPerMin;
    public final String rateRaw;
    public final String atmosphere;
    public final String calibrationId;

    public final List<String> rawX = new ArrayList<>();
    public final List<String> rawY = new ArrayList<>();
    public final double[] xK;
    public final double[] yWg;
    public final boolean[] good;
    public final List<BadPoint> badPoints = new ArrayList<>();

    public static final class BadPoint {
        public final int index;
        public final String rawTemp;
        public final String rawHeatFlow;
        public final String reason;

        public BadPoint(int index, String rawTemp, String rawHeatFlow, String reason) {
            this.index = index;
            this.rawTemp = rawTemp;
            this.rawHeatFlow = rawHeatFlow;
            this.reason = reason;
        }
    }

    public Segment(String segmentId, int cycleIndex, String mode, String tempUnit, String heatFlowUnit,
                   String instrumentSign, double scanRateKPerMin, String rateRaw, String atmosphere,
                   String calibrationId, int n) {
        this.segmentId = segmentId;
        this.cycleIndex = cycleIndex;
        this.mode = mode;
        this.tempUnit = tempUnit;
        this.heatFlowUnit = heatFlowUnit;
        this.instrumentSign = instrumentSign;
        this.scanRateKPerMin = scanRateKPerMin;
        this.rateRaw = rateRaw;
        this.atmosphere = atmosphere;
        this.calibrationId = calibrationId;
        this.xK = new double[n];
        this.yWg = new double[n];
        this.good = new boolean[n];
    }

    public int size() {
        return xK.length;
    }

    public boolean isHeating() {
        return "HEATING".equals(mode);
    }

    /** 沿扫描方向（加热温度递增 / 冷却温度递减）严格单调，允许非等间隔。 */
    public boolean monotonic() {
        for (int i = 1; i < size(); i++) {
            if (!good[i] || !good[i - 1]) {
                continue;
            }
            double d = xK[i] - xK[i - 1];
            if (isHeating() ? d <= 0 : d >= 0) {
                return false;
            }
        }
        return true;
    }

    public int firstGood() {
        for (int i = 0; i < size(); i++) {
            if (good[i]) {
                return i;
            }
        }
        return -1;
    }

    public int lastGood() {
        for (int i = size() - 1; i >= 0; i--) {
            if (good[i]) {
                return i;
            }
        }
        return -1;
    }
}
