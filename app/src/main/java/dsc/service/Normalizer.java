package dsc.service;

import dsc.domain.*;
import dsc.store.SegmentDao;
import dsc.store.SourceDao;

import java.util.ArrayList;
import java.util.List;
import java.util.OptionalDouble;

/**
 * Derivation-layer normalization. The source rows are never rewritten: this class reads raw points
 * plus current sources and produces the working frame (°C, W/g, EXO_UP). Unconvertible points stay
 * in place as NaN entries with a diagnostic rather than being deleted.
 */
public class Normalizer {

    public static class Result {
        public NormalizedCurve curve;
        public double rateSignedCperMin = Double.NaN;
        public List<String> diagnostics = new ArrayList<>();
        public double calibrationOffsetC;
    }

    private final SegmentDao segments;
    private final SourceDao sources;

    public Normalizer(SegmentDao segments, SourceDao sources) {
        this.segments = segments; this.sources = sources;
    }

    public Result normalize(Segment seg) {
        Result out = new Result();
        NormalizedCurve c = new NormalizedCurve();
        out.curve = c;
        List<CurvePoint> pts = segments.points(seg.id);
        int n = pts.size();
        c.tC = new double[n];
        c.hfWg = new double[n];
        c.badPoints = new ArrayList<>();

        double massMg = Double.NaN;
        for (SourceRecord s : sources.currentForSample(seg.sampleId)) {
            if (s.kind == Enums.SourceKind.MASS && s.numericValid) {
                OptionalDouble mg = Units.toMg(s.numericValue, s.unit);
                if (mg.isPresent()) massMg = mg.getAsDouble();
            }
        }
        c.massMg = massMg; c.massKnown = !Double.isNaN(massMg);

        double calC = 0;
        for (SourceRecord s : sources.currentForSegment(seg.id)) {
            if (s.kind == Enums.SourceKind.TEMPERATURE_CALIBRATION && s.numericValid) {
                OptionalDouble cv = Units.toCelsius(s.numericValue, s.unit);
                calC = cv.orElse(0);
                out.calibrationOffsetC = calC;
            }
            if (s.kind == Enums.SourceKind.SCAN_RATE && s.numericValid) {
                OptionalDouble rv = Units.toCelsiusPerMin(s.numericValue, s.unit);
                if (rv.isPresent()) {
                    double r = rv.getAsDouble();
                    out.rateSignedCperMin = seg.cooling() ? -Math.abs(r) : Math.abs(r);
                }
            }
        }

        for (int i = 0; i < n; i++) {
            CurvePoint p = pts.get(i);
            OptionalDouble t = Units.toCelsius(p.tRaw, p.tRawUnit);
            OptionalDouble h = Units.toWattsPerGram(p.hfRaw, p.hfRawUnit, massMg);
            if (t.isEmpty() || h.isEmpty()) {
                c.tC[i] = Double.NaN; c.hfWg[i] = Double.NaN;
                c.badPoints.add(i);
                out.diagnostics.add("unconvertible-point@" + i + " tUnit=" + p.tRawUnit + " hfUnit=" + p.hfRawUnit);
                continue;
            }
            c.tC[i] = t.getAsDouble() + calC;
            double hf = h.getAsDouble();
            if (seg.signConvention == Enums.SignConvention.EXO_DOWN) { hf = -hf; c.signFlipped = true; }
            c.hfWg[i] = hf;
        }
        if (seg.signConvention == Enums.SignConvention.UNKNOWN) {
            out.diagnostics.add("sign-convention-unknown: direction semantics cannot be resolved");
        }
        return out;
    }
}
