package io.dsc.engine;

import io.dsc.model.DiagnosticCodes;
import io.dsc.model.Interval;
import io.dsc.model.Segment;

import java.util.List;

/**
 * Geometry rules for dragged intervals.
 *  - empty window (no plottable points) -> EMPTY, derivation refused
 *  - window crossing into another segment / a cycle switch -> invalid, never mixed
 */
public final class IntervalValidator {
    private IntervalValidator() {}

    public static class Result {
        public final Interval.Status status;
        public final String diagnosticCode;
        public final int pointCount;
        Result(Interval.Status status, String code, int pointCount) {
            this.status = status;
            this.diagnosticCode = code;
            this.pointCount = pointCount;
        }
        public boolean derivable() { return status == Interval.Status.VALID; }
    }

    public Result validate(Interval interval, Segment segment, List<Segment> allSegments) {
        if (segment == null || !segment.id().value().equals(interval.segmentId())) {
            return new Result(Interval.Status.CROSSES_SEGMENT,
                    DiagnosticCodes.CROSSES_SEGMENT_BOUNDARY, 0);
        }
        // cycle-switch mixing: another segment exists in the coordinate span
        for (Segment other : allSegments) {
            if (other.id().value().equals(segment.id().value())) continue;
            if (directionOverlap(other, interval)) {
                return new Result(Interval.Status.CROSSES_SEGMENT,
                        DiagnosticCodes.CYCLE_SWITCH_NOT_MIXED, 0);
            }
        }
        List<Segment.Sample> pts = new Window(interval.fromK(), interval.toK()).select(segment);
        if (pts.isEmpty()) {
            return new Result(Interval.Status.EMPTY, DiagnosticCodes.EMPTY_INTERVAL, 0);
        }
        return new Result(Interval.Status.VALID, null, pts.size());
    }

    private boolean directionOverlap(Segment other, Interval interval) {
        if (other.samples().isEmpty()) return false;
        double lo = Double.POSITIVE_INFINITY, hi = Double.NEGATIVE_INFINITY;
        for (Segment.Sample s : other.samples()) {
            lo = Math.min(lo, s.tK());
            hi = Math.max(hi, s.tK());
        }
        double ivLo = Math.min(interval.fromK(), interval.toK());
        double ivHi = Math.max(interval.fromK(), interval.toK());
        return ivHi > lo && ivLo < hi && other.direction() != directionOf(interval);
    }

    private io.dsc.model.ScanDirection directionOf(Interval interval) {
        return interval.fromK() <= interval.toK()
                ? io.dsc.model.ScanDirection.HEATING
                : io.dsc.model.ScanDirection.COOLING;
    }
}
