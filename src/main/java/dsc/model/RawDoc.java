package dsc.model;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * One archived source: instrument output, sample mass, scan rate, atmosphere,
 * temperature calibration or a user-selected tangent. Each kind is stored
 * separately, versioned, and keeps its original (raw) representation.
 */
public final class RawDoc {
    public enum Kind {
        INSTRUMENT_OUTPUT,
        SAMPLE_MASS,
        SCAN_RATE,
        ATMOSPHERE,
        TEMP_CALIBRATION,
        USER_TANGENT
    }

    public final String id;
    public final int version;
    public final Kind kind;
    public final String raw;
    public final Map<String, String> meta;
    public final String hash;
    public final String createdAt;

    public RawDoc(String id, int version, Kind kind, String raw,
                  Map<String, String> meta, String hash, String createdAt) {
        this.id = id;
        this.version = version;
        this.kind = kind;
        this.raw = raw;
        this.meta = meta == null ? Map.of() : new LinkedHashMap<>(meta);
        this.hash = hash;
        this.createdAt = createdAt;
    }

    public String ref() {
        return id + "@v" + version;
    }
}
