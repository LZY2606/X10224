package dsc.rules;

import dsc.model.CurvePoint;
import dsc.model.ParsedCurve;
import dsc.model.Q;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Parser for the offline DSC instrument text format.
 *
 * <p>The endo-/exothermic direction is read only from the declared
 * {@code instrument.signConvention} header. The file name is never consulted, and a
 * missing convention produces a diagnostic rather than a guessed sign.
 */
public final class InstrumentParser {

    public static final String RULE_VERSION = "instrument-parse/v3";

    private InstrumentParser() {
    }

    public record Header(Map<String, String> fields, String signConvention,
                         String declaredScanDirection, String temperatureUnit,
                         String heatFlowUnit, List<String> diagnostics) {
    }

    public static Header parseHeader(String payload) {
        Map<String, String> fields = new LinkedHashMap<>();
        List<String> diagnostics = new ArrayList<>();
        for (String rawLine : payload.split("\\r?\\n", -1)) {
            String line = rawLine.strip();
            if (line.isEmpty() || line.startsWith("#") || line.startsWith("[")) {
                continue;
            }
            if (line.regionMatches(true, 0, "index,", 0, 6) || line.startsWith("time")) {
                break;
            }
            int colon = line.indexOf(':');
            if (colon <= 0) {
                continue;
            }
            fields.put(line.substring(0, colon).strip(), line.substring(colon + 1).strip());
        }
        String sign = fields.getOrDefault("instrument.signConvention", "").trim()
                .toUpperCase(Locale.ROOT);
        String normalizedSign;
        if (sign.equals("EXO_UP") || sign.equals("EXOTHERM_UP") || sign.equals("EXO+UP")) {
            normalizedSign = "EXO_UP";
        } else if (sign.equals("EXO_DOWN") || sign.equals("EXOTHERM_DOWN") || sign.equals("EXO+DOWN")) {
            normalizedSign = "EXO_DOWN";
        } else {
            normalizedSign = "ABSENT";
            diagnostics.add("HEAT_FLOW_SIGN_NOT_DECLARED");
        }
        String direction = fields.getOrDefault("instrument.scanDirection", "").trim()
                .toUpperCase(Locale.ROOT);
        if (!direction.equals("HEATING") && !direction.equals("COOLING")) {
            direction = "ABSENT";
        }
        String tempUnit = fields.getOrDefault("instrument.temperatureUnit", "°C").trim();
        String hfUnit = fields.getOrDefault("instrument.heatFlowUnit", "mW").trim();
        return new Header(fields, normalizedSign, direction, tempUnit, hfUnit, diagnostics);
    }

    public static ParsedCurve parse(String payload) {
        Header header = parseHeader(payload);
        List<CurvePoint> points = new ArrayList<>();
        List<String> diagnostics = new ArrayList<>(header.diagnostics());
        String[] lines = payload.split("\\r?\\n", -1);
        boolean inData = false;
        int index = 0;
        for (String rawLine : lines) {
            String line = rawLine.strip();
            if (line.startsWith("[")) {
                inData = line.equalsIgnoreCase("[points]");
                continue;
            }
            if (!inData) {
                continue;
            }
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            if (line.regionMatches(true, 0, "index,", 0, 6)) {
                continue;
            }
            String[] cells = line.split(",", -1);
            if (cells.length < 4) {
                diagnostics.add("MALFORMED_ROW:" + index + ":" + line);
                index++;
                continue;
            }
            Q time = Q.parse(cells[1].trim(), Q.TIME_S, Q.TIME_S);
            Q temp = Q.parse(cells[2].trim(), header.temperatureUnit(), Q.TEMP_C);
            Q hf = Q.parse(cells[3].trim(), header.heatFlowUnit(), Q.HEAT_FLOW_MW);
            if (!temp.valid() || !hf.valid()) {
                diagnostics.add("UNCONVERTIBLE_SAMPLE_KEPT:" + index);
            }
            points.add(new CurvePoint(index, temp, hf, time, rawLine));
            index++;
        }
        if (points.isEmpty()) {
            diagnostics.add("EMPTY_CURVE");
        }
        return new ParsedCurve(points, diagnostics, header.signConvention(),
                header.declaredScanDirection(), header.temperatureUnit(), header.heatFlowUnit());
    }
}
