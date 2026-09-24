package org.materials.dsc.domain;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Integration of (signal - baseline) over the event window.  Enthalpy is
 * integrated in the time domain whenever times exist, else temperature domain
 * using the scan rate.  Mass-normalized specific enthalpy is a separate,
 * mass-version-dependent value that goes stale on mass revision.
 */
public final class IntegrationResult {
    public String id;
    public String baselineId;
    public String eventId;
    public double areaJ;
    /** J/g, or NaN when mass is missing/unconvertible */
    public double specificEnthalpyJg = Double.NaN;
    public int massRevisionUsed = -1;
    public double onsetC = Double.NaN;
    public double peakC = Double.NaN;
    public double endsetC = Double.NaN;
    public double peakHeightW = Double.NaN;
    /** endothermic / exothermic under the resolved endo-up convention */
    public String thermalSense = "unknown";
    public double windowStartC;
    public double windowEndC;
    public String integrationDomain; // "time" | "temperature"
    public String status = "ok";
    public String diagnostics = "";
    public int sampleCount;

    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("baselineId", baselineId);
        m.put("eventId", eventId);
        m.put("areaJ", areaJ);
        m.put("specificEnthalpyJg", Double.isNaN(specificEnthalpyJg) ? null : specificEnthalpyJg);
        m.put("massRevisionUsed", massRevisionUsed);
        m.put("onsetC", Double.isNaN(onsetC) ? null : onsetC);
        m.put("peakC", Double.isNaN(peakC) ? null : peakC);
        m.put("endsetC", Double.isNaN(endsetC) ? null : endsetC);
        m.put("peakHeightW", Double.isNaN(peakHeightW) ? null : peakHeightW);
        m.put("thermalSense", thermalSense);
        m.put("windowStartC", windowStartC);
        m.put("windowEndC", windowEndC);
        m.put("integrationDomain", integrationDomain);
        m.put("status", status);
        m.put("diagnostics", diagnostics);
        m.put("sampleCount", sampleCount);
        m.put("units", unitsMap());
        return m;
    }

    public static Map<String, String> unitsMap() {
        Map<String, String> u = new LinkedHashMap<>();
        u.put("areaJ", "J");
        u.put("specificEnthalpyJg", "J/g");
        u.put("onsetC", "°C");
        u.put("peakC", "°C");
        u.put("endsetC", "°C");
        u.put("peakHeightW", "W");
        u.put("windowStartC", "°C");
        u.put("windowEndC", "°C");
        return m(u);
    }

    private static Map<String, String> m(Map<String, String> u) {
        return u;
    }
}
