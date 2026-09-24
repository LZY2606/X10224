package org.materials.dsc.engine;

import org.materials.dsc.domain.Diagnostic;
import org.materials.dsc.domain.Direction;
import org.materials.dsc.domain.InstrumentOutput;
import org.materials.dsc.domain.Quantity;
import org.materials.dsc.domain.Segment;
import org.materials.dsc.domain.Units;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 扫描分段：
 *  - 仪器程序段标签来自数据行的 segment 列（循环段切换时同文件含 Heat1/Cool1/Heat2）；
 *  - 每段方向来自仪器程序元数据，绝不从文件名推断；
 *  - 数据温度走向与声明方向矛盾时产生诊断（heating/cooling 绝不静默混用）。
 */
public final class Segmenter {
    private Segmenter() {
    }

    public record ProgramInfo(Direction direction, double rateCelsiusPerMin, String cycle) {
    }

    public record Result(List<Segment> segments, List<Diagnostic> diagnostics) {
    }

    public static Result segment(InstrumentOutput out, Normalizer.Series series,
                                 Map<String, ProgramInfo> programByName,
                                 String[] rowSegmentLabels) {
        List<Diagnostic> diag = new ArrayList<>();
        List<Segment> segments = new ArrayList<>();
        int n = series.length();
        if (programByName != null && !programByName.isEmpty()) {
            int segIdx = 0;
            int runStart = -1;
            String runLabel = null;
            for (int i = 0; i <= n; i++) {
                String label = i < n ? rowSegmentLabels[i] : runLabel;
                boolean boundary = i == n
                        || (label != null && runLabel != null && !label.equals(runLabel));
                if (boundary && runStart >= 0) {
                    int lo = runStart;
                    int hi = i - 1;
                    ProgramInfo info = programByName.getOrDefault(runLabel,
                            new ProgramInfo(out.declaredDirection(), Double.NaN, runLabel));
                    segments.add(buildSegment(out, segIdx++, lo, hi, info, series, diag));
                    runStart = -1;
                }
                if (i < n && series.valid[i] && runStart < 0) {
                    runStart = i;
                    runLabel = label;
                }
            }
        } else {
            int first = -1;
            int last = -1;
            for (int i = 0; i < n; i++) {
                if (series.valid[i]) {
                    if (first < 0) {
                        first = i;
                    }
                    last = i;
                }
            }
            if (first >= 0) {
                ProgramInfo info = new ProgramInfo(out.declaredDirection(), Double.NaN, "S1");
                segments.add(buildSegment(out, 0, first, last, info, series, diag));
            }
        }
        return new Result(segments, diag);
    }

    private static Segment buildSegment(InstrumentOutput out, int idx, int lo, int hi,
                                        ProgramInfo info, Normalizer.Series series,
                                        List<Diagnostic> diag) {
        String id = out.id() + "-seg-" + idx;
        Direction observed = observedDirection(series, lo, hi);
        if (info.direction() != Direction.UNKNOWN && observed != Direction.UNKNOWN
                && observed != info.direction()) {
            diag.add(Diagnostic.warn("diag-dir-" + id, "direction-data-mismatch",
                    "程序声明方向 " + info.direction().label() + " 与数据温度走向 "
                            + observed.label() + " 不一致，保留声明并标记异常", id));
        }
        Quantity rate = Double.isNaN(info.rateCelsiusPerMin())
                ? Quantity.raw("(not-declared)", "°C/min")
                : Quantity.of(info.rateCelsiusPerMin(), Units.C_PER_MIN);
        return new Segment(id, out.id(), info.direction(), lo, hi,
                series.tCelsius[lo], series.tCelsius[hi], rate, info.cycle());
    }

    private static Direction observedDirection(Normalizer.Series s, int lo, int hi) {
        int up = 0;
        int down = 0;
        for (int i = lo + 1; i <= hi; i++) {
            if (!s.valid[i] || !s.valid[i - 1]) {
                continue;
            }
            if (s.tCelsius[i] > s.tCelsius[i - 1] + 1e-9) {
                up++;
            } else if (s.tCelsius[i] < s.tCelsius[i - 1] - 1e-9) {
                down++;
            }
        }
        if (up > down * 2) {
            return Direction.HEATING;
        }
        if (down > up * 2) {
            return Direction.COOLING;
        }
        if (up == 0 && down == 0) {
            return Direction.ISOTHERMAL;
        }
        return Direction.UNKNOWN;
    }
}
