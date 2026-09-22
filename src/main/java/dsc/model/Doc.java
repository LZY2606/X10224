package dsc.model;

import com.google.gson.JsonObject;

/**
 * Versioned document envelope.
 *
 * <p>{@code id} is a stable identity assigned once and preserved through export/import.
 * {@code version} is a deterministic content hash of ({@code kind}, {@code lineage},
 * {@code baseVersion}, {@code data}) so the same replay always yields the same versions.
 * A lineage holds all revisions of one logical object; a pointer (in events) selects
 * the current revision. Old revisions are never overwritten.
 */
public final class Doc {
    public final String id;
    public final String lineage;
    public final String kind;
    public final long seq;
    public final String baseVersion;
    public final String version;
    public final JsonObject data;

    public Doc(String id, String lineage, String kind, long seq, String baseVersion,
               String version, JsonObject data) {
        this.id = id;
        this.lineage = lineage;
        this.kind = kind;
        this.seq = seq;
        this.baseVersion = baseVersion;
        this.version = version;
        this.data = data;
    }

    public JsonObject toJson() {
        JsonObject o = J.obj();
        J.put(o, "id", id);
        J.put(o, "lineage", lineage);
        J.put(o, "kind", kind);
        J.put(o, "seq", seq);
        J.put(o, "baseVersion", baseVersion == null ? "" : baseVersion);
        J.put(o, "version", version);
        J.put(o, "data", data);
        return o;
    }

    public static Doc fromJson(JsonObject o) {
        return new Doc(
                o.get("id").getAsString(),
                o.get("lineage").getAsString(),
                o.get("kind").getAsString(),
                o.get("seq").getAsLong(),
                J.str(o, "baseVersion", ""),
                o.get("version").getAsString(),
                o.getAsJsonObject("data"));
    }
}
