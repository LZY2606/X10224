package dsc.app;

import dsc.domain.EnthalpyErrorSources;
import dsc.domain.Event;
import dsc.domain.EventType;
import dsc.domain.ThermalSign;
import dsc.domain.TransitionResult;
import dsc.domain.TransitionType;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Flattens a transition result into the string payload map carried by an event. */
public final class TransitionCodec {

    private TransitionCodec() {
    }

    public static Map<String, String> encode(TransitionResult tr) {
        Map<String, String> p = new LinkedHashMap<>();
        p.put("transition_id", tr.id());
        p.put("interval_id", tr.intervalId());
        p.put("segment_id", tr.segmentId());
        p.put("baseline_candidate_id", tr.baselineCandidateId());
        p.put("transition_type", tr.type().code());
        putD(p, "onset_k", tr.onsetK());
        putD(p, "peak_k", tr.peakK());
        putD(p, "endset_k", tr.endsetK());
        putD(p, "peak_flow_w", tr.peakFlowW());
        putD(p, "step_height_w", tr.stepHeightWK());
        putD(p, "midpoint_k", tr.midpointK());
        putD(p, "specific_enthalpy_jg", tr.specificEnthalpyJG());
        p.put("sign", tr.sign().code());
        EnthalpyErrorSources es = tr.errorSources();
        if (es != null) {
            p.put("err_mass_rel", String.valueOf(es.massRelativeFraction()));
            p.put("err_rate_rel", String.valueOf(es.rateRelativeFraction()));
            p.put("err_calib_k", String.valueOf(es.calibrationUncertaintyK()));
            p.put("err_baseline_spread_jg", String.valueOf(es.baselineSpreadJG()));
            p.put("err_sampling_jg", String.valueOf(es.samplingUncertaintyJG()));
            p.put("err_notes", String.join(";;", es.notes() == null ? List.of() : es.notes()));
        }
        p.put("diagnostics", String.join(";;", tr.diagnostics() == null ? List.of() : tr.diagnostics()));
        p.put("mass_version", String.valueOf(tr.massVersion()));
        p.put("rate_version", String.valueOf(tr.rateVersion()));
        p.put("calibration_version", String.valueOf(tr.calibrationVersion()));
        p.put("raw_batch_version", String.valueOf(tr.rawBatchVersion()));
        p.put("tangent_version_set", String.valueOf(tr.tangentVersionSet()));
        p.put("data_hash", tr.dataHash() == null ? "" : tr.dataHash());
        return p;
    }

    public static TransitionResult decode(Event e) {
        EnthalpyErrorSources es = new EnthalpyErrorSources(
                getD(e, "err_mass_rel", 0), getD(e, "err_rate_rel", 0),
                getD(e, "err_calib_k", 0), getD(e, "err_baseline_spread_jg", 0),
                getD(e, "err_sampling_jg", 0),
                split(e.payload("err_notes")));
        String diag = e.payload("diagnostics");
        return new TransitionResult(
                e.payload("transition_id"), e.payload("interval_id"),
                e.payload("segment_id"), e.payload("baseline_candidate_id"),
                TransitionType.fromCode(e.payload("transition_type")),
                getDBoxed(e, "onset_k"), getDBoxed(e, "peak_k"), getDBoxed(e, "endset_k"),
                getDBoxed(e, "peak_flow_w"), getDBoxed(e, "step_height_w"),
                getDBoxed(e, "midpoint_k"), getDBoxed(e, "specific_enthalpy_jg"),
                ThermalSign.valueOf(safeSign(e.payload("sign"))),
                es,
                split(diag),
                Integer.parseInt(e.payload("mass_version")),
                Integer.parseInt(e.payload("rate_version")),
                Integer.parseInt(e.payload("calibration_version")),
                Integer.parseInt(e.payload("raw_batch_version")),
                Integer.parseInt(String.valueOf(e.payload("tangent_version_set"))),
                e.payload("data_hash"));
    }

    private static String safeSign(String s) {
        if (s == null) return "UNDETERMINED";
        return s.toUpperCase();
    }

    private static List<String> split(String s) {
        if (s == null || s.isEmpty()) return List.of();
        return Arrays.asList(s.split(";;", -1));
    }

    private static void putD(Map<String, String> p, String k, Double v) {
        p.put(k, v == null ? "" : String.valueOf(v));
    }

    private static Double getDBoxed(Event e, String k) {
        String s = e.payload(k);
        return s == null || s.isEmpty() ? null : Double.parseDouble(s);
    }

    private static double getD(Event e, String k, double dflt) {
        String s = e.payload(k);
        return s == null || s.isEmpty() ? dflt : Double.parseDouble(s);
    }
}
