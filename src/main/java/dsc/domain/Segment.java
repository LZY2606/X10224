package dsc.domain;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.ArrayList;
import java.util.List;

/**
 * One heating or cooling run inside a single sample history.
 * A segment owns its instrument output, rate, atmosphere, calibration and tangent sources.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public final class Segment {
    public String id;
    public String cycleNo;
    public ScanDirection direction;
    public String directionEvidence;
    public String filenameHint;
    public String label;
    public final List<RawPoint> points = new ArrayList<>();

    public Segment() {
    }

    public Segment(String id, String cycleNo, ScanDirection direction,
                   String directionEvidence, String filenameHint, String label) {
        this.id = id;
        this.cycleNo = cycleNo;
        this.direction = direction;
        this.directionEvidence = directionEvidence;
        this.filenameHint = filenameHint;
        this.label = label;
    }
}
