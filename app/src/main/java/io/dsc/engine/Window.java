package io.dsc.engine;

import io.dsc.model.Segment;

import java.util.ArrayList;
import java.util.List;

/**
 * Selects plottable samples of one segment inside a calibrated-temperature
 * window. Heating and cooling are both handled by coordinate ordering; callers
 * must never pass a window spanning two segments.
 */
public record Window(double fromK, double toK) {

    public List<Segment.Sample> select(Segment segment) {
        List<Segment.Sample> out = new ArrayList<>();
        double lo = Math.min(fromK, toK);
        double hi = Math.max(fromK, toK);
        for (Segment.Sample s : segment.samples()) {
            if (s.plottable() && s.tK() >= lo && s.tK() <= hi) out.add(s);
        }
        return out;
    }
}
