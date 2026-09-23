package dsc.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A sample history (履历): heating and cooling DSC sequences (cycles) live under one sample.
 * Mass and atmosphere are sample-level; instrument outputs, scan rates and temperature
 * calibration are segment-level; tangent selections are interval-level.
 */
public final class Sample {
    public final String id;
    public final String name;
    public final String material;
    public final List<Segment> segments = new ArrayList<>();

    public Sample(String id, String name, String material) {
        this.id = id;
        this.name = name;
        this.material = material;
    }

    public List<Segment> segmentsView() {
        return Collections.unmodifiableList(segments);
    }
}
