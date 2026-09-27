package dsc.model;

import java.util.ArrayList;
import java.util.List;

/**
 * 源档案的当前快照：仪器输出、样品质量、扫描速率、气氛、温度标定、
 * 用户选定切线分别存档。事件重放得到该结构。
 */
public final class SourceBundle {
    public String sampleId;
    public String sampleName;
    public int revision;                   // 源版本号（乐观并发）
    public final List<Segment> segments = new ArrayList<>();
    public final List<MassRecord> masses = new ArrayList<>();
    public final List<AtmosphereRecord> atmospheres = new ArrayList<>();
    public final List<Calibration> calibrations = new ArrayList<>();
    public final List<TangentSelection> tangents = new ArrayList<>();

    public Segment segment(String id) {
        for (Segment s : segments) if (s.segmentId.equals(id)) return s;
        return null;
    }

    public MassRecord currentMass() {
        return masses.isEmpty() ? null : masses.get(masses.size() - 1);
    }

    public Calibration currentCalibration() {
        return calibrations.isEmpty() ? null : calibrations.get(calibrations.size() - 1);
    }
}
