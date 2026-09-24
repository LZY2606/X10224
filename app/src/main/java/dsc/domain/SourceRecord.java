package dsc.domain;

import java.util.ArrayList;
import java.util.List;

/**
 * A separately archived direct source: instrument output, sample mass, scan rate, atmosphere,
 * temperature calibration or a user-picked tangent. Each edit appends a new version; old rows stay.
 */
public class SourceRecord {
    public String id;          // stable across versions (identity of the logical source)
    public String sampleId;
    public String segmentId;   // null for sample-level sources (mass, atmosphere)
    public Enums.SourceKind kind;
    public int version;
    public boolean current;    // current-pointer; only one current per (id)
    public String rawText;     // original representation, preserved verbatim
    public String unit;        // declared unit (may be unknown -> diagnostic)
    public double numericValue;
    public boolean numericValid;
    public String payloadJson; // structured details (file name hash, gas, flow, calibration refs...)
    public String originRef;   // file/path/operator the source came from (not used to infer signs)
    public long createdAt;
    public String createdBy;
    public String supersedesId;
    public List<String> tags = new ArrayList<>();

    public String key() { return id + ":v" + version; }
}
