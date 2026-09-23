package dsc.core;

import java.util.ArrayList;
import java.util.List;

/**
 * Raw archive -> canonical derivation layer.
 *
 * Original text/unit representations stay on the archived objects; here they
 * are parsed, converted to canonical frames (K, s, W, W/g, endo-up), and any
 * unconvertible value is retained in place with a diagnostic.
 */
public final class Normalizer {

    public record Inputs(SegmentInput segment, MassVersion mass,
                         ScanRateRecord rate, TemperatureCalibration cal) {}

    public NormalizedSegment normalize(Inputs in) {
        SegmentInput seg = in.segment();
        List<Diagnostic> diag = new ArrayList<>();
        double calOffsetK = 0.0;
        if (in.cal() != null) {
            Qty off = in.cal().offset().to("K");
            if (off.isUnconverted()) {
                diag.add(Diagnostic.warn("CALIBRATION-UNCONVERTED",
                        "temperature calibration offset could not be converted to K: "
                                + in.cal().offset() + "; treating as 0",
                        in.cal().id()));
            } else {
                calOffsetK = off.value();
            }
        } else {
            diag.add(Diagnostic.warn("CALIBRATION-MISSING",
                    "no temperature calibration archived for " + seg.id(),
                    seg.id()));
        }

        double massG = Double.NaN;
        if (in.mass() != null) {
            Qty mg = in.mass().mass().to("g");
            if (mg.isUnconverted()) {
                diag.add(Diagnostic.error("MASS-UNCONVERTED",
                        "mass value cannot be converted to grams: " + in.mass().mass(),
                        in.mass().id()));
            } else if (mg.value() <= 0.0) {
                diag.add(Diagnostic.error("MASS-NONPOSITIVE",
                        "mass must be positive, got " + mg.value(), in.mass().id()));
            } else {
                massG = mg.value();
            }
        } else {
            diag.add(Diagnostic.warn("MASS-MISSING",
                    "no mass version archived for " + seg.sampleId(),
                    seg.sampleId()));
        }

        double rateKs = Double.NaN;
        if (in.rate() != null) {
            Qty r = in.rate().rate().to("K/s");
            if (r.isUnconverted()) {
                diag.add(Diagnostic.warn("RATE-UNCONVERTED",
                        "scan rate cannot be converted to K/s: " + in.rate().rate(),
                        in.rate().id()));
            } else {
                rateKs = r.value();
            }
        }

        if (seg.polarity() == SignConvention.UNKNOWN) {
            diag.add(Diagnostic.error("POLARITY-UNKNOWN",
                    "heat-flow endo/exo polarity is not declared; direction must "
                            + "not be inferred from the file name (" + seg.sourceFile() + ")",
                    seg.id()));
        }
        if (seg.direction() == ScanDirection.UNKNOWN
                || seg.direction() == ScanDirection.MIXED) {
            diag.add(Diagnostic.warn("DIRECTION-AMBIGUOUS",
                    "segment thermal direction is " + seg.direction().token()
                            + "; heating/cooling must not be mixed", seg.id()));
        }

        double pol = seg.polarity().toEndoUpFactor();
        List<NormalizedPoint> out = new ArrayList<>();
        double tPrev = Double.NaN;
        for (RawPoint rp : seg.points()) {
            Double tK = parseTemp(rp, diag);
            Double hfW = parsePower(rp, diag);
            boolean accepted = tK != null && hfW != null;
            double tk = tK == null ? Double.NaN : tK + calOffsetK;
            double hw = hfW == null ? Double.NaN : hfW * pol;
            if (accepted && !Double.isNaN(tPrev)) {
                if (seg.direction() == ScanDirection.HEATING && tk < tPrev - 1e-9) {
                    diag.add(Diagnostic.warn("NONMONOTONIC-TEMPERATURE",
                            "temperature decreases inside a heating segment at index "
                                    + rp.index(), seg.id()));
                }
                if (seg.direction() == ScanDirection.COOLING && tk > tPrev + 1e-9) {
                    diag.add(Diagnostic.warn("NONMONOTONIC-TEMPERATURE",
                            "temperature increases inside a cooling segment at index "
                                    + rp.index(), seg.id()));
                }
            }
            double tC = Double.isNaN(tk) ? Double.NaN : tk - 273.15;
            double hfWg = (Double.isNaN(hw) || Double.isNaN(massG))
                    ? Double.NaN : hw / massG;
            NormalizedPoint np = new NormalizedPoint(rp.index(), tk, tC,
                    Double.NaN, hw, hfWg, accepted, rp.tRaw(), rp.hfRaw());
            out.add(np);
            if (accepted) { tPrev = tk; }
        }

        fillTimesAndSpacing(out, rateKs, seg, diag);

        if (out.isEmpty()) {
            diag.add(Diagnostic.error("SEGMENT-EMPTY",
                    "segment contains zero points", seg.id()));
        }
        return new NormalizedSegment(seg.id(), seg.direction(),
                in.mass() == null ? null : in.mass().id(),
                in.rate() == null ? null : in.rate().id(),
                in.cal() == null ? null : in.cal().id(),
                massG, rateKs, out, diag);
    }

