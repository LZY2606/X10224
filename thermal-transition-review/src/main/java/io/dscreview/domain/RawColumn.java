package io.dscreview.domain;

import java.util.ArrayList;
import java.util.List;

/**
 * One imported column with its original textual cells preserved verbatim.
 * Numeric parsing is best-effort: unparseable cells stay in place
 * ({@link #numeric()} carries NaN) and yield diagnostics instead of being
 * dropped for plotting convenience.
 */
public final class RawColumn {
    private final String name;
    private final String declaredUnit;
    private final List<String> cells;

    public RawColumn(String name, String declaredUnit, List<String> cells) {
        this.name = name;
        this.declaredUnit = declaredUnit == null ? "" : declaredUnit;
        this.cells = List.copyOf(cells);
    }

    public String name() { return name; }
    public String declaredUnit() { return declaredUnit; }
    public List<String> cells() { return cells; }

    public List<Double> numeric() {
        List<Double> out = new ArrayList<>(cells.size());
        for (String cell : cells) {
            out.add(parseNumber(cell));
        }
        return out;
    }

    public record BadCell(int row, String text) {}

    public List<BadCell> nonNumericRows() {
        List<BadCell> bad = new ArrayList<>();
        for (int i = 0; i < cells.size(); i++) {
            if (Double.isNaN(parseNumber(cells.get(i)))) {
                bad.add(new BadCell(i, cells.get(i)));
            }
        }
        return bad;
    }

    static double parseNumber(String text) {
        if (text == null) return Double.NaN;
        String t = text.trim().replace(",", ".");
        if (t.isEmpty() || t.equals("-") || t.equalsIgnoreCase("na")
                || t.equalsIgnoreCase("n/a") || t.equalsIgnoreCase("null")) {
            return Double.NaN;
        }
        try {
            return Double.parseDouble(t);
        } catch (NumberFormatException e) {
            return Double.NaN;
        }
    }
}
