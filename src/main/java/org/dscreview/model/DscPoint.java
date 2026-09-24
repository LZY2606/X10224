package org.dscreview.model;

/**
 * One raw instrument sample. Raw strings are preserved verbatim from the
 * instrument output; parsed/NaN values carry diagnostics instead of being dropped.
 */
public record DscPoint(
        int index,
        double timeS,
        Quant temperature,
        Quant heatFlow,
        boolean usable,
        String diagnostic) {
}
