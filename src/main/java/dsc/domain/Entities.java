package dsc.domain;

import java.util.List;

/**
 * 事件溯源的实体与命令对象。
 * 仪器输出、样品质量、扫描速率、气氛、温度标定、用户切线分别存档为独立来源事件。
 */
public final class Entities {

    private Entities() {
    }

    public enum Direction {
        HEATING("加热"), COOLING("冷却"), UNKNOWN("未知");
        public final String label;

        Direction(String label) {
            this.label = label;
        }
    }

    /** 仪器输出约定的放热方向（绝不能靠文件名猜）。 */
    public enum ExoSign {
        /** 仪器约定：放热峰向上（热流读数增大为放热）。 */
        EXO_UP("exo_up"),
        /** 仪器约定：放热峰向下（热流读数减小为放热）。 */
        EXO_DOWN("exo_down"),
        UNKNOWN("unknown");
        public final String code;

        ExoSign(String code) {
            this.code = code;
        }

        public static ExoSign fromCode(String c) {
            if (c == null) {
                return UNKNOWN;
            }
            return switch (c.trim().toLowerCase()) {
                case "exo_up", "up" -> EXO_UP;
                case "exo_down", "down" -> EXO_DOWN;
                default -> UNKNOWN;
            };
        }
    }

    /** 仪器输出（含点序列与符号约定）。 */
    public static final class InstrumentOutput {
        public final String id;
        public final String name;
        public final String heatUnit;
        public final String tempUnit;
        public final ExoSign exoSign;
        public final String encodingNote;
        public final List<Values.Point> points;

        public InstrumentOutput(String id, String name, String heatUnit, String tempUnit,
                                ExoSign exoSign, String encodingNote, List<Values.Point> points) {
            this.id = id;
            this.name = name;
            this.heatUnit = heatUnit;
            this.tempUnit = tempUnit;
            this.exoSign = exoSign;
            this.encodingNote = encodingNote;
            this.points = List.copyOf(points);
        }
    }

    public static final class MassVersion {
        public final String id;
        public final double kg;
        public final String raw;
        public final String note;
        public final int version;

        public MassVersion(String id, double kg, String raw, String note, int version) {
            this.id = id;
            this.kg = kg;
            this.raw = raw;
            this.note = note;
            this.version = version;
        }
    }

    public static final class ScanRate {
        public final String id;
        public final double kPerMin;
        public final String raw;

        public ScanRate(String id, double kPerMin, String raw) {
            this.id = id;
            this.kPerMin = kPerMin;
            this.raw = raw;
        }
    }

    public static final class Atmosphere {
        public final String id;
        public final String gas;
        public final String raw;

        public Atmosphere(String id, String gas, String raw) {
            this.id = id;
            this.gas = gas;
            this.raw = raw;
        }
    }

    /** 温度标定：加性偏移(K)与斜率，来自标样；记录原始值。 */
    public static final class TempCalibration {
        public final String id;
        public final double offsetK;
        public final double slope;
        public final String standard;
        public final String raw;

        public TempCalibration(String id, double offsetK, double slope, String standard, String raw) {
            this.id = id;
            this.offsetK = offsetK;
            this.slope = slope;
            this.raw = raw;
            this.standard = standard;
        }

        public double apply(double tempK) {
            return slope * tempK + offsetK;
        }
    }

    /** 一次扫描段：加热/冷却 + 速率 + 绑定的仪器输出。绝不跨加热/冷却混用。 */
    public static final class Segment {
        public final String id;
        public final String cycle;
        public final int ordinal;
        public final Direction direction;
        public final String instrumentOutputId;
        public final String scanRateId;
        public final String atmosphereId;
        public final String tempCalibrationId;
        public final double tempRangeLowK;
        public final double tempRangeHighK;

        public Segment(String id, String cycle, int ordinal, Direction direction,
                       String instrumentOutputId, String scanRateId, String atmosphereId,
                       String tempCalibrationId, double lowK, double highK) {
            this.id = id;
            this.cycle = cycle;
            this.ordinal = ordinal;
            this.direction = direction;
            this.instrumentOutputId = instrumentOutputId;
            this.scanRateId = scanRateId;
            this.atmosphereId = atmosphereId;
            this.tempCalibrationId = tempCalibrationId;
            this.tempRangeLowK = lowK;
            this.tempRangeHighK = highK;
        }
    }

    /** 用户选定的切线（区间 + 两个锚点温度）。 */
    public static final class Tangent {
        public final String id;
        public final String segmentId;
        public final String eventRef;
        public final double fromK;
        public final double toK;
        public final double leftAnchorK;
        public final double rightAnchorK;
        public final String kind;

        public Tangent(String id, String segmentId, String eventRef, String kind,
                       double fromK, double toK, double leftAnchorK, double rightAnchorK) {
            this.id = id;
            this.segmentId = segmentId;
            this.eventRef = eventRef;
            this.kind = kind;
            this.fromK = fromK;
            this.toK = toK;
            this.leftAnchorK = leftAnchorK;
            this.rightAnchorK = rightAnchorK;
        }
    }

    /** 并列基线候选的裁定：采纳 / 否定 / 未决定（保留全部合法候选）。 */
    public enum Verdict {
        PROPOSED("proposed"),
        ACCEPTED("accepted"),
        REJECTED("rejected");
        public final String code;

        Verdict(String code) {
            this.code = code;
        }

        public static Verdict fromCode(String c) {
            return switch (c == null ? "" : c.toLowerCase()) {
                case "accepted" -> ACCEPTED;
                case "rejected" -> REJECTED;
                default -> PROPOSED;
            };
        }
    }
}
