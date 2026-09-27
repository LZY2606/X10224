package dsc.model;

import java.util.ArrayList;
import java.util.List;

/**
 * 一个加热或冷却扫描段。方向与符号约定来自仪器元数据头，而不是文件名。
 * 每个段独立成档，加热与冷却段绝不混用基线/积分。
 */
public final class Segment {
    public String segmentId;
    public String fileName;
    public String instrument;
    public String method;
    public Direction declaredDirection = Direction.UNKNOWN;
    public SignConvention declaredSign = SignConvention.UNSPECIFIED;
    public Quantity rate;                 // K/min，来自仪器头
    public List<RawPoint> points = new ArrayList<>();
    public List<String> diagnostics = new ArrayList<>();

    public Segment() {}

    public Segment(String segmentId, String fileName) {
        this.segmentId = segmentId;
        this.fileName = fileName;
    }

    public List<RawPoint> usablePoints() {
        List<RawPoint> out = new ArrayList<>();
        for (RawPoint p : points) if (p.usable()) out.add(p);
        return out;
    }
}
