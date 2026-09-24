package dsc.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Parses an instrument CSV into a {@link RawData} fragment.
 * The original text is retained in full; unparseable cells stay visible as
 * {@link RawData.RawRowIssue} instead of being dropped for plotting. The
 * endo/exo convention is read from the header only — a file named
 * "heating_exo_up.csv" without a matching header row yields UNKNOWN and the
 * downstream rules refuse to guess.
 */
public final class CsvSeriesParser {

    private CsvSeriesParser() {
    }

    public static RawData parse(String sourceName, String csv, String declaredConvention) {
        String[] lines = csv.split("\\R");
        String tempUnit = "K";
        String hfUnit = "mW";
        HeatFlowConvention convention = HeatFlowConvention.fromHeader(declaredConvention);
        int dataStart = 0;

        for (int i = 0; i < Math.min(lines.length, 6); i++) {
            String low = lines[i].toLowerCase(Locale.ROOT);
            if (low.startsWith("temp") || low.startsWith("temperature") || low.contains("temp[")) {
                tempUnit = bracketUnit(lines[i], tempUnit);
                hfUnit = bracketUnit(secondColumn(lines[i]), hfUnit);
                if (low.contains("endo") || low.contains("exo")) {
                    convention = HeatFlowConvention.fromHeader(lines[i]);
                }
                dataStart = i + 1;
                break;
            }
        }

        List<Double> temps = new ArrayList<>();
        List<Double> hfs = new ArrayList<>();
        List<RawData.RawRowIssue> issues = new ArrayList<>();
        int rowIndex = dataStart;
        for (int i = dataStart; i < lines.length; i++) {
            String line = lines[i];
            if (line.isBlank() || line.startsWith("#")) {
                if (line.toLowerCase(Locale.ROOT).contains("endo")
                        || line.toLowerCase(Locale.ROOT).contains("exo")) {
                    convention = HeatFlowConvention.fromHeader(line);
                }
                continue;
            }
            String[] parts = line.split(",", -1);
            if (parts.length < 2) continue;
            String rt = parts[0].trim();
            String rh = parts[1].trim();
            Double tv = tryNumber(rt);
            Double hv = tryNumber(rh);
            if (tv == null || hv == null) {
                String reason = tv == null && hv == null ? "temperature and heat flow unparseable"
                        : tv == null ? "temperature unparseable" : "heat flow unparseable";
                issues.add(new RawData.RawRowIssue(rowIndex, rt, rh, reason));
            } else {
                temps.add(toCanonical(tv, tempUnit));
                hfs.add(toCanonical(hv, hfUnit));
            }
            rowIndex++;
        }
        double[] ta = temps.stream().mapToDouble(Double::doubleValue).toArray();
        double[] ha = hfs.stream().mapToDouble(Double::doubleValue).toArray();
        return new RawData(sourceName, tempUnit, hfUnit, convention, ta, ha, issues, csv);
    }

    private static String bracketUnit(String header, String fallback) {
        int i = header.indexOf('[');
        int j = header.indexOf(']');
        return i >= 0 && j > i ? header.substring(i + 1, j).trim() : fallback;
    }

    private static String secondColumn(String header) {
        String[] parts = header.split(",");
        return parts.length > 1 ? parts[1] : header;
    }

    private static Double tryNumber(String s) {
        try {
            return Double.parseDouble(s.replace(",", "."));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static double toCanonical(double v, String unit) {
        Quantity q = Quantity.parsed(v, Double.toString(v), unit);
        return q.value();
    }
}
