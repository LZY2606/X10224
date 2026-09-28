package com.materials.dsc.domain;

import com.fasterxml.jackson.databind.JsonNode;
import com.materials.dsc.model.BaselineCandidate;
import com.materials.dsc.model.Point;
import com.materials.dsc.model.Qty;
import com.materials.dsc.model.SourceRecord;
import com.materials.dsc.model.Units;
import com.materials.dsc.util.Json;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/**
 * Produces every defensible baseline for a range: a two-sided linear
 * baseline, a step baseline (glass transition) and, when the reviewer has
 * archived tangent anchors, a user-tangent baseline.  Multiple candidates are
 * always kept; callers never collapse them into one "answer".
 */
public final class BaselineEngine {

    public static final class WorkingPoint {
        public final double t;
        public final double y;
        public final Double timeS;

        WorkingPoint(double t, double y, Double timeS) {
            this.t = t;
            this.y = y;
            this.timeS = timeS;
        }
    }

    public static final class Result {
        public final List<BaselineCandidate> candidates = new ArrayList<>();
        public final List<WorkingPoint> inside = new ArrayList<>();
        public String diagnostic;
    }

    private BaselineEngine() {
    }

    public static List<WorkingPoint> window(SeriesBuilder.Series series, double t0, double t1) {
        List<WorkingPoint> out = new ArrayList<>();
        for (SeriesBuilder.Sample p : series.acquisitionOrder) {
            if (p.hfMissing) {
                continue;
            }
            double lo = Math.min(t0, t1);
            double hi = Math.max(t0, t1);
            if (p.tCalC >= lo - 1e-9 && p.tCalC <= hi + 1e-9) {
                out.add(new WorkingPoint(p.tCalC, p.hfEndoUpMw, p.timeS));
            }
        }
        out.sort(Comparator.comparingDouble(a -> a.t));
        return out;
    }

    private static double[] polyfit(List<WorkingPoint> pts) {
        double sx = 0, sy = 0, sxx = 0, sxy = 0;
        int n = 0;
        for (WorkingPoint p : pts) {
            sx += p.t;
            sy += p.y;
            sxx += p.t * p.t;
            sxy += p.t * p.y;
            n++;
        }
        if (n < 2) {
            return null;
        }
        double d = n * sxx - sx * sx;
        if (Math.abs(d) < 1e-12) {
            return null;
        }
        double slope = (n * sxy - sx * sy) / d;
        double intercept = (sy - slope * sx) / n;
        return new double[]{slope, intercept};
    }

    private static double[] lineAt(double slope, double intercept, double[] ts) {
        double[] ys = new double[ts.length];
        for (int i = 0; i < ts.length; i++) {
            ys[i] = slope * ts[i] + intercept;
        }
        return ys;
    }

    private static double rmse(List<WorkingPoint> pts, double slope, double intercept) {
        double acc = 0;
        for (WorkingPoint p : pts) {
            double r = p.y - (slope * p.t + intercept);
            acc += r * r;
        }
        return Math.sqrt(acc / pts.size());
    }

    private static double[] ts(List<WorkingPoint> pts) {
        double[] t = new double[pts.size()];
        for (int i = 0; i < pts.size(); i++) {
            t[i] = pts[i].t;
        }
        return t;
    }

