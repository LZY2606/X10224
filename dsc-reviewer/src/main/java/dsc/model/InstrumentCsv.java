package dsc.model;

import dsc.qty.Qty;
import dsc.qty.Unit;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Parser for the instrument export CSV used by the offline case package.
 *
 * Header example:
 *   time_s,temperature_K,heatflow_mW
 * The header declares axis units explicitly; unparseable numeric cells are kept
 * on their row as raw quantities with a diagnostic, rather than dropping rows.
 */
public final class InstrumentCsv {

    public record ParseResult(List<Reading> rows, List<String> headers, List<Diagnostic> diagnostics) {}

    private InstrumentCsv() {}

    public static ParseResult parse(String content, String fileName) {
        String[] lines = content.split("\\r?\\n");
        List<Reading> rows = new ArrayList<>();
        List<Diagnostic> diagnostics = new ArrayList<>();
        if (lines.length == 0) {
            return new ParseResult(rows, List.of(), diagnostics);
        }
        List<String> headers = splitCsv(lines[0]);
        String[] units = new String[headers.size()];
        for (int i = 0; i < headers.size(); i++) {
            units[i] = unitFromHeader(headers.get(i));
            if (!Unit.known(units[i])) {
                diagnostics.add(new Diagnostic(Diagnostic.UNKNOWN_UNIT, "error",
                        "column " + headers.get(i) + " declares unknown unit " + units[i],
                        fileName, fileName));
            }
        }
        int timeCol = findAxis(headers, "time");
        int tempCol = findAxis(headers, "temperature");
        int hfCol = findAxis(headers, "heatflow");
        for (int li = 1; li < lines.length; li++) {
            String line = lines[li];
            if (line.isBlank()) {
                continue;
            }
            List<String> cells = splitCsv(line);
            Qty t = cell(cells, timeCol, units, li + 1, "time", fileName, diagnostics);
            Qty temp = cell(cells, tempCol, units, li + 1, "temperature", fileName, diagnostics);
            Qty hf = cell(cells, hfCol, units, li + 1, "heatflow", fileName, diagnostics);
            rows.add(new Reading(t, temp, hf, fileName, li + 1));
        }
        return new ParseResult(rows, headers, diagnostics);
    }

    private static Qty cell(List<String> cells, int col, String[] units, int line,
                            String axis, String file, List<Diagnostic> out) {
        String claimed = col >= 0 && col < units.length ? units[col] : "";
        String raw = col >= 0 && col < cells.size() ? cells.get(col).trim() : "";
        if (raw.isEmpty()) {
            out.add(new Diagnostic(Diagnostic.UNPARSEABLE_CELL, "error",
                    axis + " missing at line " + line, file + ":" + line, file));
            return Qty.unparseable(raw, claimed);
        }
        try {
            double v = Double.parseDouble(raw);
            return Qty.parsed(v, claimed, raw, null);
        } catch (NumberFormatException e) {
            out.add(new Diagnostic(Diagnostic.UNPARSEABLE_CELL, "error",
                    axis + " value '" + raw + "' not numeric at line " + line,
                    file + ":" + line, file));
            return Qty.unparseable(raw, claimed);
        }
    }

    private static int findAxis(List<String> headers, String axis) {
        for (int i = 0; i < headers.size(); i++) {
            if (headers.get(i).toLowerCase(Locale.ROOT).startsWith(axis)) {
                return i;
            }
        }
        return -1;
    }

    private static String unitFromHeader(String header) {
        int underscore = header.lastIndexOf('_');
        if (underscore < 0) {
            return "";
        }
        return header.substring(underscore + 1).trim();
    }

    /** Minimal CSV split: case fixtures do not use quoted commas, but support quoted fields anyway. */
    static List<String> splitCsv(String line) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                quoted = !quoted;
            } else if (c == ',' && !quoted) {
                out.add(cur.toString());
                cur.setLength(0);
            } else {
                cur.append(c);
            }
        }
        out.add(cur.toString());
        return out;
    }
}
