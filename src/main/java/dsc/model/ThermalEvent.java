package dsc.model;

import java.util.List;

/**
 * 热事件（玻璃化/结晶/熔融）。方向由符号约定判定，不由文件名。
 * errorSources: 机器可读的焓变误差来源。
 */
public record ThermalEvent(
        String id,
        String regionId,
        int version,
        String eventType,       // GLASS_TRANSITION | CRYSTALLIZATION | MELTING | UNKNOWN
        String direction,       // ENDOTHERMIC | EXOTHERMIC | UNKNOWN
        double onsetT, double peakT, double endsetT,
        Double enthalpyJg,      // null 表示无法给出（如空区间）
        List<ErrSrc> errorSources,
        String baselineId,
        String status           // valid | stale | diagnostic
) {
    public record ErrSrc(String source, double absJPerG) {}
}
