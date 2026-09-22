package dsc.engine;

import dsc.domain.InstrumentSeries;
import dsc.domain.Segmenter;
import dsc.domain.SignConvention;
import dsc.state.State;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Everything a recomputation needs, built by folding a branch. Holds parsed
 * series, segments, the sign convention, active calibration, declared rate,
 * atmosphere, current mass and tangent sources.
 */
public class InputBundle {

    public final State state;
    public final Map<String, InstrumentSeries> series = new LinkedHashMap<>();
    public final Map<String, List<Segmenter.Segment>> segments = new LinkedHashMap<>();
    public SignConvention sign = SignConvention.UNKNOWN;
    public String signSourceEventId;
    public String signSourceText;

    public Map<String, Object> activeMass;
    public Map<String, Object> activeCalibration;
    public final Map<String, Map<String, Object>> ratesByRun = new LinkedHashMap<>();
    public final Map<String, Map<String, Object>> atmosphereByRun = new LinkedHashMap<>();
    public final List<Map<String, Object>> bundleDiagnostics = new ArrayList<>();

    public InputBundle(State state, java.util.function.BiFunction<String, String,
            InstrumentSeries> seriesLoader) {
        this.state = state;
        int highestCal = Integer.MIN_VALUE;
        for (Map<String, Object> c : state.calibrations.values()) {
            int rev = num(c.get("revision"), 1);
            if (rev > highestCal) {
                highestCal = rev;
                activeCalibration = c;
            }
        }
        int highestMass = Integer.MIN_VALUE;
        for (Map<String, Object> m : state.masses.values()) {
            int rev = num(m.get("revision"), 1);
            if (rev > highestMass) {
                highestMass = rev;
                activeMass = m;
            }
        }
        for (Map<String, Object> r : state.runs.values()) {
            String runId = String.valueOf(r.get("runId"));
            String csv = (String) r.get("csv");
            if (csv == null && seriesLoader != null) {
                InstrumentSeries loaded = seriesLoader.apply(runId, null);
                if (loaded != null) {
                    series.put(runId, loaded);
                }
            } else if (csv != null) {
                InstrumentSeries parsed = dsc.domain.DscCsv.parse(runId,
                        String.valueOf(r.getOrDefault("fileName", runId + ".csv")), csv);
                series.put(runId, parsed);
                var md = parsed.metadata();
                String signRaw = md.getOrDefault("heatflow-sign", md.get("plot-convention"));
                if (signRaw != null) {
                    SignConvention parsedSign = SignConvention.fromMetadata(signRaw);
                    if (sign == SignConvention.UNKNOWN && parsedSign != SignConvention.UNKNOWN) {
                        sign = parsedSign;
                        signSourceText = signRaw;
                        signSourceEventId = (String) r.get("importedByEventId");
                    }
                }
                segments.put(runId, Segmenter.segments(runId, parsed.timeS(),
                        parsed.temperatureK()));
            }
        }
        if (sign == SignConvention.UNKNOWN) {
            Map<String, Object> d = new LinkedHashMap<>();
            d.put("code", "SIGN_UNKNOWN");
            d.put("message", "heat-flow endo/exo sign is not declared in instrument metadata; "
                    + "direction is not guessed from file names");
            bundleDiagnostics.add(d);
        }
        for (Map<String, Object> rate : state.rates.values()) {
            ratesByRun.put(String.valueOf(rate.get("runId")), rate);
        }
        for (Map<String, Object> atm : state.atmospheres.values()) {
            atmosphereByRun.put(String.valueOf(atm.get("runId")), atm);
        }
    }

    public List<Segmenter.Segment> segmentOf(String segmentId) {
        for (var e : segments.entrySet()) {
            for (Segmenter.Segment seg : e.getValue()) {
                if (seg.segmentId().equals(segmentId)) {
                    return List.of(seg);
                }
            }
        }
        return List.of();
    }

    public String runIdOfSegment(String segmentId) {
        int slash = segmentId.indexOf('/');
        return slash < 0 ? segmentId : segmentId.substring(0, slash);
    }

    public double calibrationOffsetK() {
        if (activeCalibration == null) {
            return 0.0;
        }
        Object raw = activeCalibration.get("offsetK");
        return raw instanceof Number n ? n.doubleValue() : 0.0;
    }

    public String calibrationId() {
        return activeCalibration == null ? null
                : String.valueOf(activeCalibration.get("calibrationId"));
    }

    private static int num(Object o, int dflt) {
        return o instanceof Number n ? n.intValue() : dflt;
    }
}
