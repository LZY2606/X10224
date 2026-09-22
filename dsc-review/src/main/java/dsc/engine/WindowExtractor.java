package dsc.engine;

import dsc.domain.InstrumentSeries;
import dsc.domain.Segmenter;
import dsc.domain.Units;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Extracts usable points for a window on one segment. Rules enforced here:
 * <ul>
 *   <li>heating and cooling segments never mix: a window lives on exactly one
 *       segment; spanning a cycle boundary is rejected with a diagnostic;</li>
 *   <li>non-uniform temperature spacing is kept (spacing array drives error
 *       propagation and trapezoid integration);</li>
 *   <li>unparseable cells are skipped, not dropped silently; a window with no
 *       usable interior becomes EMPTY;</li>
 *   <li>the active temperature calibration offset is applied at this layer, so
 *       the raw series stays untouched.</li>
 * </ul>
 */
final class WindowExtractor {

    private WindowExtractor() {
    }

    static WindowData extract(InputBundle bundle, String windowId, String windowVersionId,
                              String segmentId, Double tStartK, Double tEndK) {
        List<Map<String, Object>> diagnostics = new ArrayList<>();
        List<Segmenter.Segment> hit = bundle.segmentOf(segmentId);
        if (hit.isEmpty()) {
            diagnostics.add(diag("SEGMENT_MISSING",
                    "window references unknown segment " + segmentId));
            return new WindowData(windowId, windowVersionId, segmentId, null,
                    new int[0], new double[0], new double[0], new double[0], new double[0],
                    bundle.sign.code().equals("exo_up"),
                    bundle.sign == dsc.domain.SignConvention.UNKNOWN,
                    true, diagnostics);
        }
        Segmenter.Segment seg = hit.get(0);
        String runId = seg.runId();
        InstrumentSeries series = bundle.series.get(runId);
        double offsetK = bundle.calibrationOffsetK();

        if (tStartK == null || tEndK == null) {
            diagnostics.add(diag("WINDOW_UNPARSEABLE",
                    "window endpoint cannot be converted to kelvin; raw endpoints retained"));
            return new WindowData(windowId, windowVersionId, segmentId, seg.direction(),
                    new int[0], new double[0], new double[0], new double[0], new double[0],
                    bundle.sign.code().equals("exo_up"),
                    bundle.sign == dsc.domain.SignConvention.UNKNOWN,
                    true, diagnostics);
        }
        double lo = Math.min(tStartK, tEndK);
        double hi = Math.max(tStartK, tEndK);

        List<Integer> rows = new ArrayList<>();
        List<Double> temps = new ArrayList<>();
        List<Double> times = new ArrayList<>();
        List<Double> flows = new ArrayList<>();
        List<Double> spacings = new ArrayList<>();
        int skipped = 0;
        double prevCal = Double.NaN;
        for (int r = seg.fromRow(); r <= seg.toRow(); r++) {
            Double tRaw = series.temperatureK()[r];
            Double fRaw = series.heatFlowW()[r];
            if (tRaw == null || fRaw == null) {
                skipped++;
                diagnostics.add(diagRow(r, "POINT_UNUSABLE",
                        "missing or unparseable temperature/heat flow at row " + r
                                + "; point retained in source, skipped in derivation"));
                continue;
            }
            double tCal = tRaw + offsetK;
            if (tCal < lo || tCal > hi) {
                continue;
            }
            rows.add(r);
            temps.add(tCal);
            times.add(series.timeS()[r]);
            // Sign normalization: convert instrument numbers so endothermic
            // deviation is positive residual. Convention comes from metadata.
            double endoUpFlow = switch (bundle.sign) {
                case ENDO_UP -> fRaw;
                case EXO_UP -> -fRaw;
                default -> fRaw;
            };
            flows.add(endoUpFlow);
            spacings.add(Double.isNaN(prevCal) ? 0.0 : Math.abs(tCal - prevCal));
            prevCal = tCal;
        }
        if (skipped > 0) {
            diagnostics.add(diag("POINTS_SKIPPED", skipped + " point(s) unusable in window"));
        }
        boolean nonUniform = false;
        double[] sp = new double[spacings.size()];
        for (int i = 0; i < sp.length; i++) {
            sp[i] = spacings.get(i);
            if (i > 1 && sp.length > 3) {
                double ref = sp[1];
                if (ref > 1e-12 && Math.abs(sp[i] - ref) / ref > 0.05) {
                    nonUniform = true;
                }
            }
        }
        if (nonUniform) {
            diagnostics.add(diag("NON_UNIFORM_GRID",
                    "temperature spacing varies; trapezoid integration on actual grid"));
        }
        boolean empty = rows.size() < 3;
        if (empty) {
            diagnostics.add(diag("EMPTY_WINDOW",
                    "window contains fewer than 3 usable points; no baseline/integral is computed"));
        }
        if (bundle.sign == dsc.domain.SignConvention.UNKNOWN) {
            diagnostics.add(diag("SIGN_UNKNOWN",
                    "endo/exo sign unknown: onset/peak/endset positions are computed, "
                            + "but enthalpy endo/exo classification is not assigned"));
        }
        return new WindowData(windowId, windowVersionId, segmentId, seg.direction(),
                rows.stream().mapToInt(Integer::intValue).toArray(),
                temps.stream().mapToDouble(Double::doubleValue).toArray(),
                times.stream().mapToDouble(Double::doubleValue).toArray(),
                flows.stream().mapToDouble(Double::doubleValue).toArray(),
                sp, bundle.sign.code().equals("exo_up"),
                bundle.sign == dsc.domain.SignConvention.UNKNOWN,
                empty, diagnostics);
    }

    static Map<String, Object> diag(String code, String message) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("code", code);
        m.put("message", message);
        return m;
    }

    static Map<String, Object> diagRow(int row, String code, String message) {
        Map<String, Object> m = diag(code, message);
        m.put("row", row);
        return m;
    }

    static double parseK(String raw) {
        return Units.parseTemperature(raw).si();
    }
}
