package dsc.core;

import dsc.domain.DirectionConvention;
import dsc.domain.InstrumentOutput;
import dsc.domain.MassVersion;
import dsc.domain.CalibrationRecord;
import dsc.domain.RawValue;
import dsc.domain.Segment;
import dsc.domain.Units;

import java.util.ArrayList;
import java.util.List;
import java.util.OptionalDouble;

/**
 * 仪器 CSV 导入。
 *
 * 分层纪律：原始字符串全部保留；规范化只在派生层发生。无法转换的值留在原位并附诊断。
 * 方向只来自仪器头 directionHeader；反向热流（exo-up）在此翻转，不猜测文件名。
 * 非等间隔温度原样保留，不重采样。
 */
public final class CsvImport {

    public static final class ImportReport {
        public final Segment segment;
        public final DirectionConvention direction;
        public final List<String> diagnostics = new ArrayList<>();
        public final boolean reversedHeatFlow;

        ImportReport(Segment segment, DirectionConvention direction, boolean reversedHeatFlow) {
            this.segment = segment;
            this.direction = direction;
            this.reversedHeatFlow = reversedHeatFlow;
        }
    }

    private CsvImport() {
    }

    public static ImportReport importOutput(InstrumentOutput out, String segmentId, int cycleIndex, String mode,
                                            MassVersion mass, CalibrationRecord calibration,
                                            double scanRateKPerMin, String atmosphere) {
        DirectionConvention direction = DirectionConvention.fromInstrumentHeader(out.directionHeader,
                out.instrumentModel);
        List<String[]> rows = out.rows;
        Segment seg = new Segment(segmentId, cycleIndex, mode, out.tempUnit, out.heatFlowUnit,
                direction.code, scanRateKPerMin, out.rateRaw, atmosphere,
                calibration == null ? out.calibrationLabel : calibration.calibrationId, rows.size());
        ImportReport report = new ImportReport(seg, direction, direction == DirectionConvention.EXO_UP);

        if (direction == DirectionConvention.UNRESOLVED) {
            report.diagnostics.add("DIRECTION_UNRESOLVED: instrument header direction marker missing or "
                    + "unrecognized ('" + out.directionHeader + "'); heat direction must not be guessed "
                    + "from file name '" + out.fileName + "'");
        }
        if (report.reversedHeatFlow) {
            report.diagnostics.add("REVERSED_HEATFLOW: instrument convention is exo-up; sign flipped to "
                    + "internal endo-up convention");
        }

        double massGrams = mass == null ? Double.NaN : mass.grams();
        double calOffset = calibration == null ? 0.0 : calibration.offsetK;

        double[] prevGoodX = {Double.NaN};
        double[] gaps = new double[rows.size()];
        for (int i = 0; i < rows.size(); i++) {
            String[] row = rows.get(i);
            String rx = row.length > 0 ? row[0] : null;
            String ry = row.length > 1 ? row[1] : null;
            seg.rawX.add(rx == null ? "" : rx);
            seg.rawY.add(ry == null ? "" : ry);

            RawValue xv = RawValue.of(rx);
            RawValue yv = RawValue.of(ry);
            String reason = null;
            double xk = Double.NaN;
            double ywg = Double.NaN;

            if (!xv.parseOk) {
                reason = "temperature " + xv.diagnostic;
            } else {
                OptionalDouble nx = Units.normalizeTemperature(xv.parsed, out.tempUnit);
                if (nx.isEmpty()) {
                    reason = "temperature unit unconvertible: " + out.tempUnit;
                } else {
                    xk = nx.getAsDouble() + calOffset;
                }
            }
            if (!yv.parseOk) {
                reason = reason == null ? "heatflow " + yv.diagnostic : reason + "; heatflow " + yv.diagnostic;
            } else {
                OptionalDouble ny = Units.normalizeHeatFlow(yv.parsed, out.heatFlowUnit, massGrams);
                if (ny.isEmpty()) {
                    String why = mass != null ? "heatflow unit unconvertible: " + out.heatFlowUnit
                            : "heatflow unit '" + out.heatFlowUnit + "' requires valid sample mass";
                    reason = reason == null ? why : reason + "; " + why;
                } else {
                    ywg = direction.toInternal(ny.getAsDouble());
                }
            }

            seg.xK[i] = xk;
            seg.yWg[i] = ywg;
            seg.good[i] = reason == null;
            if (reason != null) {
                seg.badPoints.add(new Segment.BadPoint(i, rx, ry, reason));
            } else {
                if (!Double.isNaN(prevGoodX[0])) {
                    gaps[i] = Math.abs(xk - prevGoodX[0]);
                }
                prevGoodX[0] = xk;
            }
        }

        if (!seg.badPoints.isEmpty()) {
            report.diagnostics.add("BAD_POINTS: " + seg.badPoints.size()
                    + " point(s) kept in place with diagnostics (not dropped)");
        }
        if (!seg.monotonic()) {
            report.diagnostics.add("NON_MONOTONIC: temperature is not strictly monotonic along scan direction");
        }
        flagNonUniform(seg, gaps, report.diagnostics);
        return report;
    }

    /** 非等间隔检测：中位间距之外超过 3 倍间距即记录（不影响积分正确性，仅标注坐标特征）。 */
    private static void flagNonUniform(Segment seg, double[] gaps, List<String> diagnostics) {
        List<Double> positive = new ArrayList<>();
        for (double g : gaps) {
            if (g > 0) {
                positive.add(g);
            }
        }
        if (positive.size() < 4) {
            return;
        }
        List<Double> sorted = new ArrayList<>(positive);
        sorted.sort(Double::compare);
        double median = sorted.get(sorted.size() / 2);
        double max = sorted.get(sorted.size() - 1);
        if (max > 3.0 * median) {
            diagnostics.add("NONUNIFORM_GRID: temperature spacing non-uniform (median gap "
                    + String.format("%.3f", median) + " K, max gap " + String.format("%.3f", max)
                    + " K); time-based trapezoid integration used, no resampling");
        }
    }
}
