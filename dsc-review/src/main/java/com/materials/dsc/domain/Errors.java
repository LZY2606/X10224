package com.materials.dsc.domain;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.materials.dsc.model.BaselineCandidate;
import com.materials.dsc.model.Qty;
import com.materials.dsc.util.Json;

/**
 * Builds the ordered list of error sources shown next to every enthalpy
 * value.  Each entry is a machine-readable contribution, not prose.
 */
public final class Errors {
    private Errors() {
    }

    public static void add(ArrayNode list, String code, String sourceId, int sourceVersion,
                           double contributionJPerG, String basis) {
        ObjectNode o = Json.obj();
        o.put("code", code);
        o.put("sourceId", sourceId == null ? "" : sourceId);
        o.put("sourceVersion", sourceVersion);
        o.put("contributionJPerG", Math.round(contributionJPerG * 1e6) / 1e6);
        o.put("basis", basis);
        list.add(o);
    }

    public static ArrayNode forTransition(BaselineCandidate chosen, double enthalpyJPerG,
                                          Qty mass, Qty rate, double calOffsetC,
                                          String calSourceId, int calVersion,
                                          boolean timeIntegrated, boolean overlap,
                                          boolean nonUniform, int skippedPoints) {
        ArrayNode list = Json.arr();
        double baselineShare = 0.10;
        add(list, "BASELINE_MODEL", chosen.id, chosen.nodeVersion,
                Math.abs(enthalpyJPerG) * baselineShare,
                "baseline choice '" + chosen.method + "' carries ~10% of integrated area uncertainty");
        add(list, "TEMPERATURE_CALIBRATION", calSourceId, calVersion,
                Math.abs(enthalpyJPerG) * 0.01,
                "calibration offset " + calOffsetC + " degC shifts integration limits");
        if (mass.usable()) {
            add(list, "SAMPLE_MASS", "", 0, 0.0,
                    "normalised enthalpy divides by mass " + mass.value + " " + mass.unit
                            + " (raw: '" + mass.raw + "')");
        } else {
            add(list, "SAMPLE_MASS", "", 0, 0.0,
                    "mass unusable (" + mass.diagnostic + "); normalised enthalpy withheld");
        }
        if (rate.usable()) {
            add(list, "SCAN_RATE", "", 0, 0.0,
                    "scan rate " + rate.value + " K/min (raw: '" + rate.raw + "') used for time axis");
        } else {
            add(list, "SCAN_RATE", "", 0, 0.0,
                    "scan rate unusable (" + rate.diagnostic + ")");
        }
        add(list, timeIntegrated ? "TIME_AXIS_MEASURED" : "TIME_AXIS_FROM_RATE", "", 0, 0.0,
                timeIntegrated ? "integrated over the instrument time channel"
                        : "time reconstructed from temperature and scan rate");
        if (overlap) {
            add(list, "OVERLAPPING_TRANSITION", chosen.analysisId, 0,
                    Math.abs(enthalpyJPerG) * 0.15,
                    "overlapping event; shared area split, uncertainty raised");
        }
        if (nonUniform) {
            add(list, "NON_UNIFORM_SAMPLING", "", 0, Math.abs(enthalpyJPerG) * 0.03,
                    "temperature spacing is non-uniform; trapezoid rule on actual coordinates");
        }
        if (skippedPoints > 0) {
            add(list, "MISSING_HEATFLOW", "", 0, 0.0,
                    skippedPoints + " sample(s) without parseable heat flow were kept in place");
        }
        return list;
    }
}
