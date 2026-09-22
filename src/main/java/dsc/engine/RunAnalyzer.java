package dsc.engine;

import dsc.domain.BaselineCandidate;
import dsc.domain.EnthalpyMetric;
import dsc.domain.Feature;
import dsc.domain.ScanSegment;
import dsc.domain.SourceDoc;
import dsc.domain.TangentSelection;
import dsc.domain.Units;
import dsc.ingest.CsvRunParser;
import dsc.ingest.ParsedRun;

import java.util.ArrayList;
import java.util.List;

/**
 * 单条 run 的完整分析：解析原始仪器输出（保留原始表示）→ 单位规范化 →
 * 标定仅在派生层应用 → 实测切段 → 检测 → 并列基线 → 焓变与误差。
 */
public final class RunAnalyzer {

    public record AnalyzedRun(
            ParsedRun parsed,
            double[][] points,
            int tCol,
            int hfCol,
            List<ScanSegment> segments,
            List<Feature> features,
            boolean polarityReversed,
            boolean polarityAmbiguous,
            List<String> diagnostics
    ) {
    }

    private RunAnalyzer() {
    }

    public static AnalyzedRun analyze(AnalysisContext context) {
        List<String> diagnostics = new ArrayList<>(context.diagnostics());
        SourceDoc instrument = context.instrument();
        String rawText = instrument.rawText();
        String fileName = rawText.lines().filter(l -> l.startsWith("file:")).findFirst()
                .map(l -> l.substring(5).trim()).orElse("instrument.csv");
        ParsedRun parsed = CsvRunParser.parse(fileName,
                rawText.lines().filter(l -> !l.startsWith("file:"))
                        .reduce("", (a, b) -> a + b + "\n"));

        int tIdx = parsed.columnIndex("t");
        int timeIdx = parsed.columnIndex("time");
        int hfIdx = chooseHeatflowColumn(parsed);
        if (hfIdx < 0) {
            throw new IllegalStateException("仪器输出缺少 heatflow 列: " + parsed.fileName());
        }
        double tempOffset = calibrationOffset(context.calibration(), diagnostics);

        int n = parsed.rows().size();
        double[][] points = new double[n][3];
        boolean rawTemps = false;
        for (int i = 0; i < n; i++) {
            String[] row = parsed.rows().get(i);
            double timeSec;
            if (timeIdx >= 0) {
                timeSec = Double.parseDouble(row[timeIdx].trim());
            } else {
                Units.Quantity tq = Units.parse(row[tIdx >= 0 ? tIdx : 0].trim());
                timeSec = i * 60.0;
            }
            Units.Quantity temp = Units.parse(row[tIdx >= 0 ? tIdx : 1].trim());
            Units.Quantity hf = Units.parse(row[hfIdx].trim());
            if (!temp.convertible()) {
                diagnostics.add("UNCONVERTIBLE_TEMPERATURE@row" + i + ":"
                        + (temp.diagnostic() == null ? "" : temp.diagnostic().message()));
                rawTemps = true;
            }
            if (!hf.convertible()) {
                diagnostics.add("UNCONVERTIBLE_HEATFLOW@row" + i + ":"
                        + (hf.diagnostic() == null ? "" : hf.diagnostic().message()));
            }
            points[i][0] = timeIdx >= 0 ? timeSec : i;
            points[i][1] = (temp.siValue() == null ? Double.NaN : temp.siValue()) + tempOffset;
            points[i][2] = hf.siValue() == null ? Double.NaN : hf.siValue();
        }

        String declared = parsed.headerOr("declared_direction", "unknown").toLowerCase();
        String convention = parsed.headerOr("sign_convention", "exo_up").toLowerCase();
        String polarityHeader = parsed.headerOr("heatflow_polarity", "").toLowerCase();

        List<ScanSegment> segments = Segmenter.segments(points);
        boolean anyHeating = segments.stream().anyMatch(ScanSegment::heating);
        boolean anyCooling = segments.stream().anyMatch(s -> !s.heating());
        boolean declaredHeating = declared.contains("heat") || declared.contains("加热");
        boolean declaredCooling = declared.contains("cool") || declared.contains("冷却");

        boolean polarityAmbiguous = false;
        boolean polarityReversed = "endo_up".equals(convention) || "reverse".equals(polarityHeader);
        if ((declaredHeating && anyCooling && !anyHeating)
                || (declaredCooling && anyHeating && !anyCooling)) {
            polarityAmbiguous = true;
            diagnostics.add("SIGN_POLARITY_CONFLICT:声明方向 " + declared
                    + " 与实测温度轨迹不一致；不按文件名猜测方向，保留两种解释待裁决");
        }
        if ("ambiguous".equals(polarityHeader)) {
            polarityAmbiguous = true;
            diagnostics.add("SIGN_POLARITY_AMBIGUOUS:仪器极性档案标记 ambiguous，焓变符号待人工裁定");
        }
        if (segments.size() > 1) {
            diagnostics.add("CYCLIC_SEGMENT_SWITCH:实测轨迹含 " + segments.size()
                    + " 个扫描段，加热与冷却段隔离计算，不混用");
        }
        segments.stream().filter(ScanSegment::nonuniform).findAny().ifPresent(s ->
                diagnostics.add("NONUNIFORM_TEMPERATURE_SPACING@seg" + s.index()
                        + ":温度采样非等间隔，积分采用时间轴梯形法"));

        String runId = instrument.runId();
        List<Feature> features = new ArrayList<>();
        List<String> hints = parsed.rawHeaderLines().stream()
                .filter(l -> l.toLowerCase().startsWith("hint")).map(l -> l.substring(4)).toList();
        for (ScanSegment segment : segments) {
            List<Feature> detected = FeatureDetector.detect(runId, points, 2,
                    segment, hints, 0L);
            for (Feature feature : detected) {
                Feature enriched = applyTangent(feature, points, 2, context.tangent());
                boolean overlapNext = enriched.diagnostics().stream()
                        .anyMatch(x -> x.startsWith("OVERLAPPING"));
                List<BaselineCandidate> candidates = Baselines.forFeature(
                        enriched, points, 2, overlapNext);
                String adopted = defaultAdopt(candidates, enriched, context.tangent());
                List<BaselineCandidate> finalCandidates = new ArrayList<>();
                for (BaselineCandidate candidate : candidates) {
                    finalCandidates.add(candidate.withAdoption(
                            candidate.id().equals(adopted),
                            candidate.id().equals(adopted) ? "默认采纳（可在复核界面改选）"
                                    : candidate.rationale()));
                }
                enriched = enriched.withBaselines(finalCandidates, adopted);
                EnthalpyMetric enthalpy = Integrator.enthalpy(enriched, points, 2,
                        chosen(finalCandidates, adopted), finalCandidates,
                        integralInputs(context, segment, polarityReversed, polarityAmbiguous));
                enriched = enriched.withEnthalpy(enthalpy);
                features.add(enriched);
            }
        }
        if (rawTemps) {
            diagnostics.add("RAW_VALUES_RETAINED:无法转换的温度原值保留在来源档案中，未为画图丢弃");
        }
        return new AnalyzedRun(parsed, points, tIdx, hfIdx, segments, features,
                polarityReversed, polarityAmbiguous, diagnostics);
    }

