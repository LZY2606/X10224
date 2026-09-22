package dsc.core;

import java.util.ArrayList;
import java.util.List;

/**
 * 同一样品履历：按循环与段组织加热/冷却序列。
 * cycle 从 1 开始；每个段显式声明属于哪个 cycle，用于“循环段切换”分支。
 */
public final class SampleHistory {
    private final String sampleId;
    private final String sampleName;
    private final String materialId;
    private final List<Series> segments = new ArrayList<>();

    public SampleHistory(String sampleId, String sampleName, String materialId) {
        this.sampleId = sampleId;
        this.sampleName = sampleName;
        this.materialId = materialId;
    }

    public void addSegment(Series s) { segments.add(s); }

    public String sampleId() { return sampleId; }
    public String sampleName() { return sampleName; }
    public String materialId() { return materialId; }
    public List<Series> segments() { return segments; }

    public Series segment(String segmentId) {
        for (Series s : segments) if (s.segmentId().equals(segmentId)) return s;
        return null;
    }

    /** 加热段与冷却段绝不混用：只返回同一方向的段。 */
    public List<Series> segments(Direction dir) {
        List<Series> out = new ArrayList<>();
        for (Series s : segments) if (s.declaredDirection() == dir) out.add(s);
        return out;
    }
}
