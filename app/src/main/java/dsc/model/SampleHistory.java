package dsc.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A sample history holds ordered heating AND cooling segments of the same sample,
 * plus separately archived metadata: mass versions, rate/atmosphere live on segments,
 * temperature calibrations and user tangent selections.
 */
public final class SampleHistory {
    private final String sampleId;
    private final String name;
    private final String material;
    private final List<RawTrace> segments = new ArrayList<>();
    private final List<MassRecord> masses = new ArrayList<>();
    private final List<TangentSelection> tangents = new ArrayList<>();
    private final Map<String, String> rawMetadata = new LinkedHashMap<>();

    public SampleHistory(String sampleId, String name, String material) {
        this.sampleId = sampleId;
        this.name = name;
        this.material = material;
    }

    public String sampleId() { return sampleId; }
    public String name() { return name; }
    public String material() { return material; }
    public List<RawTrace> segments() { return Collections.unmodifiableList(segments); }
    public List<MassRecord> masses() { return Collections.unmodifiableList(masses); }
    public List<TangentSelection> tangents() { return Collections.unmodifiableList(tangents); }
    public Map<String, String> rawMetadata() { return Collections.unmodifiableMap(rawMetadata); }

    public void addSegment(RawTrace t) { segments.add(t); }
    public void addMass(MassRecord m) { masses.add(m); }
    public void addTangent(TangentSelection t) { tangents.add(t); }
    public void putMetadata(String k, String v) { rawMetadata.put(k, v); }

    public RawTrace segment(String id) {
        for (RawTrace t : segments) {
            if (t.segmentId().equals(id)) {
                return t;
            }
        }
        return null;
    }

    public MassRecord latestMass() {
        return masses.isEmpty() ? null : masses.get(masses.size() - 1);
    }
}
