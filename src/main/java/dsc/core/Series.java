package dsc.core;

import java.util.ArrayList;
import java.util.List;

/**
 * 一段扫描序列（加热段或冷却段），属于同一样品履历中的一个 segment。
 * 原始点保留字符串表示与原始单位；规范化只在派生层发生。
 * 支持非等间隔温度（以时间戳做积分基准）。
 */
public final class Series {
    public static final class Point {
        public final String tempRaw, heatRaw, timeRaw;
        public final double tempC, heatMw, timeMin; // 归一化值；NaN 表示不可转换
        public final String tempDiag, heatDiag, timeDiag;

        public Point(String tRaw, String tUnit, String hRaw, String hUnit, String sRaw, String sUnit) {
            this.tempRaw = tRaw;
            this.heatRaw = hRaw;
            this.timeRaw = sRaw;
            Quantity t = Quantity.parse(tRaw, tUnit, Unit.Dim.TEMPERATURE);
            Quantity h = Quantity.parse(hRaw, hUnit, Unit.Dim.HEAT_FLOW);
            Quantity s = Quantity.parse(sRaw, sUnit, Unit.Dim.TIME);
            this.tempC = t.value();
            this.heatMw = h.value();
            this.timeMin = s.value();
            this.tempDiag = t.diagnostic();
            this.heatDiag = h.diagnostic();
            this.timeDiag = s.diagnostic();
        }
    }

    private final String segmentId;
    private final String segmentName;
    private final Direction declaredDirection;
    private final SignConvention signConvention;
    private final String tempUnit, heatUnit, timeUnit;
    private final String atmosphere;          // 气氛，原样存档
    private final Quantity rate;              // 扫描速率
    private final List<Point> points = new ArrayList<>();

    public Series(String segmentId, String segmentName, Direction dir, SignConvention sign,
                  String tempUnit, String heatUnit, String timeUnit, String atmosphere, Quantity rate) {
        this.segmentId = segmentId;
        this.segmentName = segmentName;
        this.declaredDirection = dir;
        this.signConvention = sign;
        this.tempUnit = tempUnit;
        this.heatUnit = heatUnit;
        this.timeUnit = timeUnit;
        this.atmosphere = atmosphere;
        this.rate = rate;
    }

    public void addPoint(String t, String h, String s) {
        points.add(new Point(t, tempUnit, h, heatUnit, s, timeUnit));
    }

    public String segmentId() { return segmentId; }
    public String segmentName() { return segmentName; }
    public Direction declaredDirection() { return declaredDirection; }
    public SignConvention signConvention() { return signConvention; }
    public String atmosphere() { return atmosphere; }
    public Quantity rate() { return rate; }
    public List<Point> points() { return points; }
    public int size() { return points.size(); }

    public double[] tempArray() {
        double[] a = new double[points.size()];
        for (int i = 0; i < a.length; i++) a[i] = points.get(i).tempC;
        return a;
    }

    /** 归一到 endoUp 约定（吸热为正，mW）的热流。 */
    public double[] heatArrayNormalized() {
        double[] a = new double[points.size()];
        for (int i = 0; i < a.length; i++) a[i] = signConvention.normalizeHeatFlow(points.get(i).heatMw);
        return a;
    }

    public double[] timeArray() {
        double[] a = new double[points.size()];
        for (int i = 0; i < a.length; i++) a[i] = points.get(i).timeMin;
        return a;
    }

    /** 收集所有不可转换点诊断；诊断不导致丢弃，而被附加到来源。 */
    public List<String> diagnostics() {
        List<String> out = new ArrayList<>();
        for (int i = 0; i < points.size(); i++) {
            Point p = points.get(i);
            if (p.tempDiag != null) out.add("段 " + segmentName + " 第" + i + "点温度: " + p.tempDiag);
            if (p.heatDiag != null) out.add("段 " + segmentName + " 第" + i + "点热流: " + p.heatDiag);
            if (p.timeDiag != null) out.add("段 " + segmentName + " 第" + i + "点时间: " + p.timeDiag);
        }
        return out;
    }
}
