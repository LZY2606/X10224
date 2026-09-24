package org.materials.dsc.engine;

import org.materials.dsc.domain.Diagnostic;
import org.materials.dsc.domain.Direction;
import org.materials.dsc.domain.DisplayValue;
import org.materials.dsc.domain.Segment;
import org.materials.dsc.domain.ThermalSign;
import org.materials.dsc.domain.Transition;
import org.materials.dsc.domain.TransitionKind;

import java.util.ArrayList;
import java.util.List;

/**
 * 转折检测（仅在单个扫描段内；加热与冷却不混用）：
 *  - 局部斜率持续偏移 -> 玻璃化台阶候选；
 *  - 显著局部极值 -> 结晶/熔融峰候选；
 *  - 区间相交的峰标记为重叠转折，不合并、不吞掉多解。
 */
public final class Detector {
    private Detector() {
    }

    public static final String RULE_VERSION = "rules-1.0.0";

    public record RawTransition(int lo, int peak, int hi, TransitionKind kind, ThermalSign sign) {
    }

    public record Result(List<Transition> transitions, List<RawTransition> raw,
                         List<Diagnostic> diagnostics) {
    }

    public static Result detect(Segment seg, Normalizer.Series s, String ruleVersion, String eventSeq) {
        List<Diagnostic> diag = new ArrayList<>();
        int lo = seg.startIndex();
        int hi = seg.endIndex();
        double noise = robustNoise(s, lo, hi);
        double scale = Math.max(noise, 1e-9);

        double[] slope = new double[s.length()];
        for (int i = Math.max(lo + 1, 1); i <= Math.min(hi, s.length() - 2); i++) {
            if (s.valid[i - 1] && s.valid[i + 1]) {
                slope[i] = (s.hfMw[i + 1] - s.hfMw[i - 1]) / (s.tCelsius[i + 1] - s.tCelsius[i - 1]);
            }
        }
        double slopeNoise = robustNoise(slope, lo, hi);

        List<RawTransition> found = new ArrayList<>();
        int win = 3;
        int i = lo + win + 1;
        while (i < hi - win) {
            if (!s.valid[i]) {
                i++;
                continue;
            }
            boolean peakUp = s.hfMw[i] - s.hfMw[i - win] > 4 * scale
                    && s.hfMw[i] - s.hfMw[i + win] > 4 * scale;
            boolean peakDown = s.hfMw[i - win] - s.hfMw[i] > 4 * scale
                    && s.hfMw[i + win] - s.hfMw[i] > 4 * scale;
            if (peakUp || peakDown) {
                int[] ext = extent(s, i, lo, hi, scale, peakUp);
                ThermalSign sign = peakUp ? ThermalSign.EXOTHERMIC : ThermalSign.ENDOTHERMIC;
                TransitionKind kind = guessKind(seg.direction(), sign, found.size());
                found.add(new RawTransition(ext[0], i, ext[1], kind, sign));
                i = ext[1] + win;
                continue;
            }
            double ds = 0;
            if (i + 2 <= hi) {
                ds = Math.abs(slope[i] - slope[i + 2]);
            }
            if (ds > 5 * Math.max(slopeNoise, 1e-12)) {
                int[] ext = stepExtent(slope, i, lo, hi, Math.max(slopeNoise, 1e-12));
                if (ext[1] - ext[0] >= 3 && !covered(found, ext[0], ext[1])) {
                    found.add(new RawTransition(ext[0], (ext[0] + ext[1]) / 2, ext[1],
                            TransitionKind.GLASS, ThermalSign.AMBIGUOUS));
                    i = ext[1] + win;
                    continue;
                }
            }
            i++;
        }

        List<Transition> out = new ArrayList<>();
        for (int k = 0; k < found.size(); k++) {
            RawTransition r = found.get(k);
            String id = seg.id() + "-tr-" + k;
            boolean overlap = false;
            List<String> overlaps = new ArrayList<>();
            for (int m = 0; m < found.size(); m++) {
                if (m == k) {
                    continue;
                }
                RawTransition o = found.get(m);
                if (r.lo() <= o.hi() && o.lo() <= r.hi()) {
                    overlap = true;
                    overlaps.add(seg.id() + "-tr-" + m);
                }
            }
            String status = overlap ? Transition.STATUS_OVERLAPPED : Transition.STATUS_DETECTED;
            if (overlap) {
                diag.add(Diagnostic.warn("diag-overlap-" + id, "overlapping-transitions",
                        "转折区间与相邻转折重叠，保留各自候选而不合并", id));
            }
            DisplayValue onset = dv(id + "-onset", "onset", s.tCelsius[r.lo()], "°C",
                    List.of(seg.id()), eventSeq, ruleVersion);
            DisplayValue peakV = dv(id + "-peak", "peak", s.tCelsius[r.peak()], "°C",
                    List.of(seg.id()), eventSeq, ruleVersion);
            DisplayValue endset = dv(id + "-endset", "endset", s.tCelsius[r.hi()], "°C",
                    List.of(seg.id()), eventSeq, ruleVersion);
            out.add(new Transition(id, seg.traceId(), seg.id(),
                    r.kind(), r.kind(), seg.direction(), r.sign(),
                    r.lo(), r.peak(), r.hi(), onset, peakV, endset,
                    overlaps, new ArrayList<>(), null, status, eventSeq, ruleVersion,
                    new ArrayList<>(diap4(id, diag))));
        }
        return new Result(out, found, diag);
    }

