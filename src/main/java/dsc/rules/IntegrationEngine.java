package dsc.rules;

import java.util.ArrayList;
import java.util.List;

/**
 * 相变积分引擎。
 * 语义保证：
 *  - 加热与冷却段不混用：积分区间必须位于单一 segment 内；
 *  - 吸放热方向依据规范符号（ENDO_UP）而非文件名；
 *  - 空区间/零宽度 -> INVALID，不编造数值；
 *  - 多个并列基线分别积分，基线散布作为误差来源，差异保留；
 *  - 质量修订通过调用方触发整批派生过期，本结果记录 massVersion。
 */
public final class IntegrationEngine {

    public static final class Calibration {
        public double offsetUncertaintyK = 0.3;
        public double slopeUncertainty = 0.002;
        public double massRelUncertainty = 0.002;
        public double rateUncertainty = 0.0;
    }

    public IntegrationResult integrate(String transitionId, NormalizedCurve curve,
                                       BaselineResult baseline, RuleSet rules,
                                       Calibration cal, double rateKPerMin) {
        List<String> diags = new ArrayList<>(List.copyOf(baseline.diagnostics()));
        double t0 = baseline.t0K();
        double t1 = baseline.t1K();
        String intervalId = baseline.intervalId();

        int n = CurveMath.countPoints(curve.points(), t0, t1);
        if (n == 0 || t1 <= t0) {
            diags.add("EMPTY_INTERVAL: 无法积分——区间内 0 个有效点或宽度为 0");
            return invalid(transitionId, intervalId, curve, diags, rules, null);
        }

        BaselineCandidate selected = null;
        for (BaselineCandidate c : baseline.candidates()) {
            if (c.candidateId().equals(baseline.selectedCandidateId())) {
                selected = c;
            }
        }
        if (selected == null) {
            if (baseline.candidates().isEmpty()) {
                diags.add("NO_BASELINE: 没有可用基线，积分无法定义");
                return invalid(transitionId, intervalId, curve, diags, rules, null);
            }
            diags.add("BASELINE_UNDECIDED: 尚未选择基线，默认用第一个候选积分但保留全部候选差异");
            selected = baseline.candidates().get(0);
        }

        List<Double> enthalpies = new ArrayList<>();
        IntegrationResult first = null;
        for (BaselineCandidate c : baseline.candidates()) {
            IntegrationResult r = integrateOne(transitionId, curve, baseline, c, rules, cal, rateKPerMin, diags);
            if (first == null) {
                first = r;
            }
            if (r.enthalpyJPerG() != null) {
                enthalpies.add(r.enthalpyJPerG());
            }
        }

        double baseSpread = 0;
        if (enthalpies.size() > 1) {
            double mn = enthalpies.stream().mapToDouble(Double::doubleValue).min().orElse(0);
            double mx = enthalpies.stream().mapToDouble(Double::doubleValue).max().orElse(0);
            baseSpread = (mx - mn) / 2.0;
        }

        IntegrationResult chosen = integrateOne(transitionId, curve, baseline, selected, rules, cal, rateKPerMin, diags);
        List<ErrorSource> errs = new ArrayList<>(chosen.errorSources());
        if (baseSpread > 0) {
            errs.add(new ErrorSource("BASELINE_AMBIGUITY", baseSpread,
                    "并列基线候选造成的焓变散布 ±" + String.format("%.4f", baseSpread)
                            + " J/g，多解未被合并"));
        }
        double tempUnc = Math.hypot(cal.offsetUncertaintyK,
                cal.slopeUncertainty * Math.abs(0.5 * (t0 + t1) - 273.15));

        return new IntegrationResult(transitionId, intervalId, curve.segmentId(),
                chosen.eventClass(), chosen.thermalSign(), chosen.enthalpyJPerG(),
                chosen.cpShiftJPerGK(), chosen.features(), errs, tempUnc,
                diags, rules.version(), selected.candidateId(), curve.massVersion());
    }

