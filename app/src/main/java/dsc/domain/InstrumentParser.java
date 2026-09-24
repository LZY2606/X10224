package dsc.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Line-oriented parser for the offline case-pack DSC text files:
 *
 *   # instrument: DSC-4000
 *   # segment: heat
 *   # direction: heating
 *   # sign: exo-up
 *   # temperature-unit: °C
 *   # heatflow-unit: mW
 *   # rate: 10 °C/min
 *   # atmosphere: N2, 50 mL/min
 *   30.000,0.012
 *
 * The file name is never consulted for direction/sign. Unknown units do not abort parsing.
 */
public final class InstrumentParser {
    private InstrumentParser() {}

    public static InstrumentFile parse(String fileName, String content) {
        InstrumentFile f = new InstrumentFile();
        f.fileName = fileName;
        for (String rawLine : content.split("\\r?\\n", -1)) {
            String line = rawLine.trim();
            if (line.isEmpty()) continue;
            if (line.startsWith("#")) {
                f.rawHeader.add(rawLine);
                applyHeader(f, line.substring(1).trim());
                continue;
            }
            String[] parts = line.split("[,;\\t]+");
            if (parts.length < 2) {
                f.diagnostics.add("unparsable-row:" + rawLine);
                continue;
            }
            try {
                CurvePoint p = new CurvePoint();
                p.tRaw = Double.parseDouble(parts[0].trim());
                p.hfRaw = Double.parseDouble(parts[1].trim());
                p.tRawUnit = f.tUnit;
                p.hfRawUnit = f.hfUnit;
                f.points.add(p);
            } catch (NumberFormatException e) {
                f.diagnostics.add("unparsable-row:" + rawLine);
            }
        }
        if (f.points.isEmpty()) f.diagnostics.add("no-data-points");
        return f;
    }

    private static void applyHeader(InstrumentFile f, String h) {
        int colon = h.indexOf(':');
        if (colon < 0) return;
        String key = h.substring(0, colon).trim().toLowerCase(Locale.ROOT);
        String val = colon + 1 <= h.length() ? h.substring(colon + 1).trim() : "";
        switch (key) {
            case "instrument" -> f.instrument = val;
            case "method" -> f.method = val;
            case "segment" -> f.role = parseRole(val);
            case "direction" -> f.direction = parseDirection(val);
            case "sign" -> f.signConvention = parseSign(val);
            case "temperature-unit" -> f.tUnit = val;
            case "heatflow-unit" -> f.hfUnit = val;
            case "rate" -> parseRate(f, val);
            case "atmosphere" -> parseAtmos(f, val);
            default -> { }
        }
    }

    static Enums.SegmentRole parseRole(String v) {
        v = v.toLowerCase(Locale.ROOT);
        if (v.startsWith("heat")) return Enums.SegmentRole.HEAT;
        if (v.startsWith("cool")) return Enums.SegmentRole.COOL;
        if (v.startsWith("hold") || v.startsWith("iso")) return Enums.SegmentRole.HOLD;
        return Enums.SegmentRole.UNKNOWN;
    }

    static Enums.Direction parseDirection(String v) {
        v = v.toLowerCase(Locale.ROOT);
        if (v.contains("heat")) return Enums.Direction.HEATING;
        if (v.contains("cool")) return Enums.Direction.COOLING;
        return Enums.Direction.UNKNOWN;
    }

    static Enums.SignConvention parseSign(String v) {
        v = v.toLowerCase(Locale.ROOT);
        if (v.replace(" ", "").contains("exo-up") || v.equals("exo_up")) return Enums.SignConvention.EXO_UP;
        if (v.replace(" ", "").contains("exo-down") || v.equals("exo_down")) return Enums.SignConvention.EXO_DOWN;
        return Enums.SignConvention.UNKNOWN;
    }

    private static void parseRate(InstrumentFile f, String val) {
        String[] toks = val.trim().split("\\s+");
        try {
            f.declaredRate = Double.parseDouble(toks[0]);
            f.rateUnit = toks.length > 1 ? toks[1] : null;
        } catch (RuntimeException e) {
            f.diagnostics.add("unparsable-rate:" + val);
        }
    }

    private static void parseAtmos(InstrumentFile f, String val) {
        String[] parts = val.split(",", 2);
        f.gas = parts[0].trim();
        if (parts.length > 1) {
            String rest = parts[1].trim();
            String[] toks = rest.split("\\s+");
            try {
                f.gasFlow = Double.parseDouble(toks[0]);
                f.gasFlowUnit = toks.length > 1 ? toks[1] : null;
            } catch (RuntimeException e) {
                f.gasFlowUnit = rest;
            }
        }
    }
}
