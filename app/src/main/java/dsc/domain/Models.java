package dsc.domain;

import java.util.ArrayList;
import java.util.List;

/**
 * 领域模型。仪器输出（曲线）、样品质量、扫描速率、气氛、温度标定、用户选定的切线
 * 都是彼此独立的存档来源；曲线点保留原始表示，规范化值（K/W/s）放在派生字段。
 */
public final class Models {

    private Models() {
    }

    public enum Direction {
        HEATING, COOLING, ISOTHERMAL, UNKNOWN
    }

    public enum EventKind {
        GLASS_TRANSITION, CRYSTALLIZATION, MELTING, UNCLASSIFIED
    }

    /** 一个数据点：原始三件套 + 规范化三件套（转换失败的规范化分量为 null）。 */
    public static final class Point {
        public Q time;
        public Q temperature;
        public Q heatFlow;

        public Point() {
        }

        public Point(Q time, Q temperature, Q heatFlow) {
            this.time = time;
            this.temperature = temperature;
            this.heatFlow = heatFlow;
        }
    }

    /** 仪器输出：一段扫描曲线 + 仪器自述的扫描速率（与曲线本身独立存档）。 */
    public static final class Run {
        public String id;
        public String label;
        public String cycleTag;          // e.g. heat1 / cool1 / heat2
        public String sampleId;
        public String heatFlowUnit;
        public String heatFlowFamily;    // total | specific | unknown
        public String tempUnit;
        public String timeUnit;
        public String instrument;
        public String fileHint;          // 仅作元数据；方向绝不能由文件名猜
        public Q scanRate;               // 仪器自述扫描速率（来源独立）
        public Direction declaredDirection = Direction.UNKNOWN; // 数据自述（字段），非文件名
        public List<Point> points = new ArrayList<>();

        public Run() {
        }
    }

    /** 样品质量：每次称重都是独立版本。 */
    public static final class MassRecord {
        public String id;
        public String sampleId;
        public Q mass;
        public String since; // 生效时间戳（ISO）
        public String note;

        public MassRecord() {
        }
    }

    /** 气氛：独立来源。 */
    public static final class AtmosphereRecord {
        public String id;
        public String sampleId;
        public String gas;
        public Q flowRate; // 规范化 ml/min -> m3/s? 保留原始，仅数值归一不强转单位
        public String note;

        public AtmosphereRecord() {
        }
    }

    /** 温度标定：独立来源（标定物/修正量）。 */
    public static final class TempCalibration {
        public String id;
        public String sampleId;
        public String standard;
        public Q correction; // K 修正
        public String appliedToRunId;

        public TempCalibration() {
        }
    }

    /** 用户选定的切线：与基线候选分离，只是“在某候选上做的人工决定”。 */
    public static final class Tangent {
        public String id;
        public String runId;
        public String intervalId;
        public String side; // left | right
        public Q anchorTemperature;
        public Q anchorHeatFlow;
        public double slope; // 用户拖出的斜率
        public String note;

        public Tangent() {
        }
    }
}
