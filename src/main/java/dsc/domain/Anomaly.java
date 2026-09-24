package dsc.domain;

/**
 * 异常/边界情形：输入片段 + 判定状态 + 受影响对象。
 * code 为机器可读标识（reverse_heatflow / nonuniform_temp / overlapping_transitions /
 * cycle_switch / mass_version_changed / parallel_baselines / empty_interval）。
 */
public record Anomaly(
        String code,
        String severity,       // info / warning / error
        String status,         // detected / blocked / retained
        String affectedKind,
        String affectedId,
        String message
) {}