    private Double parseTemp(RawPoint rp, List<Diagnostic> diag) {
        try {
            double v = Double.parseDouble(rp.tRaw().trim());
            Qty q = new Qty(v, rp.tUnit()).to("K");
            if (q.isUnconverted()) {
                diag.add(Diagnostic.error("TEMP-UNCONVERTED",
                        "temperature unit '" + rp.tUnit() + "' cannot be converted; "
                                + "raw value '" + rp.tRaw() + "' retained",
                        "point#" + rp.index()));
                return null;
            }
            return q.value();
        } catch (RuntimeException e) {
            diag.add(Diagnostic.error("TEMP-UNPARSEABLE",
                    "raw temperature '" + rp.tRaw() + "' retained but excluded "
                            + "from integration", "point#" + rp.index()));
            return null;
        }
    }

    private Double parsePower(RawPoint rp, List<Diagnostic> diag) {
        try {
            double v = Double.parseDouble(rp.hfRaw().trim());
            Qty q = new Qty(v, rp.hfUnit()).to("W");
            if (q.isUnconverted()) {
                diag.add(Diagnostic.error("HF-UNCONVERTED",
                        "heat-flow unit '" + rp.hfUnit() + "' cannot be converted; "
                                + "raw value '" + rp.hfRaw() + "' retained",
                        "point#" + rp.index()));
                return null;
            }
            return q.value();
        } catch (RuntimeException e) {
            diag.add(Diagnostic.error("HF-UNPARSEABLE",
                    "raw heat flow '" + rp.hfRaw() + "' retained but excluded",
                    "point#" + rp.index()));
            return null;
        }
    }

    private void fillTimesAndSpacing(List<NormalizedPoint> pts, double rateKs,
                                     SegmentInput seg, List<Diagnostic> diag) {
        if (pts.size() < 2 || Double.isNaN(rateKs) || rateKs <= 0) return;
        double t0 = pts.get(0).tK();
        List<Double> dts = new ArrayList<>();
        for (int i = 0; i < pts.size(); i++) {
            NormalizedPoint p = pts.get(i);
            double timeS = (p.tK() - t0) / rateKs;
            pts.set(i, new NormalizedPoint(p.index(), p.tK(), p.tC(), timeS,
                    p.hfW(), p.hfWg(), p.accepted(), p.rawT(), p.rawHf()));
            if (i > 0 && p.accepted() && pts.get(i - 1).accepted()) {
                dts.add(Math.abs(p.tK() - pts.get(i - 1).tK()));
            }
        }
        double mean = dts.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        long irregular = dts.stream().filter(d -> Math.abs(d - mean) > 0.25 * mean).count();
        if (irregular > 0) {
            diag.add(Diagnostic.info("NONUNIFORM-TEMPERATURE",
                    irregular + " of " + dts.size()
                            + " temperature steps deviate >25% from mean spacing "
                            + String.format("%.4g K; trapezoidal integration is used", mean),
                    seg.id()));
        }
    }
}
