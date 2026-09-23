package dsc.core;

public final class QuantityFormat {
    private QuantityFormat() {}
    public static String trim(double v) {
        if (v == 0) return "0";
        double a = Math.abs(v);
        if (a >= 100 || a < 0.01) return String.format("%.4g", v);
        return String.format("%.4f", v).replaceAll("0+$", "").replaceAll("\\.$", "");
    }
}
