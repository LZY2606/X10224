package dsc.replay;

import dsc.domain.BaselineCandidate;
import dsc.domain.Derivation;
import dsc.domain.EnthalpyMetric;
import dsc.domain.Feature;
import dsc.domain.ScanSegment;
import dsc.domain.SourceDoc;
import dsc.engine.AnalysisContext;
import dsc.engine.RunAnalyzer;
import dsc.store.Json;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 依赖图传播：给出来源版本集合，只重算受影响的 run/feature/metric，
 * 未变节点沿用上一版本的派生结果（新版本行拷贝并标记 valid）。
 */
public final class GraphEngine {

    public record RebuildResult(
            List<Derivation> derivations,
            Map<String, RunAnalyzer.AnalyzedRun> analyzed,
            List<String> notes
    ) {
    }

    private Map<String, Derivation> latestVersions = Map.of();

    public RebuildResult rebuild(Map<String, SourceDoc> sources,
                                 SourceBundle bundle,
                                 List<Derivation> previous,
                                 Map<String, Integer> changedSourceVersions,
                                 long eventSeq,
                                 boolean propagate) {
        Map<String, Derivation> prevByKey = new LinkedHashMap<>();
        for (Derivation derivation : previous) {
            prevByKey.put(derivation.key(), derivation);
        }
        latestVersions = prevByKey;
        List<Derivation> out = new ArrayList<>();
        Map<String, RunAnalyzer.AnalyzedRun> analyzed = new LinkedHashMap<>();
        List<String> notes = new ArrayList<>();

        for (SourceBundle.RunSources runSources : bundle.runs().values()) {
            Map<String, Integer> runDeps = depVersions(runSources, null);
            boolean runAffected = anyChanged(runDeps, changedSourceVersions);
            String runKey = "run:" + runSources.runId();
            RunAnalyzer.AnalyzedRun analyzedRun = null;

            if (runAffected || !prevByKey.containsKey(runKey)) {
                analyzedRun = analyze(runSources, eventSeq);
                analyzed.put(runSources.runId(), analyzedRun);
                out.add(derivation(runKey, "run", "segments", runDeps,
                        Derivation.VALID, eventSeq,
                        List.of(), Json.write(Map.of(
                                "segments", analyzedRun.segments().size(),
                                "features", analyzedRun.features().size())),
                        reason(runDeps, changedSourceVersions)));
                notes.add("重算 " + runSources.runId() + "："
                        + reason(runDeps, changedSourceVersions));
            } else {
                out.add(carry(prevByKey.get(runKey), eventSeq,
                        "扫描来源版本未变，段划分沿用旧结果"));
            }

            if (analyzedRun == null && prevByKey.containsKey(runKey)) {
                analyzedRun = reAnalyzeForFeatures(runSources, eventSeq);
                analyzed.put(runSources.runId(), analyzedRun);
            }
            if (analyzedRun == null) {
                analyzedRun = analyze(runSources, eventSeq);
                analyzed.put(runSources.runId(), analyzedRun);
            }

            for (Feature feature : analyzedRun.features()) {
                Map<String, Integer> featureDeps = depVersions(runSources, feature.id());
                String facet = facetOf(featureDeps, changedSourceVersions);
                Derivation prevFeature = prevByKey.get(feature.id());
                boolean affected = runAffected || anyChanged(featureDeps, changedSourceVersions)
                        || prevFeature == null;
                List<String> diagnostics = new ArrayList<>(feature.diagnostics());
                if (feature.onsetT() >= feature.endsetT()) {
                    diagnostics.add("EMPTY_INTERVAL:可拖动区间为空或反向，积分不可用");
                }
                if (affected || propagate) {
                    String status = feature.status();
                    String why = "由 " + facet + " 改版触发重算";
                    out.add(derivation(feature.id(), facet, feature.kind().code(),
                            featureDeps, status, eventSeq, diagnostics,
                            Json.write(stripPoints(feature)), why));
                    notes.add("重算 " + feature.id() + "（" + facet + "）");
                } else {
                    out.add(carry(prevFeature, eventSeq,
                            "该转折依赖版本未变，检测结果仍有效"));
                }

                String metricKey = feature.enthalpy() == null ? null
                        : feature.enthalpy().id();
                if (metricKey != null) {
                    Derivation prevMetric = prevByKey.get(metricKey);
                    boolean metricAffected = affected
                            || (prevMetric == null);
                    if (metricAffected) {
                        out.add(derivation(metricKey, "normalized", "enthalpy",
                                featureDeps,
                                feature.enthalpy().valueJPerG() == 0
                                        && feature.onsetT() >= feature.endsetT()
                                        ? Derivation.EMPTY : Derivation.VALID,
                                eventSeq, diagnostics,
                                Json.write(feature.enthalpy()),
                                massChanged(featureDeps, changedSourceVersions)
                                        ? "质量换版：归一化派生过期后重算"
                                        : "随转折重算"));
                    } else if (prevMetric != null) {
                        out.add(carry(prevMetric, eventSeq,
                                "质量与基线版本未变，归一化结果仍有效"));
                    }
                }
            }
        }
        return new RebuildResult(out, analyzed, notes);
    }

