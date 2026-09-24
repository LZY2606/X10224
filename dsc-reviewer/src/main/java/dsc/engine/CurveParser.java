package dsc.engine;

import dsc.domain.Diagnostic;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Parses simple {@code temperature,heatFlow} CSV instrument output.
 *
 * <p>The parser is deliberately format-faithful: it never infers a heating or
 * cooling scan direction from the file name or ordering, it keeps non-uniform
 * temperature spacing (reporting it), and leaves unconvertible rows in place
 * with diagnostics instead of filtering them out for plotting.
 */
public final class CurveParser {

    private CurveParser() {
    }

    public static ParsedSource parse(String csv) {
        String[] lines = csv.replace("\r", "").split("\n", -1);
        List<Pt> points = new ArrayList<>();
        List<String[]> rawRows = new ArrayList<>();
        List<Diagnostic> diagnostics = new ArrayList<>();

        int start = 0;
        if (lines.length > 0 && looksLikeHeader(lines[0])) {
            start = 1;
        }

        for (int i = start; i < lines.length; i++) {
            String line = lines[i];
            if (line.isBlank()) {
                continue;
            }
            String[] cells = line.split(",", -1);
            rawRows.add(cells);
            if (cells.length < 2) {
                diagnostics.add(Diagnostic.of("PARSE_COLUMNS", Diagnostic.ERROR,
                        "row has fewer than 2 columns", "line:" + (i + 1),
                        Map.of("line", i + 1, "raw", line)));
                continue;
            }
            Double t = tryParse(cells[0]);
            Double hf = tryParse(cells[1]);
            if (t == null || hf == null) {
                diagnostics.add(Diagnostic.of("PARSE_NUMBER", Diagnostic.WARN,
                        "unconvertible value kept in place", "line:" + (i + 1),
                        Map.of("line", i + 1, "temperatureRaw", cells[0].trim(),
                                "heatFlowRaw", cells[1].trim())));
                continue;
            }
            points.add(new Pt(t, hf));
        }

        boolean nonUniform = detectNonUniform(points, diagnostics);
        return new ParsedSource(csv, List.copyOf(points), List.copyOf(rawRows),
                List.copyOf(diagnostics), nonUniform);
    }

    private static boolean looksLikeHeader(String line) {
        String[] cells = line.split(",", -1);
        if (cells.length < 2) {
            return false;
        }
        return tryParse(cells[0]) == null || tryParse(cells[1]) == null;
    }

    private static Double tryParse(String s) {
        try {
            double v = Double.parseDouble(s.trim());
            return Double.isFinite(v) ? v : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Reports (but does not resample) non-equal temperature spacing. */
    static boolean detectNonUniform(List<Pt> points, List<Diagnostic> sink) {
        if (points.size() < 3) {
            return false;
        }
        double firstStep = points.get(1).t() - points.get(0).t();
        double sign = Math.signum(firstStep);
        double tolerance = Math.max(1e-9, Math.abs(firstStep) * 1e-3);
        for (int i = 1; i < points.size(); i++) {
            double step = points.get(i).t() - points.get(i - 1).t();
            if (sign != 0 && Math.signum(step) != sign) {
                sink.add(Diagnostic.of("TEMP_NONMONOTONIC", Diagnostic.WARN,
                        "temperature is not monotonic", "index:" + i,
                        Map.of("index", i, "previousT", points.get(i - 1).t(),
                                "t", points.get(i).t())));
            }
            if (Math.abs(Math.abs(step) - Math.abs(firstStep)) > tolerance) {
                sink.add(Diagnostic.of("TEMP_NONUNIFORM", Diagnostic.INFO,
                        "non-equal temperature spacing retained", "index:" + i,
                        Map.of("index", i, "stepCelsius", step)));
                return true;
            }
        }
        return false;
    }
}
