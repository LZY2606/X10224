package org.materials.dsc.domain;

import java.util.ArrayList;
import java.util.List;

/**
 * 在单段窗口内分析热转折。
 * 积分在时间域用梯形法，因此非等间隔温度采样也精确。
 */
public final class TransitionAnalyzer {

    private TransitionAnalyzer() {
    }

    public record Input(
            String windowId,
            String segmentId,
            String label,
            String familyHint,
            List<CanonicalPoint> windowPoints,
            BaselineModel.TangentContext tangentContext,
            List<BaselineModel> candidateModels,
            double massMilligram,
            double rateKelvinPerMin,
            String acceptedCandidateId,
            boolean overlap) {
    }

    public record Result(PhaseTransition transition, List<BaselineCandidate> candidates) {
    }

    public static Result analyze(Input in, Ids ids, String createdAtIso) {
        List<CanonicalPoint> pts = in.windowPoints();
        List<String> topDiag = new ArrayList<>();
        if (pts.size() < 2) {
            String diagnostic = "empty-or-degenerate-window: start=" + start(in) + " end=" + end(in)
                    + " contains " + pts.size() + " point(s); nothing to integrate";
            EnthalpyResult emptyEnthalpy = new EnthalpyResult(0, "unknown", 0, List.of(), diagnostic);
            PhaseTransition t = new PhaseTransition(
                    ids.transitionId(in.windowId()), in.windowId(), in.segmentId(),
                    in.label(), family(in), Double.NaN, Double.NaN, Double.NaN,
                    List.of(), emptyEnthalpy, false, List.of(diagnostic), "empty");
            return new Result(t, List.of());
        }

        int peakOffset = peakOffset(pts);
        double peakK = pts.get(peakOffset).temperatureKelvin();

        double[] yMwg = new double[pts.size()];
        double[] xK = new double[pts.size()];
        for (int i = 0; i < pts.size(); i++) {
            yMwg[i] = pts.get(i).specificHeatFlowMilliwattPerGram();
            xK[i] = pts.get(i).temperatureKelvin();
        }

        double sign = yMwg[peakOffset] >= 0 ? 1 : -1;
        double maxAbs = Math.abs(yMwg[peakOffset]);
        double threshold = maxAbs * 0.05;
        int onsetOffset = walkEdge(yMwg, peakOffset, -1, sign, threshold);
        int endsetOffset = walkEdge(yMwg, peakOffset, +1, sign, threshold);
        double onsetK = tangentialIntersection(pts, onsetOffset, peakOffset, -1);
        double endsetK = tangentialIntersection(pts, endsetOffset, peakOffset, +1);

        List<Double> windowX = new ArrayList<>();
        List<Double> windowY = new ArrayList<>();
        for (CanonicalPoint p : pts) {
            windowX.add(p.temperatureKelvin());
            windowY.add(p.specificHeatFlowMilliwattPerGram());
        }

        List<BaselineCandidate> candidates = new ArrayList<>();
        String accepted = in.acceptedCandidateId();
        boolean anyAccepted = false;
        for (BaselineModel model : in.candidateModels()) {
            BaselineModel.Fit fit = model.fit(windowX, windowY, in.tangentContext());
            String candId = ids.baselineCandidateId(in.windowId(), model.key());
            String status = "proposed";
            if (accepted != null) {
                if (accepted.equals(candId)) {
                    status = "accepted";
                    anyAccepted = true;
                }
            }
            candidates.add(new BaselineCandidate(candId, in.windowId(), model.key(), model.label(),
                    fit.y(), fit.residualRms(), fit.diagnostics(), status));
        }
        if (accepted != null && !anyAccepted) {
            topDiag.add("accepted-baseline-not-found: " + accepted);
        }
        if (accepted == null && in.candidateModels().size() > 1) {
            topDiag.add("multiple-baselines-undecided: all legal candidates retained");
        }

        BaselineCandidate chosen = candidates.stream()
                .filter(BaselineCandidate::accepted).findFirst()
                .orElse(candidates.isEmpty() ? null : candidates.get(0));
        EnthalpyResult enthalpy = integrate(pts, chosen, in, onsetOffset, endsetOffset);

        if (in.overlap()) {
            topDiag.add("overlapping-transitions: peak region shared with another transition; baselines ambiguous");
        }
        if (Double.isNaN(onsetK) || Double.isNaN(endsetK)) {
            topDiag.add("tangent-degenerate: onset/endset falls back to threshold edge");
            if (Double.isNaN(onsetK)) {
                onsetK = pts.get(onsetOffset).temperatureKelvin();
            }
            if (Double.isNaN(endsetK)) {
                endsetK = pts.get(endsetOffset).temperatureKelvin();
            }
        }

        String status = chosen != null && chosen.accepted() ? "accepted" : "proposed";
        PhaseTransition transition = new PhaseTransition(
                ids.transitionId(in.windowId()), in.windowId(), in.segmentId(),
                in.label(), family(in), onsetK, peakK, endsetK,
                List.copyOf(candidates), enthalpy, in.overlap(), List.copyOf(topDiag), status);
        return new Result(transition, candidates);
    }

