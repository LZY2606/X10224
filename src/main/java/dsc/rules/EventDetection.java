package dsc.rules;

import dsc.model.BaselineCandidate;
import dsc.model.Direction;
import dsc.model.SamplePoint;

import java.util.List;

/**
 * Detects onset / peak / endset of an event inside the integration window.
 *
 * <p>The peak is the extremum of the baseline-subtracted signal; its sign
 * is decided by event kind and segment direction (DH-SIGN-1), not by file
 * name. Onset is the first point inside the window whose deviation crosses
 * the noise threshold; endset is the last.
 */
public final class EventDetection {

    private EventDetection() {}

    public record EventPoints(double onsetTK, double peakTK, double endsetTK,
                              double peakDeviationW) {}

    /**
     * @param endothermicPeakUp true if, after polarity normalisation, an
     *                          endothermic peak points "up" in the working
     *                          convention
     */
    public static EventPoints detect(List<SamplePoint> points, BaselineCandidate bl,
                                     boolean reversePolarity, boolean endothermic,
                                     double noiseW, Double meetForPiecewise) {
        double pol = reversePolarity ? -1 : 1;
        double onset = Double.NaN;
        double endset = Double.NaN;
        double peakT = Double.NaN;
        double peakDev = 0;
        // Deviation sign: endothermic signal should be positive when present.
        // On cooling legs crystallisation is exothermic and T decreases, but
        // the working signal is already polarity-normalised; endothermic
        // events seek +max, exothermic seek -max.
        double best = endothermic ? Double.NEGATIVE_INFINITY : Double.POSITIVE_INFINITY;

        SamplePoint prev = null;
        for (SamplePoint p : points) {
            if (!p.parseable()) continue;
            double t = p.tK();
            if (t < bl.leftTK() - 1e-9 || t > bl.rightTK() + 1e-9) continue;
            double base = Integrator.baselinePiecewise(bl, t, meetForPiecewise);
            double dev = pol * p.hfW() - base;
            boolean over = endothermic ? dev > noiseW : dev < -noiseW;
            if (over) {
                if (Double.isNaN(onset)) onset = t;
                endset = t;
                if (endothermic ? dev > best : dev < best) {
                    best = dev;
                    peakT = t;
                    peakDev = dev;
                }
            }
            prev = p;
        }
        if (Double.isNaN(onset)) {
            // No crossing: fall back to the window edges so the empty-window
            // diagnostic remains the single authority for "no event".
            return new EventPoints(bl.leftTK(), Double.NaN, bl.rightTK(), 0);
        }
        return new EventPoints(onset, peakT, endset, peakDev);
    }

    /** Find the piecewise meeting T stored on a candidate, if any. */
    public static Double meetOf(BaselineCandidate bl) {
        for (String d : bl.diagnostics()) {
            if (d.startsWith("{") && d.contains("meetT_K")) {
                String needle = "\"meetT_K\":";
                int i = d.indexOf(needle);
                int j = i + needle.length();
                int k = j;
                while (k < d.length() && "0123456789+-.eE".indexOf(d.charAt(k)) >= 0) k++;
                return Double.parseDouble(d.substring(j, k));
            }
        }
        return null;
    }

    /**
     * Classify the kind of transition from direction and peak sense.
     * Heating: up = melting/glass, down = crystallisation (cold
     * crystallisation). Cooling: down = crystallisation.
     */
    public static String classify(Direction direction, boolean endothermic,
                                  boolean stepLike) {
        if (stepLike) return dsc.model.Transition.Kind.GLASS.name();
        if (direction == Direction.HEATING) {
            return endothermic ? dsc.model.Transition.Kind.MELTING.name()
                    : dsc.model.Transition.Kind.CRYSTALLISATION.name();
        }
        if (direction == Direction.COOLING) {
            return endothermic ? dsc.model.Transition.Kind.UNRESOLVED.name()
                    : dsc.model.Transition.Kind.CRYSTALLISATION.name();
        }
        return dsc.model.Transition.Kind.UNRESOLVED.name();
    }
}
