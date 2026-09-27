package dsc.core;

import dsc.model.Calibration;
import dsc.model.CandidateBaseline;
import dsc.model.MassRecord;
import dsc.model.NormalizedCurve;
import dsc.model.Segment;
import dsc.model.TangentSelection;

/** 依赖指纹：派生节点通过源内容指纹判断“为何仍有效/为何需重算”。 */
public final class IdsHash {
    private IdsHash() {}

    private static String h(String s) {
        return dsc.model.Ids.sha256(s).substring(0, 16);
    }

    public static String forCurve(Segment seg, MassRecord mass, Calibration cal) {
        StringBuilder sb = new StringBuilder("curve|").append(seg.segmentId).append('|');
        for (var p : seg.points) {
            sb.append(p.tempRaw).append('/').append(p.hfRaw).append(';');
        }
        sb.append("|dir=").append(seg.declaredDirection.code())
          .append("|sign=").append(seg.declaredSign.code())
          .append("|rate=").append(seg.rate == null ? "" : seg.rate.raw);
        sb.append("|mass=").append(mass == null ? "none"
                : mass.versionId + ":" + (mass.mass.valid() ? r(mass.mass.value) : "invalid"));
        sb.append("|cal=").append(cal == null ? "none"
                : cal.versionId + ":" + (cal.offsetC.valid() ? r(cal.offsetC.value) : "invalid"));
        return h(sb.toString());
    }

    public static String forWindow(String segmentId, double fromC, double toC) {
        return h("window|" + segmentId + "|" + r(fromC) + "|" + r(toC));
    }

    public static String forBaseline(String segmentId, String windowId, CandidateBaseline b) {
        StringBuilder sb = new StringBuilder("baseline|").append(segmentId).append('|').append(windowId)
                .append('|').append(b.model.code());
        for (double p : b.params) sb.append('|').append(r(p));
        return h(sb.toString());
    }

    public static String forTangent(TangentSelection t) {
        return h("tangent|" + t.segmentId + "|" + t.windowId + "|"
                + q(t.leftFromC) + "|" + q(t.leftToC) + "|" + q(t.rightFromC) + "|" + q(t.rightToC));
    }

    public static String curveNode(String segmentId) {
        return "curve:" + segmentId;
    }

    public static String windowNode(String windowId) {
        return "window:" + windowId;
    }

    private static String q(dsc.model.Quantity q) {
        return q == null ? "null" : q.raw;
    }

    private static String r(double v) {
        return String.format(java.util.Locale.ROOT, "%.6f", v);
    }
}