    private static String family(Input in) {
        if (in.familyHint() != null && !in.familyHint().isBlank()) {
            return in.familyHint().trim().toLowerCase();
        }
        return "transition";
    }

    private static int start(Input in) {
        return in.windowPoints().isEmpty() ? -1 : in.windowPoints().get(0).rawIndex();
    }

    private static int end(Input in) {
        return in.windowPoints().isEmpty() ? -1
                : in.windowPoints().get(in.windowPoints().size() - 1).rawIndex();
    }

    private static int peakOffset(List<CanonicalPoint> pts) {
        int best = 0;
        double bestAbs = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < pts.size(); i++) {
            double a = Math.abs(pts.get(i).specificHeatFlowMilliwattPerGram());
            if (a > bestAbs) {
                bestAbs = a;
                best = i;
            }
        }
        return best;
    }

    /** 从峰值向边沿走，直到信号侧（与峰同向）低于峰值 5%。 */
    private static int walkEdge(double[] y, int peak, int dir, double sign, double threshold) {
        int i = peak;
        double peakSigned = y[peak] * sign;
        while (i + dir >= 0 && i + dir < y.length) {
            double v = y[i + dir] * sign;
            if (v < threshold && peakSigned > 0) {
                break;
            }
            i += dir;
        }
        return i;
    }

    /**
     * 切线相交法：过 edge 点的最大斜率切线与对侧基线（首/末点水平）相交。
     * 非等间隔时用各点真实 ΔT/Δt 求导。退化返回 NaN。
     */
    private static double tangentialIntersection(List<CanonicalPoint> pts, int edge, int peak, int dir) {
        if (edge <= 0 || edge >= pts.size() - 1) {
            return Double.NaN;
        }
        CanonicalPoint p0 = pts.get(edge - 1);
        CanonicalPoint p1 = pts.get(edge + 1);
        double dT = p1.temperatureKelvin() - p0.temperatureKelvin();
        if (Math.abs(dT) < 1e-9) {
            return Double.NaN;
        }
        double slope = (p1.specificHeatFlowMilliwattPerGram() - p0.specificHeatFlowMilliwattPerGram()) / dT;
        if (Math.abs(slope) < 1e-12) {
            return Double.NaN;
        }
        CanonicalPoint edgePt = pts.get(edge);
        double yAtEdge = edgePt.specificHeatFlowMilliwattPerGram();
        double baseY = dir < 0 ? pts.get(0).specificHeatFlowMilliwattPerGram()
                : pts.get(pts.size() - 1).specificHeatFlowMilliwattPerGram();
        return edgePt.temperatureKelvin() + (baseY - yAtEdge) / slope;
    }

    /**
     * t 域梯形积分：ΔH(J/g) = Σ (y_i+y_{i+1})/2 [mW/g] · Δt [s] / 1000。
     */
    private static EnthalpyResult integrate(List<CanonicalPoint> pts, BaselineCandidate chosen, Input in,
                                            int onsetOffset, int endsetOffset) {
        double area = 0;
        for (int i = 0; i + 1 < pts.size(); i++) {
            double dt = pts.get(i + 1).timeSec() - pts.get(i).timeSec();
            double s0 = pts.get(i).specificHeatFlowMilliwattPerGram() - chosen.baselineY().get(i);
            double s1 = pts.get(i + 1).specificHeatFlowMilliwattPerGram()
                    - chosen.baselineY().get(i + 1);
            area += 0.5 * (s0 + s1) * dt / 1000.0;
        }
        String kind = SignConvention.eventKind(area);

        List<ErrorSource> errors = new ArrayList<>();
        String wid = in.windowId();
        double baselineSpread = baselineSpread(pts, in);
        double eBaseline = Math.abs(baselineSpread * (totalTime(pts)) / 1000.0);
        errors.add(new ErrorSource(wid + "-err-baseline", "baseline-choice",
                "基线候选差异（不同合理基线）", eBaseline,
                    "candidate-model spread over integration window"));

        double mass = in.massMilligram();
        double eMass = Math.abs(area) * (0.002 / Math.max(mass, 1e-9) * 1000.0) / 1000.0 + 0;
        errors.add(new ErrorSource(wid + "-err-mass", "mass-balance",
                "天平分辨率与称量重复性（0.2% 质量）", rel(area, mass),
                "mass=" + String.format(java.util.Locale.ROOT, "%.3f", mass) + " mg"));

        double eRate = Math.abs(area) * 0.003;
        errors.add(new ErrorSource(wid + "-err-rate", "scan-rate",
                "扫描速率标定（0.3%）", eRate,
                "rate=" + String.format(java.util.Locale.ROOT, "%.2f", in.rateKelvinPerMin()) + " K/min"));

        double eSampling = samplingUncertainty(pts, chosen);
        errors.add(new ErrorSource(wid + "-err-sampling", "nonuniform-sampling",
                "非等间隔采样（相邻 Δt 不均）", eSampling,
                "trapezoid rule in time domain; spread of dt"));

        double eTangent = tangentUncertainty(pts, onsetOffset, endsetOffset);
        errors.add(new ErrorSource(wid + "-err-tangent", "tangent-onset-endset",
                "切线相交的 onset/endset 定位", eTangent,
                "tangent windows are a separate user-selected source"));

        double rss = 0;
        for (ErrorSource e : errors) {
            rss += e.contributionJoulePerGram() * e.contributionJoulePerGram();
        }
        double total = Math.sqrt(rss);
        return new EnthalpyResult(area, kind, total, List.copyOf(errors), null);
    }

    private static double rel(double area, double mass) {
        return Math.abs(area) * 0.002;
    }

    private static double totalTime(List<CanonicalPoint> pts) {
        return pts.get(pts.size() - 1).timeSec() - pts.get(0).timeSec();
    }

    private static double baselineSpread(List<CanonicalPoint> pts, Input in) {
        List<BaselineModel> models = in.candidateModels();
        if (models.size() < 2) {
            return 0;
        }
        List<Double> x = new ArrayList<>();
        List<Double> y = new ArrayList<>();
        for (CanonicalPoint p : pts) {
            x.add(p.temperatureKelvin());
            y.add(p.specificHeatFlowMilliwattPerGram());
        }
        double maxSpread = 0;
        List<List<Double>> bases = new ArrayList<>();
        for (BaselineModel m : models) {
            bases.add(m.fit(x, y, in.tangentContext()).y());
        }
        for (int i = 0; i < pts.size(); i++) {
            double lo = Double.POSITIVE_INFINITY;
            double hi = Double.NEGATIVE_INFINITY;
            for (List<Double> b : bases) {
                lo = Math.min(lo, b.get(i));
                hi = Math.max(hi, b.get(i));
            }
            maxSpread = Math.max(maxSpread, hi - lo);
        }
        return maxSpread;
    }

    private static double samplingUncertainty(List<CanonicalPoint> pts, BaselineCandidate chosen) {
        double area = 0;
        for (int i = 0; i + 1 < pts.size(); i++) {
            double dt = pts.get(i + 1).timeSec() - pts.get(i).timeSec();
            double s0 = pts.get(i).specificHeatFlowMilliwattPerGram() - chosen.baselineY().get(i);
            double s1 = pts.get(i + 1).specificHeatFlowMilliwattPerGram()
                    - chosen.baselineY().get(i + 1);
            area += 0.5 * (s0 + s1) * dt / 1000.0;
        }
        double meanDt = totalTime(pts) / Math.max(pts.size() - 1, 1);
        double varDt = 0;
        for (int i = 0; i + 1 < pts.size(); i++) {
            double dt = pts.get(i + 1).timeSec() - pts.get(i).timeSec();
            varDt += (dt - meanDt) * (dt - meanDt);
        }
        double cv = Math.sqrt(varDt / Math.max(pts.size() - 1, 1)) / Math.max(meanDt, 1e-12);
        return Math.abs(area) * Math.min(cv, 1.0) * 0.5;
    }

    private static double tangentUncertainty(List<CanonicalPoint> pts, int onset, int endset) {
        double area = 0;
        for (int i = 0; i + 1 < pts.size(); i++) {
            double dt = pts.get(i + 1).timeSec() - pts.get(i).timeSec();
            area += 0.5 * (pts.get(i).specificHeatFlowMilliwattPerGram()
                    + pts.get(i + 1).specificHeatFlowMilliwattPerGram()) * dt / 1000.0;
        }
        double edgeFraction = 0;
        if (pts.size() > 2) {
            edgeFraction = 2.0 / pts.size();
        }
        return Math.abs(area) * edgeFraction;
    }
}
