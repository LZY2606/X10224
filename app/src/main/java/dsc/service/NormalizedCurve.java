package dsc.service;

import java.util.List;

/** Result of the normalization derivation layer (derived, not a source). */
public class NormalizedCurve {
    public double[] tC;
    public double[] hfWg;
    public boolean signFlipped;        // EXO_DOWN was flipped into the EXO_UP working frame
    public List<Integer> badPoints;    // points that could not be converted (kept, not dropped)
    public double massMg;
    public String massUnit;
    public boolean massKnown;
}
