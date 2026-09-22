package dsc.engine;

import dsc.Csv;
import dsc.Ids;
import dsc.model.RawSeries;
import dsc.model.SourceBlob;
import dsc.units.Quantity;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class RawSeriesParser {

    public static class ParseResult {
        public RawSeries series;
        public final List<String> diagnostics = new ArrayList<>();
    }

    public ParseResult parse(String segmentId, SourceBlob blob) {
        ParseResult result = new ParseResult();
        List<List<String>> rows = Csv.parse(blob.rawText());
        if (rows.isEmpty()) {
            result.diagnostics.add("empty-csv");
            return result;
        }
        List<String> headers = rows.get(0);
        int tempCol = findColumn(headers, "temperature", "temp", "t(", "t (", "tset");
        int hfCol = findColumn(headers, "heat flow", "heatflow", "hf", "dsc", "heat_flow");
        int timeCol = findColumn(headers, "time", "t/s", "elapsed");
        if (tempCol < 0 || hfCol < 0) {
            result.diagnostics.add("missing-required-column");
            return result;
        }
        String tempUnit = unitFromHeader(headers.get(tempCol), blob.metadata().get("temperatureUnit"));
        String hfUnit = unitFromHeader(headers.get(hfCol), blob.metadata().get("heatFlowUnit"));
        String timeUnit = timeCol >= 0 ? unitFromHeader(headers.get(timeCol), blob.metadata().get("timeUnit")) : "s";

        List<Double> temps = new ArrayList<>();
        List<Double> hfs = new ArrayList<>();
        List<Double> times = new ArrayList<>();
        List<Integer> badRows = new ArrayList<>();

        for (int r = 1; r < rows.size(); r++) {
            List<String> row = rows.get(r);
            if (row.isEmpty()) {
                continue;
            }
            Quantity tq = UnitsParse(row, tempCol, tempUnit, Quantity.TEMPERATURE);
            Quantity hq = UnitsParse(row, hfCol, hfUnit, Quantity.HEAT_FLOW);
            if (!tq.valid() || !hq.valid()) {
                badRows.add(r);
                result.diagnostics.add("row-" + r + ":unconvertible:"
                        + (tq.valid() ? "" : tq.diagnostic())
                        + (hq.valid() ? "" : "/" + hq.diagnostic()));
                temps.add(Double.NaN);
                hfs.add(Double.NaN);
            } else {
                temps.add(tq.si());
                hfs.add(hq.si());
            }
            if (timeCol >= 0 && timeCol < row.size()) {
                Quantity t = UnitsParse(row, timeCol, timeUnit, Quantity.TIME);
                times.add(t.valid() ? t.si() : Double.NaN);
            } else {
                times.add(Double.NaN);
            }
        }

        String convention = blob.metadata().getOrDefault("signConvention", RawSeries.ENDO_UP);
        if (!RawSeries.ENDO_UP.equalsIgnoreCase(convention) && !RawSeries.EXO_UP.equalsIgnoreCase(convention)) {
            result.diagnostics.add("unknown-sign-convention:" + convention);
            convention = RawSeries.ENDO_UP;
        }
        if (!badRows.isEmpty()) {
            result.diagnostics.add("unconvertible-rows-retained:" + badRows);
        }
        RawSeries series = new RawSeries(segmentId, blob.blobId(), rows.size() - 1, temps, hfs, times,
                tempUnit, hfUnit, timeUnit, convention.toLowerCase(Locale.ROOT), badRows, result.diagnostics);
        result.series = series;
        return result;
    }

    private static Quantity UnitsParse(List<String> row, int col, String unit, String dimension) {
        if (col >= row.size()) {
            return Quantity.unconvertible("", dimension, "missing-cell");
        }
        return dsc.units.Units.parse(row.get(col), dimension, unit);
    }

    private static int findColumn(List<String> headers, String... keys) {
        for (int i = 0; i < headers.size(); i++) {
            String h = headers.get(i).toLowerCase(Locale.ROOT);
            for (String key : keys) {
                if (h.contains(key.toLowerCase(Locale.ROOT))) {
                    return i;
                }
            }
        }
        return -1;
    }

    private static String unitFromHeader(String header, String declared) {
        if (declared != null && !declared.isBlank()) {
            return declared.trim();
        }
        int open = header.indexOf('(');
        int close = header.lastIndexOf(')');
        if (open >= 0 && close > open) {
            return header.substring(open + 1, close).trim();
        }
        return "";
    }
}
