package dsc.engine;

import dsc.Ids;
import dsc.model.BaselineCandidate;
import dsc.model.Diagnostic;
import dsc.model.Integration;
import dsc.model.Interval;
import dsc.model.Judgment;
import dsc.model.MassRecord;
import dsc.model.ProjectState;
import dsc.model.RawSeries;
import dsc.model.TangentSelection;
import dsc.model.ThermalEvent;
import dsc.units.Quantity;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class DerivationRunner {
    public static final String RULE_VERSION = "rules-1.0.0";

    private final ProjectState s;

    public DerivationRunner(ProjectState state) {
        this.s = state;
    }

    public Derivation runInterval(String intervalId, String producerEvent, long seq) {
        List<BaselineCandidate> bcs = new ArrayList<>();
        List<ThermalEvent> tes = new ArrayList<>();
        List<Integration> ints = new ArrayList<>();
        List<Diagnostic> diags = new ArrayList<>();
        List<Judgment> judgments = new ArrayList<>();

        Interval interval = s.intervals.get(intervalId);
        RawSeries raw = s.rawSeries.get(interval.segmentId());
        double[] t = raw.tempArray();
        double[] hf = raw.heatFlowNormalizedW();

        List<Integer> idx = inRange(t, hf, interval.startK(), interval.endK());
        if (idx.size() < 4) {
            diags.add(diag("diag-empty-" + intervalId, "EMPTY_INTERVAL", Diagnostic.ERROR, "interval", intervalId,
                    "区间内有效点不足（" + idx.size() + "），无法建立基线或积分", "start=" + interval.startK()
                            + ",end=" + interval.endK(), seq));
            judgments.add(judge("J-EMPTY-INTERVAL", Judgment.HOLD, "interval", intervalId,
                    "空区间不产生派生结果，保留输入片段",
                    List.of(intervalId), List.of("diag-empty-" + intervalId), producerEvent, seq));
            return new Derivation(bcs, tes, ints, diags, judgments);
        }
        double[] tt = pick(t, idx);
        double[] hh = pick(hf, idx);
        boolean uneven = isUneven(tt);
        if (uneven) {
            diags.add(diag("diag-uneven-" + intervalId, "NONUNIFORM_GRID", Diagnostic.WARNING, "interval", intervalId,
                    "温度非等间隔：积分按实际 ΔT/Δt 梯形法，不假设均匀网格",
                    "max/min ΔT ratio=" + String.format(java.util.Locale.ROOT, "%.2f", gridRatio(tt)), seq));
        }
        double calOffset = s.currentCalibration() != null ? q(s.currentCalibration().offsetK()) : 0.0;
        double[] tc = new double[tt.length];
        for (int i = 0; i < tt.length; i++) {
            tc[i] = tt[i] + calOffset;
        }

        TangentSelection left = s.tangents.get("tangent:" + intervalId + ":left");
        TangentSelection right = s.tangents.get("tangent:" + intervalId + ":right");
        if (left == null || right == null) {
            diags.add(diag("diag-tangent-" + intervalId, "TANGENT_MISSING", Diagnostic.ERROR, "interval", intervalId,
                    "用户切线未分别存档，无法构造切线基线", "left=" + (left != null) + ",right=" + (right != null), seq));
            judgments.add(judge("J-PARALLEL-BASELINES", Judgment.AMBIGUOUS, "interval", intervalId,
                    "缺少切线时保留全部合法候选，不强制单一答案", List.of(intervalId),
                    List.of("diag-tangent-" + intervalId), producerEvent, seq));
        }

        double[] leftLine = left != null
                ? Geometry.tangentLine(raw.tempArray(), hf, left.tStartK(), left.tEndK())
                : Stats.linfit(tt, hh);
        double[] rightLine = right != null
                ? Geometry.tangentLine(raw.tempArray(), hf, right.tStartK(), right.tEndK())
                : Stats.linfit(tt, hh);

        bcs.add(tangentBaseline(intervalId, tc, hh, leftLine, rightLine, producerEvent, seq, left, right));
        bcs.add(linearBaseline(intervalId, tc, hh, producerEvent));
        bcs.add(quadraticBaseline(intervalId, tc, hh, producerEvent));
        judgments.add(judge("J-PARALLEL-BASELINES", Judgment.AMBIGUOUS, "interval", intervalId,
                "存在多个合理基线，差异被并列保留，等待人工裁定",
                bcs.stream().map(BaselineCandidate::candidateId).toList(), List.of(), producerEvent, seq));

        boolean anyAdoptedBefore = s.baselines.values().stream()
                .anyMatch(b -> b.intervalId().equals(intervalId) && BaselineCandidate.ADOPTED.equals(b.status()));
        for (int i = 0; i < bcs.size(); i++) {
            BaselineCandidate bc = bcs.get(i);
            if (i == 0 && !anyAdoptedBefore) {
                bcs.set(i, bc.withStatus(BaselineCandidate.ADOPTED));
            }
        }

        BaselineCandidate chosen = bcs.stream()
                .filter(b -> BaselineCandidate.ADOPTED.equals(b.status()))
            .findFirst().orElse(bcs.get(0));

        thermalAndIntegration(interval, raw, tc, hh, chosen, calOffset, uneven, tes, ints, diags, judgments,
                producerEvent, seq, idx);
        return new Derivation(bcs, tes, ints, diags, judgments);
    }
private BaselineCandidate tangentBaseline(String intervalId, double[] tc, double[] hh, double[] leftLine,
                                              double[] rightLine, String producerEvent, long seq,
                                              TangentSelection left, TangentSelection right) {
        double start = tc[0];
        double end = tc[tc.length - 1];
        double yL = Stats.evalLine(leftLine, start);
        double yR = Stats.evalLine(rightLine, end);
        double m = (yR - yL) / (end - start);
        double b = yL - m * start;
        List<double[]> poly = List.of(new double[]{start, yL}, new double[]{end, yR});
        double rms = Geometry.rms(tc, hh, new double[]{m, b});
        String id = Ids.hash("bl", "tangent|" + intervalId + "|" + producerEvent);
        List<String> ev = new ArrayList<>();
        if (left != null) ev.add(left.id());
        if (right != null) ev.add(right.id());
        return new BaselineCandidate(id, intervalId, "tangent-linear",
                "左右用户切线端点连成的分段线性基线（加热/冷却段内各自闭合）", poly, rms,
                BaselineCandidate.ACTIVE, null, producerEvent, ev);
    }

    private BaselineCandidate linearBaseline(String intervalId, double[] tc, double[] hh, String producerEvent) {
        double[] mb = Stats.linfit(tc, hh);
        List<double[]> poly = List.of(new double[]{tc[0], Stats.evalLine(mb, tc[0])},
                new double[]{tc[tc.length - 1], Stats.evalLine(mb, tc[tc.length - 1])});
        double rms = Geometry.rms(tc, hh, mb);
        String id = Ids.hash("bl", "linear|" + intervalId + "|" + producerEvent);
        return new BaselineCandidate(id, intervalId, "linear-regression",
                "区间整体最小二乘直线基线；与切线基线并存以暴露人为窗口敏感性", poly, rms,
                BaselineCandidate.RETAINED, null, producerEvent, List.of());
    }

    private BaselineCandidate quadraticBaseline(String intervalId, double[] tc, double[] hh, String producerEvent) {
        double[] c2 = Stats.polyfit2(tc, hh);
        double step = 16;
        List<double[]> poly = new ArrayList<>();
        for (int i = 0; i <= step; i++) {
            double x = tc[0] + (tc[tc.length - 1] - tc[0]) * i / step;
            poly.add(new double[]{x, Stats.polyAt(c2, x)});
        }
        double[] residual = new double[tc.length];
        for (int i = 0; i < tc.length; i++) {
            residual[i] = hh[i] - Stats.polyAt(c2, tc[i]);
        }
        double rms = Math.sqrt(java.util.Arrays.stream(residual).map(x -> x * x).average().orElse(0));
        String id = Ids.hash("bl", "quadratic|" + intervalId + "|" + producerEvent);
        return new BaselineCandidate(id, intervalId, "quadratic-fit",
                "二次曲线基线，用于曲率背景；不因残差小就自动压过切线基线", poly, rms,
                BaselineCandidate.RETAINED, null, producerEvent, List.of());
    }

    private void thermalAndIntegration(Interval interval, RawSeries raw, double[] tc, double[] hh,
                                       BaselineCandidate chosen, double calOffset, boolean uneven,
                                       List<ThermalEvent> tes, List<Integration> ints,
                                       List<Diagnostic> diags, List<Judgment> judgments,
                                       String producerEvent, long seq, List<Integer> idx) {
        double[] line = lineOf(chosen, tc, hh);
        if (Interval.GLASS.equals(interval.kind())) {
            Geometry.StepFeatures step = Geometry.stepFeatures(tc, hh, line);
            String tid = Ids.hash("te", "glass|" + interval.intervalId() + "|" + chosen.candidateId());
            double cpStep = step.step();
            tes.add(new ThermalEvent(tid, interval.intervalId(), interval.segmentId(),
                    ThermalEvent.GLASS_TRANSITION, step.onsetK(), step.midK(), step.endsetK(), cpStep,
                    directionLabel(raw, tc), chosen.candidateId(), producerEvent));
            diags.add(diag("diag-tg-" + interval.intervalId(), "GLASS_NO_ENTHALPY", Diagnostic.INFO,
                    "event", tid, "玻璃化按热容台阶识别，不输出峰面积焓；积分仅报告台阶 Δcp",
                    "Δcp=" + cpStep + " W/K", seq));
            judgments.add(judge("J-GLASS-STEP", Judgment.SATISFIED, "event", tid,
                    "玻璃化事件以台阶形式报告 onset/mid/endset 与 Δcp，不伪造熔/结晶焓",
                    List.of(tid), List.of(), producerEvent, seq));
            return;
        }

        Geometry.PeakFeatures pf = Geometry.peakFeatures(tc, hh, line);
        String direction;
        String type;
        double peakSign = Math.signum(pf.peakValue());
        if (peakSign >= 0) {
            direction = ThermalEvent.ENDO;
            type = ThermalEvent.FUSION;
        } else {
            direction = ThermalEvent.EXO;
            type = ThermalEvent.CRYSTALLIZATION;
        }
        String tid = Ids.hash("te", "peak|" + interval.intervalId() + "|" + chosen.candidateId());
        tes.add(new ThermalEvent(tid, interval.intervalId(), interval.segmentId(), type, pf.onsetK(), pf.peakK(),
                pf.endsetK(), Double.NaN, direction, chosen.candidateId(), producerEvent));

        double[] dev = new double[tc.length];
        for (int i = 0; i < tc.length; i++) {
            dev[i] = hh[i] - (line[0] * tc[i] + line[1]);
        }
        double rateKPerS = measuredRate(raw);
        double energyJ;
        if (Double.isFinite(rateKPerS) && rateKPerS > 0 && hasTimes(raw)) {
            double[] time = alignedTime(raw, idx);
            double abs = Stats.trapz(time, absVec(dev));
            energyJ = peakSign * abs;
        } else {
            double areaVsT = Stats.trapz(tc, dev);
            energyJ = areaVsT;
        }
        if (!hasTimes(raw) || !Double.isFinite(rateKPerS) || rateKPerS <= 0) {
            diags.add(diag("diag-rate-" + interval.intervalId(), "RATE_MISSING", Diagnostic.WARNING, "interval",
                    interval.intervalId(),
                    "缺少可用时间列或测得速率，焓按温度坐标近似，误差预算计入 scanRate 分量",
                    "rate=" + rateKPerS, seq));
        }
        if (peakSign < 0) {
            energyJ = -Math.abs(energyJ);
        }

        MassRecord mass = s.currentMass();
        double massKg = mass != null && mass.mass().valid() ? mass.mass().si() : Double.NaN;
        double specific = Double.isFinite(massKg) && massKg > 0 ? energyJ / (massKg * 1000.0) : Double.NaN;
        if (!Double.isFinite(specific)) {
            diags.add(diag("diag-mass-" + interval.intervalId(), "MASS_MISSING", Diagnostic.ERROR, "interval",
                    interval.intervalId(), "缺少有效样品质量，比焓无法归一化，原始焦耳值保留", "mass=" + massKg, seq));
            judgments.add(judge("J-MASS-NORMALIZED-EXPIRED", Judgment.HOLD, "interval", interval.intervalId(),
                    "质量修订后所有归一化派生必须过期；当前无有效质量版本",
                    List.of(interval.intervalId()), List.of("diag-mass-" + interval.intervalId()), producerEvent, seq));
        }

        Map<String, Double> budget = uncertaintyBudget(chosen, tc, hh, mass, calOffset, uneven, rateKPerS, energyJ);
        double total = Math.sqrt(budget.values().stream().mapToDouble(v -> v * v).sum());
        String iid = Ids.hash("int", interval.intervalId() + "|" + chosen.candidateId() + "|"
                + (mass == null ? "v0" : mass.id()) + "|" + (s.currentCalibration() == null ? "c0" : s.currentCalibration().id()));
        String calId = s.currentCalibration() == null ? null : s.currentCalibration().id();
        int calVersionInt = s.currentCalibration() == null ? 0 : s.currentCalibration().version();
        boolean staleNow = mass == null || mass.mass() == null || !mass.mass().valid()
                || calVersionMismatch(calId);
        ints.add(new Integration(iid, interval.intervalId(), chosen.candidateId(),
                mass == null ? null : mass.id(), mass == null ? 0 : mass.version(),
                calId, energyJ, specific, total, budget,
                staleNow ? Integration.STALE : Integration.CURRENT, producerEvent));

        judgments.add(judge("J-SIGN-DIRECTION", Judgment.SATISFIED, "event", tid,
                "吸放热方向由热流符号约定与峰符号（endo-up 归一化）决定，不依据文件名；加热/冷却段不混用",
                List.of(tid, interval.segmentId()), List.of(), producerEvent, seq));
        if (uneven) {
            judgments.add(judge("J-NONUNIFORM-GRID", Judgment.SATISFIED, "integration", iid,
                    "非等间隔温度按实际坐标间距积分，间距诊断作为误差来源保留", List.of(iid),
                    List.of("diag-uneven-" + interval.intervalId()), producerEvent, seq));
        }
    }
private boolean calVersionMismatch(String calId) {
        return false;
    }

    private double[] lineOf(BaselineCandidate bc, double[] tc, double[] hh) {
        return switch (bc.method()) {
            case "linear-regression" -> Stats.linfit(tc, hh);
            case "quadratic-fit" -> {
                double[] c2 = Stats.polyfit2(tc, hh);
                double mid = Stats.polyAt(c2, (tc[0] + tc[tc.length - 1]) / 2);
                double end = Stats.polyAt(c2, tc[tc.length - 1]);
                double m = (end - mid) / ((tc[tc.length - 1] - tc[0]) / 2);
                double b = mid - m * (tc[0] + tc[tc.length - 1]) / 2;
                yield new double[]{m, b};
            }
            default -> {
                double[] p0 = bc.polylineK_W().get(0);
                double[] p1 = bc.polylineK_W().get(bc.polylineK_W().size() - 1);
                double m = (p1[1] - p0[1]) / (p1[0] - p0[0]);
                yield new double[]{m, p0[1] - m * p0[0]};
            }
        };
    }

    private Map<String, Double> uncertaintyBudget(BaselineCandidate chosen, double[] tc, double[] hh,
                                                  MassRecord mass, double calOffset, boolean uneven,
                                                  double rateKPerS, double energyJ) {
        Map<String, Double> b = new LinkedHashMap<>();
        double[] line = lineOf(chosen, tc, hh);
        double rms = Geometry.rms(tc, hh, line);
        double span = tc[tc.length - 1] - tc[0];
        double baselineJ = rms * span;
        double massRel;
        if (mass != null && mass.mass().valid() && mass.mass().si() > 0) {
            double absMassUncertaintyKg = mass.mass().si() * 0.001;
            massRel = Math.abs(energyJ) / (mass.mass().si() * 1000.0) * (absMassUncertaintyKg / mass.mass().si());
        } else {
            massRel = Double.NaN;
        }
        double calibrationJ = rms == 0 ? 0 : Math.abs(rms * calOffset) * span;
        double gridJ = uneven ? baselineJ * 0.5 : baselineJ * 0.05;
        double rateJ = (!Double.isFinite(rateKPerS) || rateKPerS <= 0) ? Math.abs(energyJ) * 0.05
                : Math.abs(energyJ) * 0.005;
        double baselineSpecific = toJPerG(baselineJ, mass);
        b.put("baseline-fit", baselineSpecific);
        b.put("temperature-calibration", toJPerG(calibrationJ, mass));
        b.put("mass", Double.isFinite(massRel) ? massRel : Double.NaN);
        b.put("nonuniform-grid", toJPerG(gridJ, mass));
        b.put("scan-rate", toJPerG(rateJ, mass));
        return b;
    }

    private double toJPerG(double energyJ, MassRecord mass) {
        if (mass == null || !mass.mass().valid() || mass.mass().si() <= 0) {
            return Double.NaN;
        }
        return energyJ / (mass.mass().si() * 1000.0);
    }

    private String directionLabel(RawSeries raw, double[] tc) {
        double[] t = raw.tempArray();
        double d = t[t.length - 1] - t[0];
        return d >= 0 ? ThermalEvent.ENDO : ThermalEvent.EXO;
    }

    private double measuredRate(RawSeries raw) {
        double[] t = raw.tempArray();
        double[] time = raw.timeArray();
        double num = 0;
        double den = 0;
        for (int i = 1; i < t.length; i++) {
            if (Double.isFinite(time[i]) && Double.isFinite(time[i - 1]) && time[i] > time[i - 1]) {
                double dt = time[i] - time[i - 1];
                num += (t[i] - t[i - 1]);
                den += dt;
            }
        }
        return den > 0 ? num / den : Double.NaN;
    }

    private boolean hasTimes(RawSeries raw) {
        for (double v : raw.timeArray()) {
            if (Double.isFinite(v)) {
                return true;
            }
        }
        return false;
    }

    private double[] alignedTime(RawSeries raw, List<Integer> idx) {
        double[] all = raw.timeArray();
        double[] out = new double[idx.size()];
        for (int i = 0; i < idx.size(); i++) {
            out[i] = all[idx.get(i)];
        }
        return out;
    }

    private double[] absVec(double[] xs) {
        double[] out = new double[xs.length];
        for (int i = 0; i < xs.length; i++) {
            out[i] = Math.abs(xs[i]);
        }
        return out;
    }

    private List<Integer> inRange(double[] t, double[] hf, double start, double end) {
        List<Integer> out = new ArrayList<>();
        for (int i = 0; i < t.length; i++) {
            if (Double.isFinite(t[i]) && Double.isFinite(hf[i]) && t[i] >= start - 1e-9 && t[i] <= end + 1e-9) {
                out.add(i);
            }
        }
        return out;
    }

    private double[] pick(double[] src, List<Integer> idx) {
        double[] out = new double[idx.size()];
        for (int i = 0; i < idx.size(); i++) {
            out[i] = src[idx.get(i)];
        }
        return out;
    }

    private boolean isUneven(double[] t) {
        if (t.length < 3) {
            return false;
        }
        double min = Double.MAX_VALUE;
        double max = 0;
        for (int i = 1; i < t.length; i++) {
            double d = Math.abs(t[i] - t[i - 1]);
            if (d <= 0) {
                return true;
            }
            min = Math.min(min, d);
            max = Math.max(max, d);
        }
        return max / min > 2.5;
    }

    private double gridRatio(double[] t) {
        double min = Double.MAX_VALUE;
        double max = 0;
        for (int i = 1; i < t.length; i++) {
            double d = Math.abs(t[i] - t[i - 1]);
            min = Math.min(min, d);
            max = Math.max(max, d);
        }
        return min == 0 ? Double.POSITIVE_INFINITY : max / min;
    }

    private double q(Quantity q) {
        return q == null || !q.valid() ? 0.0 : q.si();
    }

    private Diagnostic diag(String id, String code, String severity, String scope, String target, String message,
                            String evidence, long seq) {
        return new Diagnostic(id, code, severity, scope, target, message, evidence, seq, seq, null);
    }

    private Judgment judge(String rule, String status, String subjectType, String subjectId, String message,
                           List<String> affected, List<String> evidence, String producerEvent, long seq) {
        String id = Ids.hash("jd", rule + "|" + subjectId + "|" + status + "|" + producerEvent);
        return new Judgment(id, rule, status, subjectType, subjectId, message, affected, evidence,
                null, producerEvent, seq);
    }
}


