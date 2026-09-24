package dsc.event;

import com.fasterxml.jackson.databind.JsonNode;
import dsc.util.Json;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Objects;

/**
 * One immutable fact in the sample history. Events are the only source of truth; derived
 * calculations are cached projections and can always be rebuilt by replaying the event log.
 *
 * <ul>
 *   <li>{@code id} is content stable (export/delete/reimport preserves it).</li>
 *   <li>{@code seq} is assignment order within one case and is preserved on import.</li>
 *   <li>{@code prevHash} forms a tamper-evident chain; it is verified on load and after import.</li>
 *   <li>{@code ruleVersion} records which core-rule revision interpreted this fact.</li>
 *   <li>{@code basisVersion} is the state version the author observed; equal bases produce a
 *       resolvable optimistic-conflict event rather than a silent overwrite.</li>
 * </ul>
 */
public final class DscEvent {
    private String id;
    private long seq;
    private String type;
    private String actor;
    private String createdAt;
    private String ruleVersion;
    private long basisVersion;
    private JsonNode payload;
    private String prevHash;
    private String hash;

    public DscEvent() {
    }

    public DscEvent(long seq, String type, String actor, String ruleVersion,
                    long basisVersion, JsonNode payload, String prevHash) {
        this.seq = seq;
        this.type = Objects.requireNonNull(type);
        this.actor = actor == null ? "reviewer" : actor;
        this.createdAt = Instant.now().toString();
        this.ruleVersion = ruleVersion;
        this.basisVersion = basisVersion;
        this.payload = payload;
        this.prevHash = prevHash == null ? "" : prevHash;
        this.id = deriveId();
        this.hash = computeHash();
    }

    /** Content hash excluding the mutable {@code seq} slot? seq is part of ordering, hashed in. */
    private String computeHash() {
        String body = seq + "|" + type + "|" + actor + "|" + createdAt + "|" + ruleVersion
                + "|" + basisVersion + "|" + (payload == null ? "" : payload.toString())
                + "|" + prevHash;
        return sha256(body);
    }

    /**
     * Stable identity survives export/delete/reimport: derived from type + canonical payload +
     * creation timestamp + author. Timestamp is authored at creation and stored, so replay keeps
     * the same id instead of regenerating it.
     */
    private String deriveId() {
        String body = type + "|" + actor + "|" + createdAt + "|"
                + (payload == null ? "" : canonicalPayload());
        return "ev_" + sha256(body).substring(0, 16);
    }

    private String canonicalPayload() {
        try {
            Object canon = Json.MAPPER.treeToValue(payload, Object.class);
            return Json.MAPPER.writeValueAsString(canon);
        } catch (Exception e) {
            return payload.toString();
        }
    }

    public static String sha256(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] out = digest.digest(text.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(out);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public boolean hashValid() {
        return Objects.equals(hash, computeHash()) && Objects.equals(id, deriveId());
    }

    public String id() { return id; }
    public long seq() { return seq; }
    public String type() { return type; }
    public String actor() { return actor; }
    public String createdAt() { return createdAt; }
    public String ruleVersion() { return ruleVersion; }
    public long basisVersion() { return basisVersion; }
    public JsonNode payload() { return payload; }
    public String prevHash() { return prevHash; }
    public String hash() { return hash; }

    public void setId(String id) { this.id = id; }
    public void setSeq(long seq) { this.seq = seq; }
    public void setType(String type) { this.type = type; }
    public void setActor(String actor) { this.actor = actor; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
    public void setRuleVersion(String ruleVersion) { this.ruleVersion = ruleVersion; }
    public void setBasisVersion(long basisVersion) { this.basisVersion = basisVersion; }
    public void setPayload(JsonNode payload) { this.payload = payload; }
    public void setPrevHash(String prevHash) { this.prevHash = prevHash; }
    public void setHash(String hash) { this.hash = hash; }
}
