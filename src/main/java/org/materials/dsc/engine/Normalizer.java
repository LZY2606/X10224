package org.materials.dsc.engine;

import org.materials.dsc.domain.Diagnostic;
import org.materials.dsc.domain.InstrumentOutput;
import org.materials.dsc.domain.SampleRow;
import org.materials.dsc.domain.SignConvention;
import org.materials.dsc.domain.TemperatureCalibration;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 派生层规范化：
 *  温度：原始单位 -> °C -> 应用温度标定；
 *  热流：原始单位 -> mW -> 应用仪器符号约定（规范域 exo 为正）。
 * 原始表示仍留在 InstrumentOutput.rows；无法转换的点留在原位置并生成诊断，不被丢弃。
 */
public final class Normalizer {
    private Normalizer() {
    }

    public static final class Series {
        public final double[] tCelsius;
        public final double[] hfMw;
        public final boolean[] valid;
        public final List<Diagnostic> diagnostics;

        Series(double[] tCelsius, double[] hfMw, boolean[] valid, List<Diagnostic> diagnostics) {
            this.tCelsius = tCelsius;
            this.hfMw = hfMw;
            this.valid = valid;
            this.diagnostics = diagnostics;
        }

        public int length() {
            return tCelsius.length;
        }
    }

    public static Series normalize(InstrumentOutput out, TemperatureCalibration cal) {
        int n = out.rows().size();
        double[] t = new double[n];
        double[] hf = new double[n];
        boolean[] valid = new boolean[n];
        List<Diagnostic> diag = new ArrayList<>();
        if (out.signConvention() == SignConvention.UNKNOWN) {
            diag.add(Diagnostic.error("diag-sign-" + out.id(), "unknown-sign-convention",
                    "热流符号约定缺失，吸放热方向无法判定（不允许按文件名猜测）", out.id()));
        }
        for (SampleRow row : out.rows()) {
            int i = row.index();
            try {
                double tv = parseDouble(row.rawTemperature());
                double hv = parseDouble(row.rawHeatFlow());
                double tC = tv;
                if (out.tempUnit() != null) {
                    tC = out.tempUnit().toCanonical(tv);
                }
                if (cal != null) {
                    tC = cal.applyCelsius(tC);
                }
                double hfmW = hv;
                if (out.hfUnit() != null) {
                    hfmW = out.hfUnit().toCanonical(hv);
                }
                if (out.signConvention() != SignConvention.UNKNOWN) {
                    hfmW *= out.signConvention().canonicalFactor();
                }
                t[i] = tC;
                hf[i] = hfmW;
                valid[i] = true;
            } catch (RuntimeException ex) {
                valid[i] = false;
                t[i] = Double.NaN;
                hf[i] = Double.NaN;
                diag.add(Diagnostic.warn(
                        String.format(Locale.ROOT, "diag-row-%s-%d", out.id(), i),
                        "unconvertible-row",
                        "第 " + i + " 行无法规范化，保留原始表示: " + ex.getMessage(),
                        out.id()));
            }
        }
        return new Series(t, hf, valid, diag);
    }

    private static double parseDouble(String s) {
        if (s == null) {
            throw new IllegalArgumentException("empty token");
        }
        String t = s.trim().replace(",", "");
        if (t.isEmpty() || t.equalsIgnoreCase("nan") || t.equalsIgnoreCase("null")
                || t.equals("NA") || t.equals("--")) {
            throw new IllegalArgumentException("non-numeric token '" + s + "'");
        }
        return Double.parseDouble(t);
    }
}
