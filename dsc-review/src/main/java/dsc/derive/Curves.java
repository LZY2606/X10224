package dsc.derive;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.List;

/** Normalize raw archived instrument output into K / endo-up W/g with diagnostics. */
public final class Curves {
    private Curves() {}

    /**
     * Build a normalized segment from the instrument source JSON.
     * Raw text and units are retained in the source; here we only read normalized fields
     * that the importer already attempted, and record any unconvertible samples as diagnostics.
     */
    public static Models.Segment normalize(ObjectNode instrument, JsonNode samples,
                                           String rateContext, String directionContext) {
        String segId = instrument.path("segmentId").asText("seg");
        String cycle = instrument.path("cycle").asText("cycle-1");
        String direction = instrument.path("direction").asText(directionContext);
        double rate = instrument.path("rateKPerMin").asDouble(Double.NaN);
        if (Double.isNaN(rate)) rate = parseRate(rateContext);
        String sign = instrument.path("signConvention").asText("undeclared");

        int n = samples.size();
        double[] tRaw = new double[n], hfRaw = new double[n];
        double[] tK = new double[n], hf = new double[n];
        List<String> diag = new ArrayList<>();
        double[] gaps = new double[Math.max(0, n - 1)];

        for (int i = 0; i < n; i++) {
            JsonNode s = samples.get(i);
            tRaw[i] = s.path("tRaw").asDouble(Double.NaN);
            hfRaw[i] = s.path("hfRaw").asDouble(Double.NaN);
            JsonNode tq = s.path("tK");
            tK[i] = tq.isNumber() ? tq.asDouble() : Double.NaN;
            JsonNode hq = s.path("hfEndoUp");
            boolean hfKnown = hq.isNumber();
            hf[i] = hfKnown ? hq.asDouble() : Double.NaN;
            if (!tq.isNumber()) diag.add("sample#" + i + ": temperature unconvertible, kept raw (" + s.path("tRawText").asText("?") + ")");
            if (!hfKnown) diag.add("sample#" + i + ": heat-flow value unconvertible, kept raw");
        }
        // declared sign conversion (only an explicit declaration may set the sign)
        if ("endo-down".equals(sign)) {
            for (int i = 0; i < n; i++) if (!Double.isNaN(hf[i])) hf[i] = -hf[i];
        } else if ("undeclared".equals(sign)) {
            diag.add("heat-flow direction UNDECLARED in instrument header: exo/endo sign is not guessed");
            for (int i = 0; i < n; i++) hf[i] = Double.NaN;
        }

        boolean nonUniform = false;
        if (n > 2) {
            List<Double> pos = new ArrayList<>();
            for (int i = 1; i < n; i++) {
                double g = tK[i] - tK[i-1];
                gaps[i-1] = g;
                if (g > 0) pos.add(g);
            }
            if (!pos.isEmpty()) {
                List<Double> sorted = new ArrayList<>(pos);
                java.util.Collections.sort(sorted);
                double median = sorted.get(sorted.size() / 2);
                double max = sorted.get(sorted.size() - 1);
                if (max > Rules.NONUNIFORM_RATIO * median) nonUniform = true;
            }
        }
        // direction consistency with temperature order
        if (n > 2) {
            int up = 0, down = 0;
            for (int i = 1; i < n; i++) { if (tK[i] > tK[i-1]) up++; else if (tK[i] < tK[i-1]) down++; }
            String observed = up > down ? "heating" : "cooling";
            if (!"unknown".equals(direction) && !observed.equals(direction)) {
                diag.add("declared direction '" + direction + "' disagrees with observed temperature order '"
                        + observed + "' (possible cycle-segment switch)");
            }
        }
        return new Models.Segment(segId, cycle, direction, rate, sign, tRaw, hfRaw, tK, hf, nonUniform, diag);
    }

    private static double parseRate(String r) {
        try { return Double.parseDouble(r.replaceAll("[^0-9.\\-]", "")); } catch (Exception e) { return Double.NaN; }
    }
}
