package io.dsc.engine;

import io.dsc.model.Quantity;
import io.dsc.model.ScanDirection;
import io.dsc.model.SignConvention;
import io.dsc.model.Unit;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Parses the domain-specific instrument CSV used by the offline case pack.
 *
 * Header lines are `# key: value` and carry segment id, cycle, direction and
 * the instrument sign convention. Data columns are
 *     time_s, temp, heatflow
 * with per-column unit headers. Unparseable/unconvertible cells are kept at
 * their original position and flagged; nothing is dropped to make a plot work.
 */
public final class InstrumentCsvParser {

    public ParsedInstrument parse(String text, String fileNameHint) {
        Map<String, String> headers = new LinkedHashMap<>();
        List<String[]> dataRows = new ArrayList<>();
        List<String> rawLines = new ArrayList<>();
        for (String rawLine : text.split("\\R", -1)) {
            String line = rawLine.trim();
            if (line.isEmpty()) continue;
            if (line.startsWith("#")) {
                int colon = line.indexOf(':');
                if (colon > 1) {
                    String key = line.substring(1, colon).trim().toLowerCase(Locale.ROOT);
                    String value = line.substring(colon + 1).trim();
                    headers.merge(key, value, (a, b) -> a + "; " + b);
                }
                continue;
            }
            rawLines.add(rawLine);
            dataRows.add(splitCsv(line));
        }
        if (dataRows.size() < 2) {
            throw new IllegalArgumentException("instrument output needs a header row plus data");
        }

        String[] columnHeader = dataRows.get(0);
        String[] units = resolveUnits(columnHeader);
        String directionRaw = headers.getOrDefault("direction", "");
        ScanDirection direction = ScanDirection.fromCode(directionRaw);
        String signRaw = headers.get("sign-convention");
        SignConvention sign = SignConvention.parse(signRaw);

        List<String> headerDiagnostics = new ArrayList<>();
        if (fileNameHint != null) {
            String lower = fileNameHint.toLowerCase(Locale.ROOT);
            boolean nameSaysHeat = lower.contains("heat") && !lower.contains("cool");
            boolean nameSaysCool = lower.contains("cool");
            if ((nameSaysHeat && direction == ScanDirection.COOLING)
                    || (nameSaysCool && direction == ScanDirection.HEATING)) {
                headerDiagnostics.add("filename-direction-mismatch");
            }
        }

        List<ParsedInstrument.ParsedRow> rows = new ArrayList<>();
        for (int i = 1; i < dataRows.size(); i++) {
            String[] cells = dataRows.get(i);
            String rawTime = cell(cells, 0);
            String rawTemp = cell(cells, 1);
            String rawHf = cell(cells, 2);

            ParsedQuantity pTime = parseQuantity(rawTime, units[0]);
            ParsedQuantity pTemp = parseQuantity(rawTemp, units[1]);
            ParsedQuantity pHf = parseQuantity(rawHf, units[2]);

            rows.add(new ParsedInstrument.ParsedRow(
                    i - 1, rawLines.get(i), rawTime, rawTemp, rawHf,
                    pTime.quantity, pTemp.quantity, pHf.quantity,
                    pTemp.unconvertible, pHf.unconvertible));
        }

        return new ParsedInstrument(
                headers.getOrDefault("segment-id", "seg-" + System.identityHashCode(text)),
                Integer.parseInt(headers.getOrDefault("cycle", "1")),
                direction, directionRaw, sign, signRaw, fileNameHint,
                headerDiagnostics, rows);
    }

    private String[] resolveUnits(String[] columnHeader) {
        String[] out = new String[3];
        for (int i = 0; i < 3 && i < columnHeader.length; i++) {
            String h = columnHeader[i].trim();
            int open = h.indexOf('[');
            int close = h.lastIndexOf(']');
            out[i] = (open >= 0 && close > open) ? h.substring(open + 1, close).trim() : null;
        }
        if (out[0] == null) out[0] = "s";
        if (out[1] == null) out[1] = "°C";
        if (out[2] == null) out[2] = "mW";
        return out;
    }

    private record ParsedQuantity(Quantity quantity, boolean unconvertible) {}

    private ParsedQuantity parseQuantity(String raw, String declaredUnit) {
        if (raw == null || raw.isBlank()) {
            return new ParsedQuantity(null, true);
        }
        String token = raw.trim();
        int split = numericPrefixLength(token);
        String numberPart = token.substring(0, split).trim();
        String unitPart = split < token.length() ? token.substring(split).trim() : declaredUnit;
        double numeric;
        try {
            numeric = Double.parseDouble(numberPart);
        } catch (NumberFormatException e) {
            return new ParsedQuantity(null, true);
        }
        Unit unit = unitPart == null ? null : Unit.fromSymbol(unitPart).orElse(null);
        if (unit == null) {
            // value preserved verbatim at origin; no normalised representation
            return new ParsedQuantity(null, true);
        }
        return new ParsedQuantity(Quantity.parsed(numeric, unit, token), false);
    }

    private int numericPrefixLength(String token) {
        int i = 0;
        boolean seenDigit = false;
        while (i < token.length()) {
            char c = token.charAt(i);
            if (Character.isDigit(c) || c == '.' || c == '-' || c == '+'
                    || (seenDigit && (c == 'e' || c == 'E'))
                    || ((c == 'e' || c == 'E') && i + 1 < token.length)) {
                if (Character.isDigit(c)) seenDigit = true;
                i++;
            } else {
                break;
            }
        }
        return i;
    }

    private static String[] splitCsv(String line) {
        List<String> cells = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') { inQuotes = !inQuotes; continue; }
            if (c == ',' && !inQuotes) { cells.add(cur.toString()); cur.setLength(0); continue; }
            cur.append(c);
        }
        cells.add(cur.toString());
        return cells.toArray(String[]::new);
    }

    private static String cell(String[] cells, int i) {
        return i < cells.length ? cells[i].trim() : "";
    }
}
