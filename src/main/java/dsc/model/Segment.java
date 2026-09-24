package dsc.model;

import java.util.List;

/**
 * 一次加热或冷却扫描段。
 *
 * direction（heating/cooling）只能来自仪器头或段内数据单调性，
 * 绝不能从文件名猜测；热流符号约定 heatFlowSign（endo-up/endo-down）
 * 同样来自仪器头。加热与冷却段在分析时不混用。
 */
public final class Segment {
    private final String id;
    private final String sourceId;
    private final String label;
    private final String cycle;
    private final String direction;          // heating | cooling | unknown
    private final String directionBasis;     // machine-readable 判定依据
    private final String heatFlowSign;       // endo-up | endo-down
    private final RawValue scanRate;
    private final String atmosphere;
    private final String calibration;       // 温度标定标识
    private final List<SamplePoint> points;
    private final List<String> diagnostics;
    private final boolean equallySpaced;

    public Segment(String id, String sourceId, String label, String cycle,
                   String direction, String directionBasis, String heatFlowSign,
                   RawValue scanRate, String atmosphere, String calibration,
                   List<SamplePoint> points, List<String> diagnostics,
                   boolean equallySpaced) {
        this.id = id;
        this.sourceId = sourceId;
        this.label = label;
        this.cycle = cycle;
        this.direction = direction;
        this.directionBasis = directionBasis;
        this.heatFlowSign = heatFlowSign;
        this.scanRate = scanRate;
        this.atmosphere = atmosphere;
        this.calibration = calibration;
        this.points = List.copyOf(points);
        this.diagnostics = List.copyOf(diagnostics);
        this.equallySpaced = equallySpaced;
    }

    public String id() { return id; }
    public String sourceId() { return sourceId; }
    public String label() { return label; }
    public String cycle() { return cycle; }
    public String direction() { return direction; }
    public String directionBasis() { return directionBasis; }
    public String heatFlowSign() { return heatFlowSign; }
    public RawValue scanRate() { return scanRate; }
    public String atmosphere() { return atmosphere; }
    public String calibration() { return calibration; }
    public List<SamplePoint> points() { return points; }
    public List<String> diagnostics() { return diagnostics; }
    public boolean equallySpaced() { return equallySpaced; }
}
