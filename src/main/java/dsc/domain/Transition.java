package dsc.domain;

import java.util.List;

/**
 * 相变/转折结果：onset / peak / endset 与比焓（J/g，熔化/吸热恒为正）。
 * 每个显示值携带稳定身份与误差来源。
 */
public record Transition(
        String id,
        String windowId,
        String baselineId,
        String kind,                   // glass / crystallization / melting / unknown
        double onsetC,
        double peakC,
        double endsetC,
        double enthalpyJPerG,
        List<ErrorSource> errorSources
) {}
