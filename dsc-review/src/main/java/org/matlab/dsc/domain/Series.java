package org.matlab.dsc.domain;

import java.util.List;

/**
 * Paired sampled arrays with an explicit coordinate frame. Temperature is in
 * kelvin on the derived layer; heat flow in milliwatts before normalisation
 * and mW/mg after. Index grids may be non-uniform on purpose.
 */
public record Series(List<Double> t, List<Double> y, List<Integer> rawIndex) {
    public int size() {
        return t.size();
    }

    public static Series of(double[] t, double[] y) {
        return new Series(
                java.util.Arrays.stream(t).boxed().toList(),
                java.util.Arrays.stream(y).boxed().toList(),
                java.util.stream.IntStream.range(0, t.length).boxed().toList());
    }
}
