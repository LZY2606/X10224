package dsc.engine;

import java.util.List;

/**
 * Normalised (derived-layer) series in the canonical frame:
 * temperature in degC (calibration frame), heat flow in W/g with endothermic-up convention.
 * Cells that could not be converted are absent from {@code y} and reported via diagnostics;
 * the raw row representation stays in the archived segment.
 */
public record Series(String segmentId, double[] t, double[] y, double[] xOrig,
                     List<Integer> unconvertedRows, boolean ascendingTemp,
                     double uniformStep, boolean nonUniform) {

    public int size() {
        return t.length;
    }
}
