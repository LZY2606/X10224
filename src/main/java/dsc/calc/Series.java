package dsc.calc;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dsc.model.J;
import dsc.model.Quantity;
import dsc.model.Units;

import java.util.ArrayList;
import java.util.List;

/**
 * Parsed one-segment measurement trace.
 *
 * <p>Raw textual representations are preserved on every point. Points whose temperature or
 * heat flow cannot be normalized stay in {@code rawRows} with a diagnostic and are excluded
 * from numeric integration — they are never dropped from the archive or plot model.
 */
public final class Series {

    public static final class RawPoint {
        public final int index;
        public final String tRaw;
        public final String tUnit;
        public final String hRaw;
        public final String hUnit;
        public final String diagnostic;

        RawPoint(int index, String tRaw, String tUnit, String hRaw, String hUnit, String diagnostic) {
            this.index = index;
            this.tRaw = tRaw;
            this.tUnit = tUnit;
            this.hRaw = hRaw;
            this.hUnit = hUnit;
            this.diagnostic = diagnostic == null ? "" : diagnostic;
        }
    }

    /** Calibrated temperature (degC) and aligned heat flow (mW), dense, same length. */
    public final double[] t;
    public final double[] hf;
    public final List<RawPoint> rawRows;
    /** Index in rawRows for each dense position (rawRows may be longer). */
    public final int[] rawIndex;
    public final double calibrationOffsetC;
    public final boolean nonUniformSpacing;
    public final double minStepC;
    public final double maxStepC;
    public final double medianStepC;

    private Series(double[] t, double[] hf, List<RawPoint> rawRows, int[] rawIndex,
                   double calibrationOffsetC, boolean nonUniformSpacing,
                   double minStepC, double maxStepC, double medianStepC) {
        this.t = t;
        this.hf = hf;
        this.rawRows = rawRows;
        this.rawIndex = rawIndex;
        this.calibrationOffsetC = calibrationOffsetC;
        this.nonUniformSpacing = nonUniformSpacing;
        this.minStepC = minStepC;
        this.maxStepC = maxStepC;
        this.medianStepC = medianStepC;
    }

    /**
     * Parse an "output" doc's {@code rows}. Each row is [tempRaw, hfRaw] or
     * {@code {temp:{rawText,rawUnit}, hf:...}}. {@code signMultiplier} (declared heat-flow
     * convention, +1 = exo-up) is metadata; filename never decides it.
     */
    public static Series parse(JsonObject outputData, JsonObject calibrationData,
                               int declaredSign, List<String> diagnostics) {
        JsonArray rows = J.arrOrEmpty(outputData, "rows");
        List<RawPoint> raws = new ArrayList<>();
        List<double[]> good = new ArrayList<>();
        List<Integer> goodIdx = new ArrayList<>();

        double offset = calibrationData != null && calibrationData.has("offsetC")
                && calibrationData.get("offsetC").isJsonPrimitive()
                ? calibrationData.get("offsetC").getAsDouble() : 0.0;
        boolean calOk = calibrationData == null || J.str(calibrationData, "status", "ok").equalsIgnoreCase("ok");

        for (int i = 0; i < rows.size(); i++) {
            JsonObject row = normalizeRow(rows.get(i));
            Quantity tq = Units.temperature(row.get("temp"));
            Quantity hq = Units.heatflow(row.get("hf"));
            String diag = "";
            if (!tq.convertible) diag = "temperature: " + tq.reason();
            if (!hq.convertible) diag = (diag.isEmpty() ? "" : diag + "; ") + "heatflow: " + hq.reason();
            raws.add(new RawPoint(i, tq.rawText, tq.rawUnit, hq.rawText, hq.rawUnit, diag));
            if (!diag.isEmpty()) {
                diagnostics.add("row " + i + " not normalized (" + diag + ")");
                continue;
            }
            double tempCal = tq.value + offset;
            double hfVal = hq.value * declaredSign;
            good.add(new double[]{tempCal, hfVal});
            goodIdx.add(i);
        }
        if (!calOk) diagnostics.add("temperature calibration status=" + J.str(calibrationData, "status", "?")
                + "; calibration offset applied provisionally");

        double[] tt = new double[good.size()];
        double[] hh = new double[good.size()];
        int[] ri = new int[good.size()];
        for (int i = 0; i < good.size(); i++) {
            tt[i] = good.get(i)[0];
            hh[i] = good.get(i)[1];
            ri[i] = goodIdx.get(i);
        }

        double[] steps = new double[Math.max(0, tt.length - 1)];
        double min = Double.POSITIVE_INFINITY, max = 0;
        for (int i = 1; i < tt.length; i++) {
            double d = Math.abs(tt[i] - tt[i - 1]);
            steps[i - 1] = d;
            min = Math.min(min, d);
            max = Math.max(max, d);
        }
        java.util.Arrays.sort(steps);
        double median = steps.length == 0 ? 0 : steps[steps.length / 2];
        boolean nonUniform = steps.length > 2 && (max / Math.max(median, 1e-12)) > 3.0;
        if (nonUniform) {
            diagnostics.add("non-uniform temperature spacing detected (median step "
                    + Quantity.format(median) + " degC, max " + Quantity.format(max)
                    + " degC): trapezoid rule used pointwise, no resampling");
        }
        return new Series(tt, hh, raws, ri, offset, nonUniform,
                steps.length == 0 ? 0 : min, max, median);
    }

    private static JsonObject normalizeRow(com.google.gson.JsonElement el) {
        JsonObject out = J.obj();
        if (el.isJsonArray()) {
            JsonArray a = el.getAsJsonArray();
            J.put(out, "temp", a.get(0));
            J.put(out, "hf", a.get(1));
        } else {
            JsonObject o = el.getAsJsonObject();
            J.put(out, "temp", o.has("temp") ? o.get("temp") : o.get("t"));
            J.put(out, "hf", o.has("hf") ? o.get("hf") : o.get("y"));
        }
        return out;
    }
}
