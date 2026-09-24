package org.materials.dsc.domain;

import java.util.ArrayList;
import java.util.List;

/**
 * A sample and its full thermal履历: heating and cooling segments live under
 * the same history, in explicit order, and retain their cycle identity.
 */
public final class SampleHistory {
    public String id;
    public String code;
    public String material;
    public String createdAt;
    public final List<Segment> segments = new ArrayList<>();

    public SampleHistory() {
    }

    public SampleHistory(String id, String code, String material, String createdAt) {
        this.id = id;
        this.code = code;
        this.material = material;
        this.createdAt = createdAt;
    }
}
