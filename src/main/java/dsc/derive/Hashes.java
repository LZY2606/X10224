package dsc.derive;

import dsc.domain.Entities.*;
import dsc.domain.Values;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/** 内容哈希：相同依赖内容 => 相同稳定身份；任何直接来源改变 => 下游哈希改变。 */
public final class Hashes {

    private Hashes() {
    }

    public static String shortHash(String s) {
        return sha256(s).substring(0, 16);
    }

    public static String sha256(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] d = md.digest(s.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : d) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    public static String instrument(InstrumentOutput io) {
        StringBuilder sb = new StringBuilder("instrument|").append(io.id).append('|')
                .append(io.heatUnit).append('|').append(io.tempUnit).append('|')
                .append(io.exoSign.code).append('|').append(io.points.size());
        // 采样若干点 + 全部点的累加，保证反向热流/非等间隔改动都变哈希
        double sum = 0;
        for (Values.Point p : io.points) {
            sb.append('#').append(p.tempK).append(':').append(p.heatFlowW);
            sum += p.tempK + p.heatFlowW;
        }
        sb.append('|').append(sum);
        return shortHash(sb.toString());
    }

    public static String mass(MassVersion m) {
        return shortHash("mass|" + m.id + "|" + m.kg + "|" + m.version);
    }

    public static String rate(ScanRate r) {
        return shortHash("rate|" + r.id + "|" + r.kPerMin);
    }

    public static String atmosphere(Atmosphere a) {
        return shortHash("atm|" + a.id + "|" + a.gas);
    }

    public static String calibration(TempCalibration c) {
        return shortHash("cal|" + c.id + "|" + c.offsetK + "|" + c.slope);
    }

    public static String segment(Segment s, InstrumentOutput io, ScanRate r,
                                 Atmosphere a, TempCalibration c, MassVersion m) {
        return shortHash("seg|" + s.id + "|" + s.direction + "|"
                + (io == null ? "x" : io.id) + "|"
                + (r == null ? "x" : r.id) + "|"
                + (a == null ? "x" : a.id) + "|"
                + (c == null ? "x" : c.id) + "|"
                + (m == null ? "x" : m.id) + "|"
                + s.tempRangeLowK + "|" + s.tempRangeHighK);
    }

    public static String candidate(Derived.BaselineCandidate cand) {
        Transition r = cand.result;
        return shortHash("cand|" + cand.id + "|" + cand.method + "|"
                + cand.baseline.leftAnchorK + "|" + cand.baseline.rightAnchorK + "|"
                + cand.baseline.leftValueW + "|" + cand.baseline.rightValueW + "|"
                + cand.fromK + "|" + cand.toK);
    }

    public static String transition(Transition t) {
        return shortHash("tr|" + t.id + "|" + t.areaWSigned + "|"
                + (t.enthalpyJPerG == null ? "n" : t.enthalpyJPerG)
                + "|" + (t.onsetK == null ? "n" : t.onsetK)
                + "|" + (t.peakK == null ? "n" : t.peakK)
                + "|" + (t.endsetK == null ? "n" : t.endsetK)
                + "|" + t.endothermic + "|" + t.kind.code
                + "|" + t.sourceVersions);
    }
}