    private IntegrationResult integrateOne(String transitionId, NormalizedCurve curve,
                                           BaselineResult baseline, BaselineCandidate cand,
                                           RuleSet rules, Calibration cal, double rateKPerMin,
                                           List<String> diags) {
        double t0 = baseline.t0K();
        double t1 = baseline.t1K();
        List<NormPoint> pts = curve.points();

        List<NormPoint> excess = new ArrayList<>();
        for (NormPoint p : pts) {
            if (!p.excluded() && p.tK() >= t0 && p.tK() <= t1) {
                double yb = BaselineEngine.baselineAt(cand.line(), cand.knots(), p.tK());
                excess.add(new NormPoint(p.tK(), p.hfPerMass() - yb, false));
            }
        }
        excess.sort(java.util.Comparator.comparingDouble(NormPoint::tK));

        double area = CurveMath.trapezoid(excess, t0, t1);
        double meanAbs = Math.abs(area) / Math.max(CurveMath.span(t0, t1), 1e-9);

        int peakIdx = 0;
        double peakV = -Double.MAX_VALUE;
        int troughIdx = 0;
        double troughV = Double.MAX_VALUE;
        for (int i = 0; i < excess.size(); i++) {
            if (excess.get(i).hfPerMass() > peakV) {
                peakV = excess.get(i).hfPerMass();
                peakIdx = i;
            }
            if (excess.get(i).hfPerMass() < troughV) {
                troughV = excess.get(i).hfPerMass();
                troughIdx = i;
            }
        }

        String eventClass;
        String thermalSign;
        int featIdx;
        double featAmp;
        if ("SIGMOID_STEP".equals(cand.method())) {
            eventClass = "GLASS_TRANSITION";
            thermalSign = "STEP";
            featIdx = (peakIdx + troughIdx) / 2;
            featAmp = 0;
        } else if (peakV > Math.abs(troughV)) {
            eventClass = peakV > 3 * meanAbs ? "MELTING" : "TRANSITION";
            thermalSign = "ENDOTHERMIC";
            featIdx = peakIdx;
            featAmp = peakV;
        } else {
            eventClass = "CRYSTALLIZATION";
            thermalSign = "EXOTHERMIC";
            featIdx = troughIdx;
            featAmp = troughV;
        }

        List<Feature> features;
        Double enthalpy;
        Double cpShift = null;
        if ("GLASS_TRANSITION".equals(eventClass)) {
            double y0 = BaselineEngine.baselineAt(cand.line(), cand.knots(), t0);
            double y1 = BaselineEngine.baselineAt(cand.line(), cand.knots(), t1);
            cpShift = Math.abs(y1 - y0);
            features = List.of(
                    new Feature("ONSET", t0, CurveMath.interpolate(pts, t0)),
                    new Feature("STEP_MID", excess.get(featIdx).tK(), featAmp),
                    new Feature("ENDSET", t1, CurveMath.interpolate(pts, t1)));
            enthalpy = null;
        } else {
            double onsetT = t0;
            double endsetT = t1;
            for (int i = featIdx; i >= 0; i--) {
                if (Math.abs(excess.get(i).hfPerMass()) < 0.1 * Math.abs(featAmp) + 1e-12) {
                    onsetT = excess.get(i).tK();
                    break;
                }
            }
            for (int i = featIdx; i < excess.size(); i++) {
                if (Math.abs(excess.get(i).hfPerMass()) < 0.1 * Math.abs(featAmp) + 1e-12) {
                    endsetT = excess.get(i).tK();
                    break;
                }
            }
            features = List.of(
                    new Feature("ONSET", onsetT, CurveMath.interpolate(pts, onsetT)),
                    new Feature("PEAK", excess.get(featIdx).tK(), featAmp),
                    new Feature("ENDSET", endsetT, CurveMath.interpolate(pts, endsetT)));
            enthalpy = area;
        }

        List<ErrorSource> errs = new ArrayList<>();
        if (enthalpy != null) {
            errs.add(new ErrorSource("MASS_UNCERTAINTY",
                    Math.abs(enthalpy) * cal.massRelUncertainty,
                    "质量称量相对不确定度 " + (cal.massRelUncertainty * 100) + "%"));
            errs.add(new ErrorSource("TEMPERATURE_CALIBRATION",
                    Math.abs(enthalpy) * cal.slopeUncertainty,
                    "温度标定斜率不确定度 " + cal.slopeUncertainty + "，截距 ±"
                            + cal.offsetUncertaintyK + "K"));
            if (rateKPerMin > 0) {
                double ratePart = Math.abs(enthalpy) * cal.rateUncertainty;
                errs.add(new ErrorSource("SCAN_RATE", ratePart,
                        "扫描速率 " + rateKPerMin + " K/min 传播的焓变不确定度"));
            }
            double trapErr = trapezoidTruncation(excess, t0, t1, enthalpy);
            errs.add(new ErrorSource("NUMERICAL_INTEGRATION", trapErr,
                    "非等间隔梯形积分截断误差估计 " + String.format("%.5f", trapErr) + " J/g"));
            if (!"ENDO_UP".equals(curve.signBasis()) || !"OK".equals(curve.signStatus())) {
                errs.add(new ErrorSource("SIGN_CONVENTION", 0,
                        "吸放热方向依据仪器元数据 " + curve.signBasis()
                                + "，非文件名；状态 " + curve.signStatus()));
            }
        }

        return new IntegrationResult(transitionId, baseline.intervalId(), curve.segmentId(),
                eventClass, thermalSign, enthalpy, cpShift, features, errs,
                cal.offsetUncertaintyK, List.of(), rules.version(),
                cand.candidateId(), curve.massVersion());
    }

    /** 粗略二阶截断误差：相邻区间面积差的量级，非均匀网格下仍有效。 */
    private static double trapezoidTruncation(List<NormPoint> excess, double t0, double t1,
                                              double area) {
        if (excess.size() < 4) {
            return Math.abs(area) * 0.05;
        }
        double sum = 0;
        for (int i = 1; i < excess.size() - 1; i++) {
            double dl = excess.get(i).tK() - excess.get(i - 1).tK();
            double dr = excess.get(i + 1).tK() - excess.get(i).tK();
            double second = (excess.get(i + 1).hfPerMass() - 2 * excess.get(i).hfPerMass()
                    + excess.get(i - 1).hfPerMass()) / Math.max(dl * dr, 1e-12);
            sum += Math.abs(second) * dl * dr;
        }
        return Math.min(Math.abs(area) * 0.05, 0.0833 * sum);
    }

    private IntegrationResult invalid(String transitionId, String intervalId,
                                      NormalizedCurve curve, List<String> diags,
                                      RuleSet rules, String candId) {
        return new IntegrationResult(transitionId, intervalId, curve.segmentId(),
                "UNKNOWN", "UNKNOWN", null, null, List.of(), List.of(),
                0, diags, rules.version(), candId, curve.massVersion());
    }
}
