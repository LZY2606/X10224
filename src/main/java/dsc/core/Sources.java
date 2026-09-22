package dsc.core;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 六类来源分别存档，互不合并：
 *  1 仪器输出 INSTRUMENT_OUTPUT（曲线点 + 方向 + 符号约定 + 段循环信息）
 *  2 样品质量 MASS
 *  3 扫描速率 RATE
 *  4 气氛 ATMOSPHERE
 *  5 温度标定 TEMPERATURE_CALIBRATION（标定偏移，带基准）
 *  6 用户选定切线 TANGENT（每条基线候选的两侧切线区间）
 * 每个来源有稳定身份 sourceId 与不可变版本 version。
 */
public final class Sources {

    public enum Kind {
        INSTRUMENT_OUTPUT("instrument_output", "仪器输出"),
        MASS("mass", "样品质量"),
        RATE("rate", "扫描速率"),
        ATMOSPHERE("atmosphere", "气氛"),
        TEMPERATURE_CALIBRATION("temperature_calibration", "温度标定"),
        TANGENT("tangent", "用户选定的切线");

        public final String code;
        public final String label;
        Kind(String code, String label) { this.code = code; this.label = label; }
    }

    /** 某一来源的一个不可变版本。 */
    public static final class SourceVersion {
        public final String sourceId;
        public final Kind kind;
        public final int version;
        public final Instant createdAt;
        public final String author;
        public final String note;
        public final String payloadJson;   // 原始表示完整保留
        public final String basis;         // 坐标/标定基准

        public SourceVersion(String sourceId, Kind kind, int version, Instant createdAt,
                             String author, String note, String payloadJson, String basis) {
            this.sourceId = sourceId;
            this.kind = kind;
            this.version = version;
            this.createdAt = createdAt;
            this.author = author;
            this.note = note;
            this.payloadJson = payloadJson;
            this.basis = basis;
        }
    }

    /** 温度标定：在基准下对实测温度施加 offsetC（内部 °C，标定以 ITS-90 基准）。 */
    public static final class TempCalibration {
        public final double offsetC;
        public final String basis;
        public final String rawText;
        public TempCalibration(double offsetC, String basis, String rawText) {
            this.offsetC = offsetC; this.basis = basis; this.rawText = rawText;
        }
    }

    private final Map<String, List<SourceVersion>> history = new LinkedHashMap<>();

    public void put(SourceVersion sv) {
        history.computeIfAbsent(sv.sourceId, k -> new ArrayList<>());
        List<SourceVersion> list = history.get(sv.sourceId);
        if (sv.version != list.size() + 1) {
            throw new IllegalStateException("来源版本必须严格递增: " + sv.sourceId + " v" + sv.version
                    + "，当前已有 " + list.size());
        }
        list.add(sv);
    }

    public SourceVersion get(String sourceId, int version) {
        List<SourceVersion> list = history.get(sourceId);
        if (list == null || version < 1 || version > list.size()) return null;
        return list.get(version - 1);
    }

    public int latestVersion(String sourceId) {
        List<SourceVersion> list = history.get(sourceId);
        return list == null ? 0 : list.size();
    }

    public SourceVersion latest(String sourceId) {
        List<SourceVersion> list = history.get(sourceId);
        return list == null || list.isEmpty() ? null : list.get(list.size() - 1);
    }

    public Map<String, List<SourceVersion>> all() { return history; }
}
