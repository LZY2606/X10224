package dsc.fixtures;

import java.util.Locale;

/**
 * Deterministic synthetic DSC curves for the offline case pack. Not a generic
 * spreadsheet: the shapes encode material-realistic events (Tg step, cold
 * crystallisation exotherm, melting endotherm) plus the boundary situations
 * the rules must survive (non-uniform grid, reversed convention, overlaps).
 */
public final class SyntheticDsc {

    private SyntheticDsc() {
    }

    /** Series of heating segments: Tg step + crystallisation exo + melting endo. */
    public static String heatingCsv(double tMin, double tMax, int n, double noise,
                                    boolean endoUp, double tgC, double tcC, double tmC,
                                    long seed, double spacingSkew) {
        Lcg rng = new Lcg(seed);
        StringBuilder sb = new StringBuilder();
        String conv = endoUp ? "endo-up" : "endo-down";
        sb.append("Temperature[").append("C").append("],HeatFlow[mW] # ").append(conv).append('\n');
        double[] grid = skewedGrid(tMin, tMax, n, spacingSkew, rng);
        double orient = endoUp ? 1 : -1;
        for (int i = 0; i < n; i++) {
            double tc = grid[i];
            double hf = baselineMW(tc)
                    + tgStepMW(tc, tgC)
                    - orient * gaussianMW(tc, tcC, 6.0, 1.4)      // crystallisation: exotherm
                    + orient * gaussianMW(tc, tmC, 5.0, 2.6)      // melting: endotherm
                    + (rng.next() - 0.5) * noise;
            sb.append(String.format(Locale.ROOT, "%.3f,%.5f%n", tc, hf));
        }
        return sb.toString();
    }

    /** Cooling curve with crystallisation exotherm only. */
    public static String coolingCsv(double tMin, double tMax, int n, double noise,
                                    boolean endoUp, double tcC, long seed, double spacingSkew) {
        Lcg rng = new Lcg(seed);
        StringBuilder sb = new StringBuilder();
        sb.append("Temperature[C],HeatFlow[mW] # ").append(endoUp ? "endo-up" : "endo-down").append('\n');
        double[] grid = skewedGrid(tMin, tMax, n, spacingSkew, rng);
        double orient = endoUp ? 1 : -1;
        for (int i = n - 1; i >= 0; i--) {
            double tc = grid[i];
            double hf = baselineMW(tc)
                    - orient * gaussianMW(tc, tcC, 5.0, 2.0)
                    + (rng.next() - 0.5) * noise;
            sb.append(String.format(Locale.ROOT, "%.3f,%.5f%n", tc, hf));
        }
        return sb.toString();
    }

    /** Two endotherms deliberately placed close together (overlapping transitions). */
    public static String overlappingCsv(double tMin, double tMax, int n, double noise, long seed) {
        Lcg rng = new Lcg(seed);
        StringBuilder sb = new StringBuilder();
        sb.append("Temperature[C],HeatFlow[mW] # endo-up\n");
        double[] grid = skewedGrid(tMin, tMax, n, 0.0, rng);
        for (int i = 0; i < n; i++) {
            double tc = grid[i];
            double hf = baselineMW(tc)
                    + gaussianMW(tc, 238, 4.0, 1.6)
                    + gaussianMW(tc, 248, 5.0, 1.8)
                    + (rng.next() - 0.5) * noise;
            sb.append(String.format(Locale.ROOT, "%.3f,%.5f%n", tc, hf));
        }
        return sb.toString();
    }

    /** A fragment whose temperature reverses mid-way (must be split). */
    public static String reversingCsv() {
        StringBuilder sb = new StringBuilder();
        sb.append("Temperature[C],HeatFlow[mW] # endo-up\n");
        for (int tc = 40; tc <= 120; tc += 2) sb.append(tc).append(',').append(baselineMW(tc)).append('\n');
        for (int tc = 118; tc >= 60; tc -= 3) sb.append(tc).append(',').append(baselineMW(tc) - 0.1).append('\n');
        return sb.toString();
    }

    private static double baselineMW(double tc) {
        return -6.0 + 0.012 * tc;
    }

    private static double tgStepMW(double tc, double tgC) {
        double spread = 3.0;
        double frac = 1.0 / (1.0 + Math.exp(-(tc - tgC) / spread));
        return 0.25 * frac;
    }

    private static double gaussianMW(double tc, double center, double widthC, double amp) {
        double z = (tc - center) / widthC;
        return amp * Math.exp(-0.5 * z * z);
    }

    private static double[] skewedGrid(double lo, double hi, int n, double skew, Lcg rng) {
        double[] g = new double[n];
        double total = 0;
        double[] w = new double[n];
        for (int i = 0; i < n; i++) {
            // skew>0 clusters points late in the range (non-uniform temperature spacing)
            double f = (double) i / (n - 1);
            w[i] = 1.0 + skew * f * f + (rng.next() - 0.5) * skew * 0.2;
            total += w[i];
        }
        double acc = 0;
        for (int i = 0; i < n; i++) {
            g[i] = lo + (hi - lo) * (acc / total);
            acc += w[i];
        }
        g[n - 1] = hi;
        return g;
    }

    static final class Lcg {
        private long state;

        Lcg(long seed) {
            this.state = seed == 0 ? 0x9e3779b97f4a7c15L : seed;
        }

        double next() {
            state = state * 6364136223846793005L + 1442695040888963407L;
            return (state >>> 11) / (double) (1L << 53);
        }
    }
}
