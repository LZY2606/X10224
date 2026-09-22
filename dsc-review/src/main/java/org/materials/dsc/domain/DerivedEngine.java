package org.materials.dsc.domain;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 依赖图重算引擎。输入一个 {@link ProjectState}，输出全部可见派生节点。
 * 节点指纹 = 规则版本 + 依赖指纹 + 输入内容摘要；增量调用时指纹不变的节点保持有效，
 * 质量修订只让质量相关派生失效，原始序列节点仍然有效——这就是“只更新受影响部分”。
 */
public final class DerivedEngine {

    private final String ruleVersion;

    public DerivedEngine(String ruleVersion) {
        this.ruleVersion = ruleVersion;
    }

    public static DerivedEngine current() {
        return new DerivedEngine(RuleVersions.CURRENT);
    }

    public record GraphResult(Map<String, DerivedNode> nodes, List<String> diagnostics) {
    }

    public GraphResult recompute(ProjectState state) {
        Map<String, DerivedNode> nodes = new LinkedHashMap<>();
        List<String> diag = new ArrayList<>();

        for (InstrumentRun run : state.runs.values()) {
            buildRun(state, run, nodes, diag);
        }
        return new GraphResult(Map.copyOf(nodes), List.copyOf(diag));
    }

    /**
     * 增量重算：保留 prev 中指纹未变的节点，其余重新推导。
     * 返回每个节点的状态（valid / recomputed / stale-implicit），供事件记录受影响对象。
     */
    public GraphResult recomputeIncremental(ProjectState state, Map<String, DerivedNode> prev) {
        GraphResult fresh = recompute(state);
        Map<String, DerivedNode> merged = new LinkedHashMap<>(prev);
        for (Map.Entry<String, DerivedNode> e : fresh.nodes().entrySet()) {
            DerivedNode old = prev.get(e.getKey());
            if (old != null && old.fingerprint().equals(e.getValue().fingerprint())
                    && old.ruleVersion().equals(e.getValue().ruleVersion())) {
                merged.put(e.getKey(), withStatus(old, "valid", e.getValue().eventSeq()));
            } else {
                merged.put(e.getKey(), withStatus(e.getValue(),
                        old == null ? "new" : "recomputed", e.getValue().eventSeq()));
            }
        }
        // 新鲜结果里消失的旧节点：源数据缩小/窗口删除导致——标记 stale。
        for (String oldId : new ArrayList<>(prev.keySet())) {
            if (!fresh.nodes().containsKey(oldId)) {
                merged.put(oldId, withStatus(prev.get(oldId), "stale", prev.get(oldId).eventSeq()));
            }
        }
        return new GraphResult(Map.copyOf(merged), fresh.diagnostics());
    }