    private static Feature applyTangent(Feature feature, double[][] points, int hfCol,
                                        SourceDoc tangentDoc) {
        if (tangentDoc == null) {
            return feature;
        }
        String target = tangentDoc.fields().get("feature_id");
        if (target != null && !target.equals(feature.id())) {
            return feature;
        }
        String left = tangentDoc.fields().get("left_t");
        String right = tangentDoc.fields().get("right_t");
        if (left == null || right == null) {
            return feature;
        }
        double l = Units.parse(left).siOrThrow();
        double r = Units.parse(right).siOrThrow();
        return new Feature(feature.id(), feature.runId(), feature.kind(), feature.rank(),
                feature.segmentIndex(), feature.heating(),
                l, feature.peakT(), r, feature.peakDeviation(),
                feature.onsetPower(), feature.peakPower(), feature.endsetPower(),
                feature.baselines(), feature.adoptedBaselineId(), feature.enthalpy(),
                feature.status(), feature.diagnostics(), feature.computedAtEvent());
    }

    private static String defaultAdopt(List<BaselineCandidate> candidates, Feature feature,
                                       SourceDoc tangentDoc) {
        if (tangentDoc != null) {
            String method = tangentDoc.fields().get("baseline_method");
            if (method != null) {
                for (BaselineCandidate candidate : candidates) {
                    if (candidate.method().equals(method)) {
                        return candidate.id();
                    }
                }
            }
        }
        return candidates.get(0).id();
    }

    private static BaselineCandidate chosen(List<BaselineCandidate> candidates, String id) {
        return candidates.stream().filter(c -> c.id().equals(id)).findFirst()
                .orElse(candidates.get(0));
    }

    private static Integrator.IntegralInputs integralInputs(AnalysisContext context,
                                                             ScanSegment segment,
                                                             boolean polarityReversed,
                                                             boolean polarityAmbiguous) {
        double massKg = 1e-3;
        double massRel = 0.002;
        if (context.mass() != null) {
            Units.Quantity q = Units.parse(context.mass().fields().getOrDefault("mass", "1 g"));
            massKg = q.siOrThrow();
            massRel = parseRel(context.mass().fields().get("uncertainty_rel"), 0.002);
        }
        double rateRel = parseRel(context.rate() == null ? null
                : context.rate().fields().get("uncertainty_rel"), 0.01);
        double calibUncertainty = context.calibration() == null ? 0.5
                : parseDouble(context.calibration().fields().get("uncertainty_k"), 0.5);
        return new Integrator.IntegralInputs(massKg, massRel,
                segment.rateKPerSec(), rateRel, 0, calibUncertainty,
                polarityReversed, polarityAmbiguous);
    }

    private static double calibrationOffset(SourceDoc calibration, List<String> diagnostics) {
        if (calibration == null) {
            return 0;
        }
        String offset = calibration.fields().get("offset_k");
        if (offset == null) {
            return 0;
        }
        try {
            return Double.parseDouble(offset.trim());
        } catch (NumberFormatException e) {
            diagnostics.add("BAD_CALIBRATION_OFFSET:" + offset + " 原样保留，派生层不应用");
            return 0;
        }
    }

    private static int chooseHeatflowColumn(ParsedRun parsed) {
        int standard = parsed.columnIndex("heatflow");
        if (standard >= 0) {
            return standard;
        }
        int hf = parsed.columnIndex("hf");
        if (hf >= 0) {
            return hf;
        }
        return -1;
    }

    private static double parseRel(String text, double fallback) {
        if (text == null) {
            return fallback;
        }
        try {
            return Double.parseDouble(text.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static double parseDouble(String text, double fallback) {
        if (text == null) {
            return fallback;
        }
        try {
            return Double.parseDouble(text.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

}