    public static Result compute(String analysisId, String rangeId, double t0, double t1,
                                 SeriesBuilder.Series series, SourceRecord tangent) {
        Result r = new Result();
        r.inside.addAll(window(series, t0, t1));
        if (r.inside.size() < 3) {
            r.diagnostic = "empty/too-narrow range: " + r.inside.size()
                    + " usable sample(s) inside [" + fmt(t0) + ", " + fmt(t1) + "] "
                    + Units.CELSIUS + "; no baseline derivable";
            return r;
        }
        List<WorkingPoint> pts = r.inside;
        double spanT = pts.get(pts.size() - 1).t - pts.get(0).t;
        int edge = Math.max(2, pts.size() / 4);
        List<WorkingPoint> pre = pts.subList(0, Math.min(edge, pts.size() - 1));
        List<WorkingPoint> post = pts.subList(pts.size() - Math.min(edge, pts.size() - 1), pts.size());

        // 1) two-sided linear baseline
        double[] preFit = polyfit(pre);
        double[] postFit = polyfit(post);
        if (preFit != null && postFit != null) {
            double slope = (preFit[0] + postFit[0]) / 2.0;
            double intercept = ((preFit[1] + preFit[0] * spanMid(pre))
                    + (postFit[1] + postFit[0] * spanMid(post))) / 2.0
                    - slope * spanMid(pts);
            r.candidates.add(makeCandidate(analysisId, rangeId, "linear", t0, t1, pts,
                    lineAt(slope, intercept, ts(pts)),
                    "least-squares mean of pre/post plateau tangents",
                    slope, intercept, Double.NaN, rmse(pts, slope, intercept)));
        }

        // 2) step baseline (midpoint step, separate pre/post levels)
        double[] step = fitStep(pts);
        if (step != null) {
            double[] ys = new double[pts.size()];
            for (int i = 0; i < pts.size(); i++) {
                ys[i] = pts.get(i).t < step[2] ? step[0] : step[1];
            }
            double rmse = 0;
            for (int i = 0; i < pts.size(); i++) {
                double rr = pts.get(i).y - ys[i];
                rmse += rr * rr;
            }
            BaselineCandidate c = makeCandidate(analysisId, rangeId, "step", t0, t1, pts, ys,
                    "two-level step fit for glass-transition Cp change",
                    Double.NaN, Double.NaN, step[2],
                    Math.sqrt(rmse / pts.size()));
            c.metrics.put("preLevelMw", round(step[0]));
            c.metrics.put("postLevelMw", round(step[1]));
            c.metrics.put("stepMw", round(step[1] - step[0]));
            r.candidates.add(c);
        }

        // 3) user-tangent baseline from separately archived tangent selection
        if (tangent != null) {
            JsonNode p = tangent.payload;
            List<WorkingPoint> chosen = new ArrayList<>();
            String err = null;
            for (String key : new String[]{"preAnchor", "postAnchor"}) {
                JsonNode node = p.get(key);
                if (node == null || node.isNull()) {
                    err = "tangent selection " + tangent.id + " missing " + key;
                    break;
                }
                String raw = node.has("raw") ? node.get("raw").asText()
                        : node.asText();
                Qty q = Units.parse(raw, Set.of(Units.CELSIUS, Units.KELVIN), key);
                if (!q.usable()) {
                    err = key + ": " + q.diagnostic;
                    break;
                }
                WorkingPoint nearest = nearest(pts, q.value);
                if (nearest == null) {
                    err = key + " at " + fmt(q.value) + " " + Units.CELSIUS
                            + " has no sample inside the range";
                    break;
                }
                chosen.add(nearest);
            }
            BaselineCandidate c;
            if (err != null || chosen.size() < 2) {
                double[] empty = new double[pts.size()];
                c = makeCandidate(analysisId, rangeId, "user-tangent", t0, t1, pts, empty,
                        "archived tangent unusable: " + err,
                        Double.NaN, Double.NaN, Double.NaN, Double.NaN);
                c.metrics.put("diagnostic", com.materials.dsc.util.Json.MAPPER.getNodeFactory()
                        .textNode(err == null ? "insufficient anchors" : err));
                c.getClass();
                r.candidates.add(markInvalid(c, err == null ? "insufficient anchors" : err));
            } else {
                double slope = (chosen.get(1).y - chosen.get(0).y)
                        / (chosen.get(1).t - chosen.get(0).t);
                double intercept = chosen.get(0).y - slope * chosen.get(0).t;
                c = makeCandidate(analysisId, rangeId, "user-tangent", t0, t1, pts,
                        lineAt(slope, intercept, ts(pts)),
                        "line through reviewer-selected tangents (" + tangent.id + " v"
                                + tangent.version + ")",
                        slope, intercept, Double.NaN, rmse(pts, slope, intercept));
                c.metrics.put("tangentSourceId", Json.MAPPER.getNodeFactory().textNode(tangent.id));
                c.metrics.put("tangentSourceVersion", tangent.version);
                r.candidates.add(c);
            }
        }
        return r;
    }

    private static BaselineCandidate markInvalid(BaselineCandidate c, String diag) {
        BaselineCandidate invalid = new BaselineCandidate(c.id, c.analysisId, c.method, c.rangeId,
                c.t0, c.t1, c.lineT, c.lineY, c.rationale, "invalid", c.nodeVersion);
        invalid.metrics = c.metrics.deepCopy();
        invalid.metrics.put("diagnostic", Json.MAPPER.getNodeFactory().textNode(diag));
        return invalid;
    }

    private static BaselineCandidate makeCandidate(String analysisId, String rangeId, String method,
                                                   double t0, double t1, List<WorkingPoint> pts,
                                                   double[] ys, String rationale,
                                                   double slope, double intercept,
                                                   double stepT, double rmse) {
        String id = "bc_" + Hashes.stable(analysisId, rangeId, method);
        BaselineCandidate c = new BaselineCandidate(id, analysisId, method, rangeId, t0, t1,
                ts(pts), ys, rationale, "candidate", 0);
        if (!Double.isNaN(slope)) {
            c.metrics.put("slope", round(slope));
            c.metrics.put("intercept", round(intercept));
        }
        if (!Double.isNaN(stepT)) {
            c.metrics.put("stepTemperatureC", round(stepT));
        }
        c.metrics.put("rmseMw", round(rmse));
        c.metrics.put("sampleCount", pts.size());
        return c;
    }

    private static WorkingPoint nearest(List<WorkingPoint> pts, double t) {
        WorkingPoint best = null;
        double bd = Double.MAX_VALUE;
        for (WorkingPoint p : pts) {
            double d = Math.abs(p.t - t);
            if (d < bd) {
                bd = d;
                best = p;
            }
        }
        return best;
    }

    private static double spanMid(List<WorkingPoint> pts) {
        return (pts.get(0).t + pts.get(pts.size() - 1).t) / 2.0;
    }

    private static double[] fitStep(List<WorkingPoint> pts) {
        double bestSse = Double.MAX_VALUE;
        double[] best = null;
        for (int k = 2; k < pts.size() - 2; k++) {
            double[] a = polyfit(pts.subList(0, k));
            double[] b = polyfit(pts.subList(k, pts.size()));
            if (a == null || b == null) {
                continue;
            }
            double sse = 0;
            for (int i = 0; i < k; i++) {
                double rr = pts.get(i).y - (a[0] * pts.get(i).t + a[1]);
                sse += rr * rr;
            }
            for (int i = k; i < pts.size(); i++) {
                double rr = pts.get(i).y - (b[0] * pts.get(i).t + b[1]);
                sse += rr * rr;
            }
            if (sse < bestSse) {
                bestSse = sse;
                double midT = (pts.get(k - 1).t + pts.get(k).t) / 2.0;
                best = new double[]{a[0] * midT + a[1], b[0] * midT + b[1], midT};
            }
        }
        return best;
    }

    private static double round(double v) {
        return Math.round(v * 1e6) / 1e6;
    }

    private static String fmt(double v) {
        return String.format("%.2f", v);
    }
}
