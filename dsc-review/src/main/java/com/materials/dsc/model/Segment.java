package com.materials.dsc.model;

import java.util.ArrayList;
import java.util.List;

/**
 * A scan segment inside one sample history: one ramp of one programmed
 * direction (heat1, cool1, heat2 ...).  Direction comes from instrument
 * metadata / temperature trend, never from the file name.
 */
public final class Segment {
    public final String id;
    public final String label;
    public final String cycle;
    public final String directionProgrammed;
    public final String signConventionRaw;
    public final List<Point> points = new ArrayList<>();
    public String resolvedDirection;
    public String directionBasis;
    public String directionDiagnostic;
    public boolean heatFlowReversed;
    public boolean nonUniformSpacing;

    public Segment(String id, String label, String cycle,
                   String directionProgrammed, String signConventionRaw) {
        this.id = id;
        this.label = label;
        this.cycle = cycle;
        this.directionProgrammed = directionProgrammed;
        this.signConventionRaw = signConventionRaw;
    }
}
