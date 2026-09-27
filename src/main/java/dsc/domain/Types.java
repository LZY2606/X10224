package dsc.domain;

import java.util.List;

/** 扫描方向：只能由扫描速率符号判定，禁止从文件名猜测。 */
public enum ScanDirection { HEATING, COOLING, ISOTHERMAL, UNKNOWN }

/**
 * 仪器热流方向约定。exo-up = 放热峰在正方向；exo-down = 放热峰在负方向。
 * 必须在仪器输出头中显式声明；缺失则方向不可判定。
 */
public enum HeatFlowConvention { EXO_UP, EXO_DOWN, UNDECLARED }

/** 气氛。 */
public record Atmosphere(String raw, String gas, Double flowMlPerMin) {}

/** 温度标定（坐标基准）：T_cal = slope*(T_raw_K) + offset_K。 */
public record TempCalibration(String id, int version, String sourceId,
                              double slope, double offsetK, String note) {}

/** 样品质量版本；revision 单调递增，新版本使全部归一化派生过期。 */
public record MassVersion(String id, int revision, Qty mass, String decidedBy, String note) {}

/** 仪器输出段。header 声明方向约定；points 为原始采样（非等间隔允许）。 */
public record Segment(String id, String instrumentOutputId, String label,
                      ScanDirection declaredDirection, double rateKPerMin,
                      HeatFlowConvention convention, String heatFlowUnit,
                      List<Point> points, String cycle) {
    public ScanDirection observedDirection() {
        if (rateKPerMin > 1e-12) return ScanDirection.HEATING;
        if (rateKPerMin < -1e-12) return ScanDirection.COOLING;
        return ScanDirection.ISOTHERMAL;
    }
}

/** 单个采样点：温度与热流都保留原始字符串表示。 */
public record Point(int index, String rawTempC, String rawHeatFlow,
                    double tempK, double heatFlow) {}

/** 仪器输出（原始档案之一）。 */
public record InstrumentOutput(String id, String instrument, String filePath,
                               HeatFlowConvention convention, String heatFlowUnit,
                               String acquiredAt, List<Segment> segments) {}

/** 用户选定/拟定的切线（基线候选的输入），独立存档。 */
public record TangentSelection(String id, String segmentId, int revision,
                               String createdBy,
                               Double leftTk, Double rightTk,
                               double leftSlope, double rightSlope,
                               String kind, boolean adopted) {}

/** 样品履历：六类来源分别存档。 */
public record SampleHistory(String sampleId, String name,
                            InstrumentOutput instrument,
                            List<MassVersion> masses,
                            Qty scanRate, Atmosphere atmosphere,
                            List<TempCalibration> calibrations,
                            List<TangentSelection> tangents) {}
