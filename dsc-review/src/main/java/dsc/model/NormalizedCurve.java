package dsc.model;

import java.util.ArrayList;
import java.util.List;

/** 一个段在给定依赖版本下的规范化曲线（派生层）。 */
public final class NormalizedCurve {
    public String nodeId;
    public String segmentId;
    public String sourceHash;              // 依赖的源内容指纹
    public String massVersion;
    public String calibrationVersion;
    public Direction direction;
    public SignConvention sign;
    public List<NormalizedPoint> points = new ArrayList<>();
    public List<String> dropped = new ArrayList<>();   // 被跳过的坏点诊断（保留位置信息）
    public boolean equidistant;
    public Double meanSpacingK;

    public NormalizedCurve() {}
}
