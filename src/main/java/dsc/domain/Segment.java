package dsc.domain;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * One heating or cooling run inside a sample history. A segment belongs to a
 * thermal cycle and its measured direction is derived from the data; the
 * declared direction from the instrument file is only corroboration.
 */
public final class Segment {
    private final String id;
    private final String label;
    private final String sourceFile;
    private final int cycleIndex;
    private final Direction declaredDirection;
    private final List<RawPoint> points;

    public Segment(String id, String label, String sourceFile, int cycleIndex,
                   Direction declaredDirection, List<RawPoint> points) {
        this.id = id;
        this.label = label;
        this.sourceFile = sourceFile;
        this.cycleIndex = cycleIndex;
        this.declaredDirection = declaredDirection;
        this.points = Collections.unmodifiableList(new ArrayList<>(points));
    }

    public String id() {
        return id;
    }

    public String label() {
        return label;
    }

    public String sourceFile() {
        return sourceFile;
    }

    public int cycleIndex() {
        return cycleIndex;
    }

    public Direction declaredDirection() {
        return declaredDirection;
    }

    public List<RawPoint> points() {
        return points;
    }
}