    private static List<String> diap4(String id, List<Diagnostic> all) {
        return all.stream().filter(d -> id.equals(d.sourceRef()))
                .map(d -> d.code() + ": " + d.message()).toList();
    }

    private static boolean covered(List<RawTransition> found, int lo, int hi) {
        for (RawTransition r : found) {
            if (lo >= r.lo() && hi <= r.hi()) {
                return true;
            }
        }
        return false;
    }

    private static DisplayValue dv(String vid, String name, double value, String unit,
                                   List<String> prov, String eventSeq, String rules) {
        return DisplayValue.of(vid, name, value, unit, prov, null, rules, eventSeq);
    }

    private static TransitionKind guessKind(Direction direction, ThermalSign sign, int ordinal) {
        if (direction == Direction.COOLING) {
            return sign == ThermalSign.EXOTHERMIC ? TransitionKind.CRYSTALLIZATION : TransitionKind.MELTING;
        }
        if (direction == Direction.HEATING) {
            if (ordinal == 0 && sign == ThermalSign.ENDOTHERMIC) {
                return TransitionKind.MELTING;
            }
            return sign == ThermalSign.EXOTHERMIC ? TransitionKind.CRYSTALLIZATION : TransitionKind.MELTING;
        }
        return TransitionKind.OTHER;
    }

    private static int[] extent(Normalizer.Series s, int peak, int lo, int hi,
                                double scale, boolean up) {
        int l = peak;
        while (l > lo && s.valid[l - 1] && significant(s, l, l - 1, up, scale)) {
            l--;
        }
        int r = peak;
        while (r < hi && s.valid[r + 1] && significant(s, r, r + 1, up, scale)) {
            r++;
        }
        return new int[]{l, r};
    }

    private static boolean significant(Normalizer.Series s, int a, int b, boolean up, double scale) {
        if (up) {
            return s.hfMw[a] - s.hfMw[b] > 0.5 * scale;
        }
        return s.hfMw[b] - s.hfMw[a] > 0.5 * scale;
    }

    private static int[] stepExtent(double[] slope, int center, int lo, int hi, double slopeNoise) {
        int l = center;
        while (l > lo + 2 && Math.abs(slope[l - 1] - slope[l]) > 1.5 * slopeNoise) {
            l--;
        }
        int r = center;
        while (r < hi - 2 && Math.abs(slope[r + 1] - slope[r]) > 1.5 * slopeNoise) {
            r++;
        }
        return new int[]{l, r};
    }

    private static double robustNoise(Normalizer.Series s, int lo, int hi) {
        List<Double> diffs = new ArrayList<>();
        for (int i = lo + 1; i <= hi; i++) {
            if (s.valid[i] && s.valid[i - 1]) {
                diffs.add(Math.abs(s.hfMw[i] - s.hfMw[i - 1]));
            }
        }
        return median(diffs);
    }

    private static double robustNoise(double[] slope, int lo, int hi) {
        List<Double> diffs = new ArrayList<>();
        for (int i = lo + 2; i <= hi - 2; i++) {
            diffs.add(Math.abs(slope[i + 1] - slope[i - 1]));
        }
        return median(diffs);
    }

    private static double median(List<Double> values) {
        if (values.isEmpty()) {
            return 0;
        }
        List<Double> copy = new ArrayList<>(values);
        copy.sort(Double::compare);
        return copy.get(copy.size() / 2);
    }
}
