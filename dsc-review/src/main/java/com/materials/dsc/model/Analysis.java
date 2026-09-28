package com.materials.dsc.model;

import java.util.ArrayList;
import java.util.List;

/**
 * One analysis attached to one segment and one draggable temperature range.
 */
public final class Analysis {
    public final String id;
    public final String segmentId;
    public final String rangeId;
    public double t0, t1;
    public final List<BaselineCandidate> candidates = new ArrayList<>();
    public final List<Transition> transitions = new ArrayList<>();
    public String status;
    public String rangeDiagnostic;

    public Analysis(String id, String segmentId, String rangeId, double t0, double t1, String status) {
        this.id = id;
        this.segmentId = segmentId;
        this.rangeId = rangeId;
        this.t0 = t0;
        this.t1 = t1;
        this.status = status;
    }
}
