package dsc.rules;

import dsc.domain.RawPoint;
import java.util.ArrayList;
import java.util.List;

/**
 * Canonical series derived from one segment's raw instrument output plus its archived
 * metadata sources.
 *
 * Points are {@code [time(s), temperature(degC, calibrated), heatFlow(W/g, endo-positive)]}.
 * Non-convertible raw points are NOT dropped from the archive, but they cannot participate
 * in numeric derivation and are reported in {@link #excludedRows} with diagnostics.
 */
public final class PreparedSeries {

    public final List<double[]> points = new ArrayList<>();
    public final List<Integer> includedRows = new ArrayList<>();
    public final List<int[]> excludedRows = new ArrayList<>();
    public final List<String> diagnostics = new ArrayList<>();
    public boolean nonUniformTemperature;
    public boolean polarityInverted;
    public boolean absoluteHeatFlow;
    public double massG;
    public double calA = 1.0;
    public double calB = 0.0;
    public String polarity;
    public int rawRowCount;

    private PreparedSeries() {
    }

    /**
     * @param points    raw rows in acquisition order
     * @param polarity  explicit sign convention from instrument metadata
     * @param massG     current sample mass [g]
     * @param hfUnit    heat-flow unit as archived with the instrument output
     * @param calA/calB temperature calibration affine correction
     */
    public static PreparedSeries build(List<RawPoint> points, String polarity,
                                       double massG, String hfUnit, double calA, double calB) {
        PreparedSeries s = new PreparedSeries();
        s.rawRowCount = points.size();
        s.polarity = polarity;
        s.polarityInverted = Polarity.EXO_UP.equals(polarity);
        s.massG = massG;
        s.calA = calA;
        s.calB = calB;
        s.absoluteHeatFlow = dsc.domain.Unit.MW.equals(hfUnit) || dsc.domain.Unit.W.equals(hfUnit);

        for (RawPoint rp : points) {
            if (!rp.clean()) {
                s.excludedRows.add(new int[]{rp.index, rp.index});
                s.diagnostics.addAll(rp.diagnostics);
                continue;
            }
            double tSec = rp.time.canonicalValue;
            double tDeg = Calibration.apply(rp.temperature.canonicalValue, calA, calB);
            double hfCanon = rp.heatFlow.canonicalValue;
            if (s.absoluteHeatFlow) {
                if (!(massG > 0)) {
                    s.excludedRows.add(new int[]{rp.index, rp.index});
                    s.diagnostics.add("heatFlow@" + rp.index + ": cannot normalize absolute heat flow, mass=" + massG + " g");
                    continue;
                }
                hfCanon = hfCanon / massG;
            }
            hfCanon = Polarity.endoPositive(hfCanon, polarity);
            s.points.add(new double[]{tSec, tDeg, hfCanon});
            s.includedRows.add(rp.index);
        }
        s.nonUniformTemperature = CurveMath.temperatureSpacingRatio(s.points) > 1.5;
        return s;
    }

    /** Time-ordered triplets inside the temperature window, without heating/cooling mixing. */
    public List<double[]> window(double fromT, double toT) {
        List<double[]> out = new ArrayList<>();
        for (double[] p : points) {
            if (p[1] >= fromT - 1e-9 && p[1] <= toT + 1e-9) {
                out.add(p);
            }
        }
        out.sort((a, b) -> Double.compare(a[0], b[0]));
        return out;
    }

    /** x=T, y=hf projection for plotting/fitting. */
    public static List<double[]> xy(List<double[]> txy) {
        List<double[]> out = new ArrayList<>();
        for (double[] p : txy) {
            out.add(new double[]{p[1], p[2]});
        }
        return out;
    }
}
