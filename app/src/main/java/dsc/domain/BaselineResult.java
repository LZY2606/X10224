package dsc.domain;

import java.util.ArrayList;
import java.util.List;

/** Numeric outcome of one baseline+transition analysis over one interval. */
public class BaselineResult {
    public String method;
    public double a, b;                 // baseline hf = a + b*T
    public double onsetC = Double.NaN;
    public double peakC = Double.NaN;
    public double endsetC = Double.NaN;
    public double onsetHfWg = Double.NaN;
    public double peakHfWg = Double.NaN;
    public double endsetHfWg = Double.NaN;
    public double deltaH = Double.NaN;  // J/g, signed in the EXO_UP-normalized frame
    public double areaWgC = Double.NaN;
    public double tgMidC = Double.NaN;  // glass transition midpoint
    public boolean isGlass;
    public boolean reversed;           // curve ran high->low temperature inside the interval
    public boolean nonUniform;
    public boolean empty;
    public boolean crossesSegments;
    public List<Integer> peakIdx = new ArrayList<>();
    public List<Double> subPeakC = new ArrayList<>();
    public boolean overlapping;        // >=2 extrema of the same sign above prominence
    public List<Enums.PolicyRule> fired = new ArrayList<>();

    public boolean usable() {
        return !empty && !crossesSegments && !Double.isNaN(deltaH);
    }
}