    private static DerivedNode withStatus(DerivedNode n, String status, long eventSeq) {
        return new DerivedNode(n.id(), n.kind(), n.dependencyIds(), n.ruleVersion(),
                n.fingerprint(), n.payload(), status, n.diagnostics(), eventSeq);
    }
}

    // ---- per-run graph ----

    private void buildRun(ProjectState state, InstrumentRun run,
                          Map<String, DerivedNode> nodes, List<String> globalDiag) {
        List<MassVersion> masses = state.massesOf(run.sampleId());
        MassVersion effectiveMass = masses.isEmpty() ? null : masses.get(masses.size() - 1);
        List<TemperatureCalibration> cals = state.calibrationsOf(run.id());
        TemperatureCalibration cal = cals.isEmpty() ? null : cals.get(cals.size() - 1);

        List<String> seriesDiag = new ArrayList<>(run.diagnostics());
        Double massMg = null;
        String massNodeId = null;
        if (effectiveMass == null) {
            seriesDiag.add("no-mass-version: specific heat flow unavailable; raw signal retained");
        } else if (!effectiveMass.mass().isConvertible()) {
            seriesDiag.add("mass-unconvertible (" + effectiveMass.id() + "): "
                    + effectiveMass.mass().diagnostic());
        } else {
            massMg = effectiveMass.milligrams();
            massNodeId = effectiveMass.id();
        }
        double offsetK = 0;
        if (cal != null && cal.offset().isConvertible()) {
            offsetK = cal.offset().canonical();
        } else if (cal != null) {
            seriesDiag.add("calibration-unconvertible (" + cal.id() + "): offset not applied");
        }
        if (run.atmosphere() != null) {
            seriesDiag.addAll(run.atmosphere().diagnostics());
        }
        if (run.scanRate() != null && !run.scanRate().isConvertible()) {
            seriesDiag.add("scan-rate-unconvertible: " + run.scanRate().diagnostic());
        }

        List<CanonicalPoint> canonical = new ArrayList<>();
        int dropped = 0;
        for (SeriesPoint sp : run.points()) {
            if (sp.timeSec() == null || sp.temperatureKelvin() == null
                    || sp.rawHeatFlowMilliwatt() == null) {
                dropped++;
                continue;
            }
            double rawMw = sp.rawHeatFlowMilliwatt();
            double canonMw = SignConvention.canonicalHeatFlowMilliwatt(rawMw, run.exoUp());
            Double specific = massMg == null ? null
                    : SignConvention.normalizeByMass(canonMw, massMg);
            canonical.add(new CanonicalPoint(sp.index(), sp.timeSec(),
                    sp.temperatureKelvin() + offsetK, rawMw, canonMw, specific));
        }
        if (dropped > 0) {
            seriesDiag.add(dropped + " point(s) kept at source with raw representation; excluded from derived plot");
        }
        double nonUniform = nonUniformity(canonical);
        if (nonUniform > 0.25) {
            seriesDiag.add("nonuniform-temperatures: dt spread/mean="
                    + String.format(java.util.Locale.ROOT, "%.2f", nonUniform)
                    + "; integration is performed in the time domain");
        }

        String seriesId = "series-" + run.id();
        List<String> seriesDeps = new ArrayList<>();
        seriesDeps.add(run.id());
        if (massNodeId != null) {
            seriesDeps.add(massNodeId);
        }
        if (cal != null) {
            seriesDeps.add(cal.id());
        }
        SeriesPayload payload = new SeriesPayload(run.id(), run.sampleId(),
                run.exoUp() ? SignConvention.EXOTHERMIC_UP : SignConvention.ENDOTHERMIC_UP,
                massMg, massNodeId, cal == null ? null : offsetK,
                List.copyOf(canonical), List.copyOf(seriesDiag));
        String fp = fingerprint("canonical-series", ruleVersion,
                run.id(), run.exoUp(), pointsFingerprint(run),
                massNodeId == null ? "no-mass" : massNodeId,
                cal == null ? "no-cal" : cal.id() + ":" + offsetK);
        nodes.put(seriesId, new DerivedNode(seriesId, "series", List.copyOf(seriesDeps),
                ruleVersion, fp, payload, massMg == null ? "incomplete" : "valid",
                List.copyOf(seriesDiag), 0));

        for (ScanSegment seg : state.explicitSegments.values()) {
            if (!seg.runId().equals(run.id())) {
                continue;
            }
            buildSegment(state, run, seg, canonical, massMg,
                    run.scanRate() != null && run.scanRate().isConvertible()
                            ? run.scanRate().canonical() : 0.0,
                    seriesId, nodes);
        }
    }

    private void buildSegment(ProjectState state, InstrumentRun run, ScanSegment seg,
                              List<CanonicalPoint> series, Double massMg, double rateKPerMin,
                              String seriesId, Map<String, DerivedNode> nodes) {
        List<CanonicalPoint> slice = sliceSegment(seg, series);
        List<String> segDiag = new ArrayList<>();
        String direction = seg.direction() == null ? "unknown" : seg.direction();
        if (!"heating".equals(direction) && !"cooling".equals(direction)) {
            segDiag.add("direction-unknown: declared via explicit run metadata only; "
                    + "baselines across heating/cooling are never mixed");
        }
        if (slice.size() < 2) {
            segDiag.add("segment-has-insufficient-points: " + slice.size());
        }
        String segNodeId = "node-" + seg.id();
        SegmentPayload segPayload = new SegmentPayload(seg, run.id(), slice.size(), List.copyOf(segDiag));
        String segFp = fingerprint("segment", ruleVersion, seg.id(), seg.fromIndex(), seg.toIndex(),
                seg.cycle(), seg.direction(), seriesId);
        nodes.put(segNodeId, new DerivedNode(segNodeId, "segment",
                List.of(seriesId), ruleVersion, segFp, segPayload,
                slice.size() < 2 ? "incomplete" : "valid", List.copyOf(segDiag), 0));

        List<TangentSelection> segTangents = state.tangents.values().stream()
                .filter(t -> t.segmentId().equals(seg.id()))
                .sorted(java.util.Comparator.comparing(TangentSelection::createdAtIso)).toList();
        TangentSelection tangent = segTangents.isEmpty() ? null : segTangents.get(segTangents.size() - 1);

        List<Window> segWindows = state.windows.values().stream()
                .filter(w -> w.segmentId().equals(seg.id()))
                .sorted(java.util.Comparator.comparingInt(w -> Math.min(w.startIndex(), w.endIndex())))
                .toList();
        int seq = 0;
        for (Window w : segWindows) {
            seq++;
            buildWindow(state, run, seg, w, slice, tangent, massMg, rateKPerMin,
                    seriesId, segNodeId, seq, nodes);
        }
    }

    private void buildWindow(ProjectState state, InstrumentRun run, ScanSegment seg, Window w,
                             List<CanonicalPoint> segPoints, TangentSelection tangent,
                             Double massMg, double rateKPerMin,
                             String seriesId, String segNodeId, int seq,
                             Map<String, DerivedNode> nodes) {
        int lo = Math.max(0, Math.min(w.startIndex(), w.endIndex()) - w.startIndex() + Math.min(w.startIndex(), w.endIndex()));
        int from = clamp(Math.min(w.startIndex(), w.endIndex()), seg.fromIndex(), seg.toIndex());
        int to = clamp(Math.max(w.startIndex(), w.endIndex()), seg.fromIndex(), seg.toIndex());
        List<CanonicalPoint> pts = new ArrayList<>();
        for (CanonicalPoint cp : segPoints) {
            if (cp.rawIndex() >= from && cp.rawIndex() <= to) {
                pts.add(cp);
            }
        }

        ProjectState.WindowMeta meta = state.windowMeta.get(w.id());
        String familyHint = meta == null ? null : meta.familyHint();
        boolean overlap = meta != null && meta.overlapFlag();

        BaselineModel.TangentContext tc = tangentContext(segPoints, tangent);
        List<BaselineModel> models = BaselineModels.all();
        String acceptedId = state.acceptedBaselines.get(w.id());

        String status;
        List<String> diag = new ArrayList<>();
        if (pts.size() < 2) {
            status = "empty";
            diag.add("empty-window: drag range contains no usable derived points");
        } else if (massMg == null) {
            status = "incomplete";
        } else if (acceptedId == null) {
            status = "multi-candidate";
        } else {
            status = "accepted";
        }

        TransitionAnalyzer.Result result;
        if (massMg == null) {
            EnthalpyResult noMass = new EnthalpyResult(0, "unknown", 0, List.of(),
                    "mass-missing: normalized enthalpy cannot be computed; raw heat flow retained in series node");
            result = new TransitionAnalyzer.Result(new PhaseTransition(
                    Ids.transitionId(w.id()), w.id(), seg.id(), w.label(),
                    familyHint == null ? "transition" : familyHint,
                    Double.NaN, Double.NaN, Double.NaN, List.of(), noMass, overlap,
                    List.of("mass-missing"), "incomplete"), List.of());
        } else {
            TransitionAnalyzer.Input input = new TransitionAnalyzer.Input(w.id(), seg.id(), w.label(),
                    familyHint, List.copyOf(pts), tc, models, massMg, rateKPerMin, acceptedId, overlap);
            result = TransitionAnalyzer.analyze(input, new WindowIds(w.id()), "");
        }

        List<String> deps = new ArrayList<>();
        deps.add(segNodeId);
        deps.add(seriesId);
        if (tangent != null) {
            deps.add(tangent.id());
        }
        if (massMg != null) {
            String massId = state.massesOf(run.sampleId()).get(state.massesOf(run.sampleId()).size() - 1).id();
            deps.add(massId);
        }
        String fp = fingerprint("transition", ruleVersion, w.id(),
                from, to, familyHint, overlap, acceptedId,
                tangent == null ? "no-tangent" : tangent.id(),
                segNodeId, seriesId,
                pointsFingerprintCanonical(pts));
        DerivedNode node = new DerivedNode(result.transition().id(), "transition",
                List.copyOf(deps), ruleVersion, fp, result.transition(), status,
                diag.isEmpty() ? result.transition().diagnostics()
                        : concat(diag, result.transition().diagnostics()), 0);
        nodes.put(node.id(), node);
    }

    private static BaselineModel.TangentContext tangentContext(List<CanonicalPoint> segPoints,
                                                               TangentSelection t) {
        if (t == null) {
            return BaselineModel.TangentContext.empty();
        }
        List<double[]> left = new ArrayList<>();
        List<double[]> right = new ArrayList<>();
        for (CanonicalPoint cp : segPoints) {
            if (cp.specificHeatFlowMilliwattPerGram() == null) {
                continue;
            }
            if (cp.rawIndex() >= t.leftFrom() && cp.rawIndex() <= t.leftTo()) {
                left.add(new double[]{cp.temperatureKelvin(), cp.specificHeatFlowMilliwattPerGram()});
            }
            if (cp.rawIndex() >= t.rightFrom() && cp.rawIndex() <= t.rightTo()) {
                right.add(new double[]{cp.temperatureKelvin(), cp.specificHeatFlowMilliwattPerGram()});
            }
        }
        return new BaselineModel.TangentContext(List.copyOf(left), List.copyOf(right));
    }

    private static List<CanonicalPoint> sliceSegment(ScanSegment seg, List<CanonicalPoint> series) {
        List<CanonicalPoint> out = new ArrayList<>();
        for (CanonicalPoint cp : series) {
            if (cp.rawIndex() >= seg.fromIndex() && cp.rawIndex() <= seg.toIndex()) {
                out.add(cp);
            }
        }
        return out;
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    private static List<String> concat(List<String> a, List<String> b) {
        List<String> all = new ArrayList<>(a);
        all.addAll(b);
        return List.copyOf(all);
    }

    private static double nonUniformity(List<CanonicalPoint> pts) {
        if (pts.size() < 3) {
            return 0;
        }
        double[] dt = new double[pts.size() - 1];
        double mean = 0;
        for (int i = 0; i < dt.length; i++) {
            dt[i] = pts.get(i + 1).timeSec() - pts.get(i).timeSec();
            mean += dt[i];
        }
        mean /= dt.length;
        if (mean <= 1e-12) {
            return 0;
        }
        double v = 0;
        for (double d : dt) {
            v += (d - mean) * (d - mean);
        }
        return Math.sqrt(v / dt.length) / mean;
    }

    private static String pointsFingerprint(InstrumentRun run) {
        StringBuilder sb = new StringBuilder();
        for (SeriesPoint p : run.points()) {
            sb.append(p.rawIndex()).append(';')
                    .append(p.timeSec()).append(';')
                    .append(p.temperatureKelvin()).append(';')
                    .append(p.rawHeatFlowMilliwatt()).append('|');
        }
        return Digest.shortHash(sb.toString());
    }

    private static String pointsFingerprintCanonical(List<CanonicalPoint> pts) {
        StringBuilder sb = new StringBuilder();
        for (CanonicalPoint p : pts) {
            sb.append(p.rawIndex()).append(';')
                    .append(p.timeSec()).append(';')
                    .append(p.temperatureKelvin()).append(';')
                    .append(p.specificHeatFlowMilliwattPerGram()).append('|');
        }
        return Digest.shortHash(sb.toString());
    }

    private static String fingerprint(Object... parts) {
        StringBuilder sb = new StringBuilder();
        for (Object o : parts) {
            sb.append(o).append('|');
        }
        return Digest.sha256Hex(sb.toString());
    }

    private static final class WindowIds implements Ids_ {
        private final String windowId;

        private WindowIds(String windowId) {
            this.windowId = windowId;
        }

        @Override
        public String transitionId(String wid) {
            return Ids.transitionId(windowId);
        }

        @Override
        public String baselineCandidateId(String wid, String modelKey) {
            return Ids.baselineCandidateId(windowId, modelKey);
        }
    }

    private interface Ids_ {
        String transitionId(String windowId);

        String baselineCandidateId(String windowId, String modelKey);
    }
}
