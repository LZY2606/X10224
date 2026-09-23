package org.matlab.dsc.rules;

import java.util.ArrayList;
import java.util.List;
import org.matlab.dsc.domain.Diagnostic;
import org.matlab.dsc.domain.RawPoint;
import org.matlab.dsc.domain.SourceRecord;
import org.matlab.dsc.ingest.RawValue;
import org.matlab.dsc.unit.Unit;

/**
 * Normalises raw instrument rows using a temperature calibration version.
 * Output frame is kelvin / milliwatt. Unconvertible rows stay flagged at
 * their original index: they are kept on the source and on the diagnostics
 * list but excluded from the derived series.
 */
public final class CalibrationRules {

    public static final String TEMP_FRAME = "K";
    public static final String HF_FRAME = "mW";

    public RuleResult calibrate(SourceRecord instrument, SourceRecord calibration) {
        RuleResult rr = new RuleResult();
        double slope = d(calibration.payload(), "slope", 1.0);
        double offsetK = d(calibration.payload(), "offsetK", 0.0);
        String calRawUnit = String.valueOf(calibration.payload().getOrDefault("rawUnit", "K"));

        List<Double> t = new ArrayList<>();
        List<Double> y = new ArrayList<>();
        List<Integer> idx = new ArrayList<>();

        for (RawPoint p : instrument.rawPoints()) {
            RawValue tv = RawValue.parse(p.rawTemperature());
            RawValue hv = RawValue.parse(p.rawHeatFlow());
            boolean bad = !p.usable();
            if (tv.error() != null || hv.error() != null) {
                bad = true;
            }
            if (bad) {
                String reason = !p.flags().isEmpty() ? String.join(",", p.flags())
                        : (tv.error() != null ? tv.error() : hv.error());
                rr.diagnostic(new Diagnostic("diag:cal:" + instrument.id() + ":" + p.index(),
                        instrument.id(), "row[" + p.index() + "]",
                        "UNCONVERTIBLE_VALUE_KEPT",
                        tv.raw() + " / " + hv.raw(), reason));
                continue;
            }
            double tk = tv.quantity().valueIn(Unit.K) * slope + offsetK;
            double mw = hv.quantity().valueIn(Unit.MILLIWATT);
            t.add(tk);
            y.add(mw);
            idx.add(p.index());
        }
        rr.payload(java.util.Map.of(
                "t", t, "hf", y, "rawIndex", idx,
                "temperatureUnit", TEMP_FRAME,
                "heatFlowUnit", HF_FRAME,
                "calibrationId", calibration.id(),
                "calibrationRawUnit", calRawUnit,
                "instrumentHeader", new java.util.LinkedHashMap<>(instrument.rawHeader()),
                "usableRows", idx.size(),
                "totalRows", instrument.rawPoints().size()));
        return rr;
    }

    private static double d(java.util.Map<String, Object> m, String k, double fallback) {
        Object v = m.get(k);
        return v instanceof Number n ? n.doubleValue() : fallback;
    }
}
