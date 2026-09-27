package dsc.core;

import dsc.domain.BaselineCandidate;
import dsc.domain.BaselineKind;
import dsc.domain.RawPoint;
import dsc.domain.TangentSelection;
import dsc.domain.TransitionType;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds legal baseline candidates. Multiple reasonable baselines are all
 * returned; the caller keeps them and records which one (if any) was adopted.
 * Each candidate's stable identity is a function of its inputs, so identical
 * recomputation returns the same id.
 */
public final class BaselineEngine {

    public record Inputs(
            String intervalId,
            TransitionType type,
            List<RawPoint> points,
            int startIndex,
            int endIndex,
            TangentSelection preTangent,
            TangentSelection postTangent,
            boolean overlap) {
    }

    private BaselineEngine() {
    }

    public static List<BaselineCandidate> candidates(Inputs in) {
        List<BaselineCandidate> out = new ArrayList<>();
        if (in.endIndex() <= in.startIndex()) {
            return out;
        }
        double[] t = new double[in.points().size()];
        double[] f = new double[in.points().size()];
        for (int i = 0; i < t.length; i++) {
            t[i] = in.points().get(i).canonicalTempK();
            f[i] = in.points().get(i).canonicalFlowW();
        }

        List<String> diagnostics = new ArrayList<>();
        if (in.overlap()) {
            diagnostics.add("overlapping transitions inside interval: multiple legal baselines retained");
        }

        if (in.type() == TransitionType.GLASS && in.preTangent() != null && in.postTangent() != null) {
            LinearFit pre = LinearFit.fit(t, f, in.preTangent().startIndex(), in.preTangent().endIndex());
            LinearFit post = LinearFit.fit(t, f, in.postTangent().startIndex(), in.postTangent().endIndex());
            String content = contentKey(in) + "|step|" + lineKey(pre) + "|" + lineKey(post);
            out.add(new BaselineCandidate(
                    dsc.core.Hashes.id("bl", content), in.intervalId(), BaselineKind.STEP_TANGENT,
                    "step tangent (pre/post)", in.preTangent().id(), in.postTangent().id(),
                    0, 0, pre.slope, post.slope, pre.intercept, post.intercept, List.of()));
            if (in.overlap()) {
                addLinearAlternative(out, in, t, f, diagnostics);
            }
            return out;
        }

        if (in.preTangent() != null && in.postTangent() != null) {
            LinearFit pre = LinearFit.fit(t, f, in.preTangent().startIndex(), in.preTangent().endIndex());
            LinearFit post = LinearFit.fit(t, f, in.postTangent().startIndex(), in.postTangent().endIndex());
            double t1 = 0.5 * (t[in.preTangent().startIndex()] + t[in.preTangent().endIndex()]);
            double t2 = 0.5 * (t[in.postTangent().startIndex()] + t[in.postTangent().endIndex()]);
            double f1 = pre.slope * t1 + pre.intercept;
            double f2 = post.slope * t2 + post.intercept;
            double slope = (f2 - f1) / (t2 - t1);
            double offset = f1 - slope * t1;
            List<String> d = new ArrayList<>(diagnostics);
            if (pre.r2 < 0.9) d.add("WARNING: pre tangent fit r2=" + String.format("%.3g", pre.r2));
            if (post.r2 < 0.9) d.add("WARNING: post tangent fit r2=" + String.format("%.3g", post.r2));
            String content = contentKey(in) + "|linear|" + lineKey(pre) + "|" + lineKey(post);
            out.add(new BaselineCandidate(
                    dsc.core.Hashes.id("bl", content), in.intervalId(), BaselineKind.LINEAR_TANGENT,
                    "linear joining tangents", in.preTangent().id(), in.postTangent().id(),
                    slope, offset, 0, 0, 0, 0, d));

            if (in.overlap()) {
                // Legally distinct alternative: a shared tangent under both peaks.
                addSharedAlternative(out, in, t, f, pre, post, diagnostics);
            }
        }
        return out;
    }

    private static void addLinearAlternative(List<BaselineCandidate> out, Inputs in, double[] t, double[] f,
                                             List<String> diagnostics) {
        LinearFit pre = LinearFit.fit(t, f, in.preTangent().startIndex(), in.preTangent().endIndex());
        LinearFit post = LinearFit.fit(t, f, in.postTangent().startIndex(), in.postTangent().endIndex());
        double t1 = 0.5 * (t[in.preTangent().startIndex()] + t[in.preTangent().endIndex()]);
        double t2 = 0.5 * (t[in.postTangent().startIndex()] + t[in.postTangent().endIndex()]);
        double f1 = pre.slope * t1 + pre.intercept;
        double f2 = post.slope * t2 + post.intercept;
        double slope = (f2 - f1) / (t2 - t1);
        double offset = f1 - slope * t1;
        List<String> d = new ArrayList<>(diagnostics);
        d.add("alternative linear baseline retained despite step interpretation");
        out.add(new BaselineCandidate(
                dsc.core.Hashes.id("bl", contentKey(in) + "|linear-alt|" + lineKey(pre) + lineKey(post)),
                in.intervalId(), BaselineKind.LINEAR_TANGENT,
                "linear alternative", in.preTangent().id(), in.postTangent().id(),
                slope, offset, 0, 0, 0, 0, d));
    }

    private static void addSharedAlternative(List<BaselineCandidate> out, Inputs in, double[] t, double[] f,
                                             LinearFit pre, LinearFit post, List<String> diagnostics) {
        // Fit a single line over the union of both tangent windows.
        int a = Math.min(in.preTangent().startIndex(), in.postTangent().startIndex());
        int b = Math.max(in.preTangent().endIndex(), in.postTangent().endIndex());
        LinearFit shared = LinearFit.fit(t, f, a, b);
        List<String> d = new ArrayList<>(diagnostics);
        d.add("shared tangent is a second legal partition of the overlap");
        out.add(new BaselineCandidate(
                dsc.core.Hashes.id("bl", contentKey(in) + "|shared|" + lineKey(shared)),
                in.intervalId(), BaselineKind.SHARED_TANGENT,
                "shared tangent over overlap", in.preTangent().id(), in.postTangent().id(),
                shared.slope, shared.intercept, 0, 0, 0, 0, d));
    }

    private static String lineKey(LinearFit fit) {
        return String.format("%.10g_%.10g", fit.slope, fit.intercept);
    }

    private static String contentKey(Inputs in) {
        StringBuilder sb = new StringBuilder(in.intervalId()).append('|').append(in.type().code());
        for (RawPoint p : in.points()) {
            if (p.computable()) {
                sb.append('|').append(p.index())
                        .append(':').append(String.format("%.8g", p.canonicalTempK()))
                        .append(':').append(String.format("%.8g", p.canonicalFlowW()));
            }
        }
        sb.append("|pre=").append(in.preTangent() == null ? "none"
                : in.preTangent().startIndex() + "-" + in.preTangent().endIndex());
        sb.append("|post=").append(in.postTangent() == null ? "none"
                : in.postTangent().startIndex() + "-" + in.postTangent().endIndex());
        return sb.toString();
    }
}
