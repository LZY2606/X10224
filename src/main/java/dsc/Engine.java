package dsc;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public final class Engine {
    public static final String RULE_VERSION = "dsc-domain-rules-1.0.0";

    public Map<String, Object> replay(List<EventRecord> events) {
        State state = new State();
        for (EventRecord event : events) {
            apply(state, event);
        }
        return state.view(null);
    }

    public Map<String, Object> replayAt(List<EventRecord> events, Long atSeq, String compareFrom, String compareTo) {
        State state = new State();
        EventRecord compareEvent = null;
        for (EventRecord event : events) {
            apply(state, event);
            if (atSeq != null && event.seq() == atSeq) {
                compareEvent = event;
            }
        }
        Map<String, Object> view = state.view(atSeq);
        if (compareFrom != null && compareTo != null) {
            view.put("comparison", compareBranches(state, compareFrom, compareTo));
        }
        if (compareEvent != null) {
            view.put("restoredEventId", compareEvent.eventId());
        }
        return view;
    }

    private void apply(State state, EventRecord event) {
        recordSource(state, event);
        if (isSourceEvent(event.eventType())) {
            state.revision = event.seq();
            recompute(state);
        } else if ("DECISION".equals(event.eventType())) {
            applyDecision(state, event);
        }
        state.events.add(event);
    }

    private boolean isSourceEvent(String type) {
        return Set.of("INSTRUMENT_OUTPUT", "MASS", "SCAN_RATE", "ATMOSPHERE", "TEMPERATURE_CALIBRATION",
                "TANGENT", "PARAMETER_REVISION", "SOURCE_DATA_CHANGE").contains(type);
    }

    private void recordSource(State state, EventRecord event) {
        if (!isSourceEvent(event.eventType())) {
            return;
        }
        String sourceId = string(event.payload(), "sourceId", null);
        if (sourceId == null) {
            sourceId = defaultSourceId(event);
        }
        Source existing = state.sources.get(sourceId);
        int version = existing == null ? 1 : existing.version + 1;
        String segmentId = string(event.payload(), "segmentId", sourceId);
        state.sources.put(sourceId, new Source(sourceId, event.eventType(), version, segmentId, event));
    }

    private String defaultSourceId(EventRecord event) {
        return switch (event.eventType()) {
            case "MASS" -> "mass";
            case "ATMOSPHERE" -> "atmosphere";
            case "TEMPERATURE_CALIBRATION" -> "temperature-calibration";
            case "TANGENT" -> "tangent-" + string(event.payload(), "subject", "transition");
            default -> string(event.payload(), "segmentId", "segment");
        };
    }

    private void recompute(State state) {
        Map<String, Source> instruments = sourcesByKind(state, "INSTRUMENT_OUTPUT");
        Map<String, Artifact> oldCurves = new LinkedHashMap<>(state.artifacts);
        for (Source instrument : instruments.values()) {
            String segmentId = instrument.segmentId;
            Map<String, Object> curve = buildCurve(state, instrument);
            Map<String, Object> depends = curveDependencies(state, segmentId, instrument);
            String stable = "curve." + segmentId;
            int curveVersion = nextVersion(state, stable);
            if (!fingerprintChanged(state, stable, depends, curve)) {
                continue;
            }
            Artifact artifact = new Artifact(
                    state.revision, did(stable, curveVersion), stable, "CURVE", segmentId, null,
                    curveVersion, "proposed", depends, curve,
                    diagnostics(curve, "DIAGNOSTIC"));
            state.artifacts.put(did(stable, curveVersion), artifact);
            buildTransitionArtifacts(state, instrument, artifact);
        }
    }

    private Map<String, Object> curveDependencies(State state, String segmentId, Source instrument) {
        Map<String, Object> depends = new LinkedHashMap<>();
        depends.put("instrument:" + segmentId, instrument.event.eventId() + ":v" + instrument.version);
        Source calibration = latestByKind(state, "TEMPERATURE_CALIBRATION");
        Source mass = latestByKind(state, "MASS");
        Source atmosphere = latestByKind(state, "ATMOSPHERE");
        Source rate = sourcesByKind(state, "SCAN_RATE").values().stream()
                .filter(source -> segmentId.equals(source.segmentId))
                .findFirst()
                .orElse(null);
        if (calibration != null) depends.put("temperatureCalibration", calibration.event.eventId() + ":v" + calibration.version);
        if (mass != null) depends.put("mass", mass.event.eventId() + ":v" + mass.version);
        if (atmosphere != null) depends.put("atmosphere", atmosphere.event.eventId() + ":v" + atmosphere.version);
        if (rate != null) depends.put("scanRate:" + segmentId, rate.event.eventId() + ":v" + rate.version);
        depends.put("rules", RULE_VERSION);
        return depends;
    }

    private Map<String, Object> buildCurve(State state, Source instrument) {
        Map<String, Object> p = instrument.event.payload();
        String segmentId = instrument.segmentId;
        String phase = upper(string(p, "phase", "UNKNOWN"));
        String convention = upper(string(p, "signalConvention", string(p, "heatFlowDirection", "ENDOTHERM_UP")));
        double offset = numericSource(latestByKind(state, "TEMPERATURE_CALIBRATION"), "offsetK", "offset", 0.0);
        double massMg = numericSource(latestByKind(state, "MASS"), "massMg", "valueMg", Double.NaN);
        Source rateSource = sourcesByKind(state, "SCAN_RATE").values().stream()
                .filter(source -> segmentId.equals(source.segmentId)).findFirst().orElse(null);
        double rate = numericSource(rateSource, "rateKPerMin", "value", Double.NaN);
        List<Map<String, Object>> rawPoints = list(p, "points");
        List<Map<String, Object>> points = new ArrayList<>();
        List<Map<String, Object>> diagnostics = new ArrayList<>();
        int invalid = 0;
        for (int i = 0; i < rawPoints.size(); i++) {
            Map<String, Object> raw = rawPoints.get(i);
            Double t = number(raw, "tMin", "timeMin", "t");
            Double temp = number(raw, "tempC", "temperatureC", "temperature");
            Double heat = number(raw, "heatFlowMw", "heatFlow", "value");
            Map<String, Object> point = new LinkedHashMap<>(raw);
            point.put("index", i);
            if (t != null) point.put("tSec", round(t * 60.0));
            if (temp != null) point.put("temperatureC", round(temp + offset));
            boolean valid = t != null && temp != null && heat != null && !Double.isNaN(massMg) && massMg != 0.0;
            if (valid) {
                double endothermicUpHeat = "EXOTHERM_UP".equals(convention) ? -heat : heat;
                point.put("normalizedHeatFlowWPerG", round(endothermicUpHeat / massMg / 1000.0));
                point.put("normalizedHeatFlowMwPerMg", round(endothermicUpHeat / massMg));
            } else {
                invalid++;
                point.put("valid", false);
                diagnostics.add(diagnostic("UNCONVERTIBLE_POINT", "Raw value remains in original representation; point excluded from derivation.",
                        Map.of("index", i, "raw", raw)));
            }
            points.set(i, point);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("segmentId", segmentId);
        result.put("phase", phase);
        result.put("signalConvention", convention);
        result.put("coordinateBasis", Map.of(
                "x", "temperatureC:ITS-90-style calibrated laboratory basis",
                "y", "normalizedHeatFlowWPerG:endothermic-positive",
                "time", "tSec"));
        result.put("scanRateKPerMin", round(rate));
        result.put("sampleMassMg", round(massMg));
        result.put("points", points);
        result.put("validPointCount", points.size() - invalid);
        addGridDiagnostics(result, points, phase, convention, diagnostics);
        result.put("diagnostics", diagnostics);
        return result;
    }

    private void addGridDiagnostics(Map<String, Object> result, List<Map<String, Object>> points,
                                    String phase, String convention, List<Map<String, Object>> diagnostics) {
        List<Map<String, Object>> valid = points.stream()
                .filter(point -> Boolean.TRUE.equals(point.get("valid")) || point.containsKey("normalizedHeatFlowWPerG"))
                .toList();
        if ("EXOTHERM_UP".equals(convention)) {
            diagnostics.add(diagnostic("REVERSE_HEAT_FLOW",
                    "Instrument reports exotherm-up; domain values are converted using segment metadata, not the file name.",
                    Map.of("declaredConvention", convention)));
        }
        if ("COOLING".equals(phase) && number(result, "scanRateKPerMin").orElse(-1.0) > 0) {
            diagnostics.add(diagnostic("PHASE_RATE_CONFLICT", "Cooling segment must have a negative signed scan rate.", Map.of()));
        }
        double[] times = valid.stream().mapToDouble(point -> ((Number) point.get("tSec")).doubleValue()).toArray();
        double[] temps = valid.stream().mapToDouble(point -> ((Number) point.get("temperatureC")).doubleValue()).toArray();
        if (times.length > 2) {
            double mean = mean(spacing(times));
            double maxGap = Arrays.stream(spacing(times)).max().orElse(0);
            if (mean > 0 && maxGap / mean > 1.25) {
                diagnostics.add(diagnostic("NONUNIFORM_TIME_GRID",
                        "Time-weighted integration uses tSec; temperature spacing is not assumed constant.",
                        Map.of("meanGapSec", round(mean), "maxGapSec", round(maxGap))));
            }
        }
        for (int i = 1; i < temps.length; i++) {
            if ("HEATING".equals(phase) && temps[i] < temps[i - 1] - 5) {
                diagnostics.add(diagnostic("CYCLE_SEGMENT_SWITCH", "A temperature discontinuity indicates a heating/cooling cycle boundary.", Map.of("index", i)));
            }
        }
        result.put("diagnostics", diagnostics);
    }

    private void buildTransitionArtifacts(State state, Source instrument, Artifact curveArtifact) {
        List<Source> tangents = sourcesByKind(state, "TANGENT").values().stream()
                .filter(source -> instrument.segmentId.equals(source.segmentId))
                .toList();
        for (Source tangent : tangents) {
            buildBaselineAndIntegration(state, curveArtifact, tangent, upper(string(tangent.event.payload(), "mode", "TANGENT")));
            if ("PEAK".equals(upper(string(tangent.event.payload(), "transitionClass", "PEAK")))) {
                buildBaselineAndIntegration(state, curveArtifact, tangent, "CHORD");
            }
        }
    }

    private void buildBaselineAndIntegration(State state, Artifact curve, Source tangent, String mode) {
        Map<String, Object> tp = tangent.event.payload();
        String subject = string(tp, "subject", "transition");
        String candidateId = string(tp, "candidateId", "primary");
        String stable = "CHORD".equals(mode)
                ? "baseline." + curve.segmentId() + "." + subject + ".chord"
                : "baseline." + curve.segmentId() + "." + subject + "." + candidateId;
        Map<String, Object> result = baselineResult(curve, tangent, mode);
        Map<String, Object> depends = new LinkedHashMap<>();
        depends.put("curve", curve.derivationId());
        depends.put("tangent", tangent.event.eventId() + ":v" + tangent.version);
        depends.put("rules", RULE_VERSION);
        if (!fingerprintChanged(state, stable, depends, result)) {
            return;
        }
        int version = nextVersion(state, stable);
        Artifact baseline = new Artifact(state.revision, did(stable, version), stable, "BASELINE",
                curve.segmentId(), subject, version, "proposed", depends, result,
                diagnostics(result, "DIAGNOSTIC"));
        state.artifacts.put(baseline.derivationId(), baseline);
        buildIntegration(state, curve, baseline, tangent);
    }

    private Map<String, Object> baselineResult(Artifact curve, Source tangent, String mode) {
        Map<String, Object> tp = tangent.event.payload();
        double from = Double.parseDouble(String.valueOf(tp.getOrDefault("fromC", tp.get("startC"))));
        double to = Double.parseDouble(String.valueOf(tp.getOrDefault("toC", tp.get("endC"))));
        List<Map<String, Object>> valid = validCurvePoints(curve);
        List<Map<String, Object>> event = pointsBetween(valid, from, to);
        List<Map<String, Object>> diagnostics = new ArrayList<>();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("segmentId", curve.segmentId());
        result.put("subject", tangent.subjectOr("transition"));
        result.put("candidateId", "CHORD".equals(mode) ? "chord" : string(tp, "candidateId", "primary"));
        result.put("mode", mode);
        result.put("interval", interval("C", from, to));
        result.put("transitionClass", upper(string(tp, "transitionClass", "PEAK")));
        if (event.isEmpty()) {
            diagnostics.add(diagnostic("EMPTY_INTERVAL", "The selected interval contains no valid DSC sample points.",
                    Map.of("interval", interval("C", from, to), "curveDerivationId", curve.derivationId())));
            result.put("points", List.of());
            result.put("valid", false);
            result.put("diagnostics", diagnostics);
            return result;
        }
        Line line;
        if ("STEP".equals(mode)) {
            List<Map<String, Object>> pre = anchors(valid, tp, "preFromC", "preToC");
            List<Map<String, Object>> post = anchors(valid, tp, "postFromC", "postToC");
            if (pre.size() < 2 || post.size() < 2) {
                diagnostics.add(diagnostic("EMPTY_INTERVAL", "Glass transition requires at least two points on each tangent plateau.", Map.of("preCount", pre.size(), "postCount", post.size())));
                result.put("points", List.of());
                result.put("valid", false);
                result.put("diagnostics", diagnostics);
                return result;
            }
            Line preLine = fit(pre);
            Line postLine = fit(post);
            double mid = (from + to) / 2.0;
            List<Map<String, Object>> points = baselinePoints(event, temp -> temp < mid ? preLine : postLine, "STEP");
            result.put("preLine", lineResult(preLine));
            result.put("postLine", lineResult(postLine));
            result.put("stepAtMidpointWPerG", round(postLine.at(mid) - preLine.at(mid)));
            result.put("points", points);
            result.put("rmseWPerG", round(Math.max(rmse(pre, preLine), rmse(post, postLine))));
            result.put("valid", true);
        } else {
            List<Map<String, Object>> anchors;
            if ("CHORD".equals(mode)) {
                anchors = List.of(nearest(valid, from), nearest(valid, to));
            } else {
                anchors = new ArrayList<>(anchors(valid, tp, "preFromC", "preToC"));
                anchors.addAll(anchors(valid, tp, "postFromC", "postToC"));
            }
            if (anchors.size() < 2) {
                diagnostics.add(diagnostic("EMPTY_INTERVAL", "A baseline needs at least two anchor points.", Map.of("anchorCount", anchors.size())));
                result.put("points", List.of());
                result.put("valid", false);
                result.put("diagnostics", diagnostics);
                return result;
            }
            line = fit(anchors);
            List<Map<String, Object>> points = baselinePoints(event, ignored -> line, mode);
            result.put("line", lineResult(line));
            result.put("points", points);
            result.put("rmseWPerG", round(rmse(anchors, line)));
            result.put("valid", true);
        }
        addOverlapDiagnostic(curve, tangent, result, diagnostics);
        if (hasNonuniformTime(event)) {
            diagnostics.add(diagnostic("NONUNIFORM_TIME_GRID", "The baseline spans a nonuniform acquisition interval and area uses explicit time weights.", Map.of("subject", result.get("subject"))));
        }
        result.put("diagnostics", diagnostics);
        return result;
    }

    private List<Map<String, Object>> baselinePoints(List<Map<String, Object>> event, LineAtTemp lineAt, String mode) {
        List<Map<String, Object>> points = new ArrayList<>();
        for (int i = 0; i < event.size(); i++) {
            Map<String, Object> input = event.get(i);
            double temp = ((Number) input.get("temperatureC")).doubleValue();
            double y = ((Number) input.get("normalizedHeatFlowWPerG")).doubleValue();
            double baseline = lineAt.at(temp);
            Map<String, Object> point = new LinkedHashMap<>();
            point.put("id", "point#" + i);
            point.put("tSec", input.get("tSec"));
            point.put("temperatureC", temp);
            point.put("heatFlowWPerG", round(y));
            point.put("baselineWPerG", round(baseline));
            point.put("residualWPerG", round(y - baseline));
            points.add(point);
        }
        return points;
    }
