package dsc.domain;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

/**
 * Stable content identities for derived results. A result id is a hash of
 * everything that can change the answer: segment, interval, baseline
 * coefficients, event type, rule version and the mass version used for
 * normalisation. Therefore the id survives export/delete/import (it does not
 * depend on a database rowid), and a changed dependency produces a new id —
 * old results stay queryable as history instead of being overwritten.
 */
final class TransitionIds {

    private TransitionIds() {
    }

    static String hash(String... parts) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            for (String p : parts) {
                md.update((p == null ? "\u0000" : p).getBytes(StandardCharsets.UTF_8));
                md.update((byte) 0x1f);
            }
            return HexFormat.of().formatHex(md.digest()).substring(0, 16);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    static TransitionResult result(String segmentId, Interval interval, TransitionType type,
                                   BaselineFit baseline, Double onset, Double peak, Double endset,
                                   Double peakHF, Double areaMJ, Double enthalpy,
                                   List<UncertaintySource> uncertainty,
                                   List<Diagnostic> diagnostics, String massVersion) {
        String baseKey = baseline == null ? "no-baseline"
                : baseline.model() + "|" + baseline.left().a() + "|" + baseline.left().b()
                + "|" + (baseline.right() == null ? "" : baseline.right().a() + "," + baseline.right().b())
                + "|" + baseline.stepTemperatureK();
        String id = hash("result", segmentId,
                fmt(interval.startK()) + ":" + fmt(interval.endK()),
                String.valueOf(type), baseKey, DscAnalyzer.RULE_VERSION,
                String.valueOf(massVersion));
        return new TransitionResult(id, segmentId, interval, type, baseline, onset, peak, endset,
                peakHF, areaMJ, enthalpy, uncertainty, diagnostics, DscAnalyzer.RULE_VERSION,
                massVersion);
    }

    private static String fmt(double v) {
        return Double.toString(v);
    }
}
