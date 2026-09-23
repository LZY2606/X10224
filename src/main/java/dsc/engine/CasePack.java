package dsc.engine;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * In-memory representation of the offline case pack
 * "热转折案：DSC 基线、相变积分与扫描历史审阅器".
 *
 * <p>A case pack is domain material, not a generic spreadsheet: it describes
 * a sample history with its thermal cycle, archives every direct source
 * separately (instrument output, mass, scan rate, atmosphere, temperature
 * calibration, reviewer tangents), and ships a machine-readable replay
 * script that walks through the seven boundary situations.
 */
public record CasePack(
        String version,
        Map<String, String> meta,
        List<SegmentSpec> segments,
        List<MassSpec> masses,
        List<ReplayStep> replay
) {

    public record SegmentSpec(
            String id,
            int order,
            String cycle,
            String traceFile,
            double programmedRate,
            String rateUnit,
            String atmosphere,
            double calOffset,
            String calUnit,
            boolean declaredReversePolarity,
            DirectionHint fileNameHint,
            List<TangentSpec> tangents
    ) {}

    public enum DirectionHint { HEATING, COOLING, NONE }

    public record TangentSpec(String segmentId, double leftLoC, double leftHiC,
                              double rightLoC, double rightHiC, double windowLeftC,
                              double windowRightC) {}

    public record MassSpec(String id, double value, String unit, String recordedAt,
                           String recordedBy, String note) {}

    /** One machine-readable replay step. {@code params} is free-form JSON data. */
    public record ReplayStep(String action, String target, String actor,
                             Map<String, Object> params, String expect) {
        public ReplayStep with(Map<String, Object> p) {
            return new ReplayStep(action, target, actor, p, expect);
        }
    }

    public String meta(String key) {
        return meta.getOrDefault(key, "");
    }
}
