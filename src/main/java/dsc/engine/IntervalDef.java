package dsc.engine;

/** 当前区间定义（拖动后产生新版本基线，区间身份保持稳定）。 */
public record IntervalDef(String intervalId, String segmentId, String transitionId,
                          double t0K, double t1K, String hint) {
}
