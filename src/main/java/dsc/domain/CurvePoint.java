package dsc.domain;

/**
 * One raw instrument sample. time/temperature/heatFlow keep original units.
 * Non-uniform sampling is allowed; integration never assumes a constant step.
 */
public record CurvePoint(
        int ord,
        double timeRaw,
        String timeUnit,
        double tempRaw,
        String tempUnit,
        double heatFlowRaw,
        String heatFlowUnit,
        boolean flagged) {}
