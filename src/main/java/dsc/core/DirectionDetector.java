package dsc.core;

import dsc.domain.Direction;
import dsc.domain.RawPoint;

import java.util.List;

/**
 * Derives heating/cooling direction from measured temperature versus time.
 * The declared direction from the file name or header is never trusted on its
 * own; a contradiction is reported instead of silently renaming the segment.
 */
public final class DirectionDetector {

    public record Result(Direction measured, double slopeKPerS, double r2, int points, boolean contradictsDeclared) {
        public boolean confident() {
            return points >= 3 && r2 >= 0.5 && Math.abs(slopeKPerS) > 1e-9;
        }
    }

    private DirectionDetector() {
    }

    public static Result detect(List<RawPoint> points, Direction declared) {
        int n = points.size();
        double[] t = new double[n];
        double[] temp = new double[n];
        int count = 0;
        for (RawPoint p : points) {
            if (!p.computable()) {
                continue;
            }
            t[count] = p.canonicalTimeS();
            temp[count] = p.canonicalTempK();
            count++;
        }
        if (count < 3) {
            return new Result(Direction.UNKNOWN, 0, 0, count, false);
        }
        LinearFit fit = LinearFit.fit(t, temp, 0, count - 1);
        Direction measured = Math.abs(fit.slope) <= 1e-9 ? Direction.UNKNOWN
                : fit.slope > 0 ? Direction.HEATING : Direction.COOLING;
        boolean contradiction = declared != null && declared != Direction.UNKNOWN
                && measured != Direction.UNKNOWN && measured != declared;
        return new Result(measured, fit.slope, fit.r2, count, contradiction);
    }
}