    private RunAnalyzer.AnalyzedRun analyze(SourceBundle.RunSources runSources, long seq) {
        List<String> diagnostics = new ArrayList<>();
        AnalysisContext context = new AnalysisContext(
                runSources.instrument(), runSources.mass(), runSources.rate(),
                runSources.atmosphere(), runSources.calibration(), runSources.tangent(),
                diagnostics);
        return RunAnalyzer.analyze(context);
    }

    private RunAnalyzer.AnalyzedRun reAnalyzeForFeatures(SourceBundle.RunSources runSources,
                                                         long seq) {
        return analyze(runSources, seq);
    }

    private Map<String, Integer> depVersions(SourceBundle.RunSources runSources,
                                             String featureId) {
        Map<String, Integer> deps = new LinkedHashMap<>();
        putVersion(deps, runSources.instrument());
        putVersion(deps, runSources.mass());
        putVersion(deps, runSources.rate());
        putVersion(deps, runSources.atmosphere());
        putVersion(deps, runSources.calibration());
        if (featureId != null) {
            SourceDoc tangent = runSources.tangent();
            if (tangent != null && (tangent.fields().get("feature_id") == null
                    || featureId.equals(tangent.fields().get("feature_id")))) {
                putVersion(deps, tangent);
            }
        } else {
            putVersion(deps, runSources.tangent());
        }
        return deps;
    }

    private void putVersion(Map<String, Integer> deps, SourceDoc source) {
        if (source != null) {
            deps.put(source.id(), source.version());
        }
    }

    private boolean anyChanged(Map<String, Integer> deps,
                               Map<String, Integer> changed) {
        return deps.entrySet().stream()
                .anyMatch(e -> changed.containsKey(e.getKey())
                        && !changed.get(e.getKey()).equals(e.getValue()));
    }

    private String facetOf(Map<String, Integer> deps, Map<String, Integer> changed) {
        for (String key : changed.keySet()) {
            if (key.contains(":mass:")) {
                return "mass";
            }
            if (key.contains(":temp_calibration")) {
                return "calibration";
            }
            if (key.contains(":tangent_selection")) {
                return "tangent";
            }
            if (key.contains(":scan_rate")) {
                return "rate";
            }
            if (key.contains(":instrument_output")) {
                return "instrument";
            }
        }
        return "source";
    }

    private boolean massChanged(Map<String, Integer> deps, Map<String, Integer> changed) {
        return changed.keySet().stream().anyMatch(k -> k.contains(":mass:"));
    }

    private String reason(Map<String, Integer> deps, Map<String, Integer> changed) {
        List<String> parts = new ArrayList<>();
        for (String key : changed.keySet()) {
            if (deps.containsKey(key)) {
                parts.add(key + "@v" + changed.get(key));
            }
        }
        return parts.isEmpty() ? "首次计算" : "依赖改版：" + String.join(", ", parts);
    }

    private Derivation derivation(String key, String facet, String kind,
                                  Map<String, Integer> deps, String status,
                                  long eventSeq, List<String> diagnostics,
                                  String json, String why) {
        Derivation previous = latestVersions.get(key);
        int version = previous == null ? 1 : previous.version() + 1;
        return new Derivation(key, facet, version, status, kind, eventSeq,
                new LinkedHashMap<>(deps), diagnostics, why, json);
    }

    private Derivation carry(Derivation previous, long eventSeq, String why) {
        if (previous == null) {
            return null;
        }
        return new Derivation(previous.key(), previous.facet(), previous.version(),
                previous.status(), previous.kind(), eventSeq,
                previous.dependencyVersions(), previous.diagnostics(), why, previous.json());
    }

    private Feature stripPoints(Feature feature) {
        List<BaselineCandidate> compact = feature.baselines().stream()
                .map(c -> new BaselineCandidate(c.id(), c.featureId(), c.method(),
                        c.label(), c.a(), c.b(), null, List.of(), c.residualRms(),
                        c.adopted(), c.rationale()))
                .toList();
        return feature.withBaselines(compact, feature.adoptedBaselineId());
    }
}
