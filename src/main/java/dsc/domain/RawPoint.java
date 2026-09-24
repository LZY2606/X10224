package dsc.domain;

/**
 * 仪器曲线上的一个采样点。原始字符串与换算值同时保留；
 * 无法换算的点 excluded=true，但绝不从来源中删除。
 */
public record RawPoint(long seq, String tRaw, String hfRaw, Double tK, Double hfW,
                       boolean excluded, String diag) {
}
