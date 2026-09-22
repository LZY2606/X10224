package dsc.rules;

import com.fasterxml.jackson.databind.JsonNode;
import dsc.domain.Derivation;
import dsc.domain.RawPoint;
import dsc.domain.SourceKind;
import dsc.domain.SourceRecord;
import dsc.domain.TangentSpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Deterministic fingerprints of derivation inputs.
 *
 * A derivation stays valid while the fingerprint of every direct input is unchanged.
 * Fingerprints reference (sourceKind, sourceId, version) plus the actual content hash, so
 * a new mass version, a tangent revision, a calibration change or an instrument-output
 * correction each invalidate exactly the derivations that consume them.
 */
public final class Fingerprints {

    private Fingerprints() {
    }

    public static String ref(SourceRecord s) {
        return s.kind.code + ":" + s.id + "@v" + s.version;
    }

    public static String ref(TangentSpec t) {
        return "tangent:" + t.id + "@v" + t.version;
    }

    public static String sha256(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] d = md.digest(s.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : d) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16));
                sb.append(Character.forDigit(b & 0xF, 16));
            }
            return sb.substring(0, 16);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public static String rawContent(List<RawPoint> pts) {
        StringBuilder sb = new StringBuilder();
        for (RawPoint p : pts) {
            sb.append(p.index).append('|')
                    .append(p.temperature.raw).append(',')
                    .append(p.temperature.unit).append('|')
                    .append(p.heatFlow.raw).append(',')
                    .append(p.heatFlow.unit).append('|')
                    .append(p.time.raw).append(',')
                    .append(p.time.unit).append(';');
        }
        return sb.toString();
    }

    public static String sourceContent(SourceRecord s) {
        JsonNode p = s.payload;
        return s.kind.code + "|" + stableString(p == null ? "" : p.toString());
    }

    private static String stableString(String s) {
        return s.replaceAll("\\s+", "");
    }

    /**
     * Fingerprint of the full input set for a segment-level numeric derivation.
     * The mass source is included deliberately: a mass revision must expire ALL normalized
     * derivations even when the instrument output already looks specific (policy).
     */
    public static String segmentInputs(Map<SourceKind, SourceRecord> current,
                                       List<RawPoint> raw, String polarityHint) {
        List<String> refs = new ArrayList<>();
        for (SourceKind k : new SourceKind[]{
                SourceKind.INSTRUMENT_OUTPUT, SourceKind.SAMPLE_MASS,
                SourceKind.SCAN_RATE, SourceKind.ATMOSPHERE,
                SourceKind.TEMPERATURE_CALIBRATION}) {
            SourceRecord s = current.get(k);
            if (s != null) {
                refs.add(ref(s) + ":" + sha256(sourceContent(s)));
            }
        }
        refs.add("raw:" + sha256(rawContent(raw)));
        if (polarityHint != null) {
            refs.add("pol:" + polarityHint);
        }
        Collections.sort(refs);
        return RuleVersion.VERSION + "|" + sha256(String.join("||", refs));
    }

    public static String withTangent(String baseFp, TangentSpec t) {
        return baseFp + "|" + (t == null ? "no-tangent" : ref(t));
    }

    public static String resultInputs(String baselineFp, String baselineId,
                                      SourceRecord mass) {
        return baselineFp + "|baseline:" + baselineId
                + "|mass:" + (mass == null ? "none" : ref(mass) + ":"
                + sha256(sourceContent(mass)));
    }

    public static boolean unchanged(Derivation d, String recomputedFp) {
        return d.inputsFingerprint != null && d.inputsFingerprint.equals(recomputedFp)
                && d.rulesetVersion != null
                && d.rulesetVersion.equals(RuleVersion.VERSION);
    }
}
