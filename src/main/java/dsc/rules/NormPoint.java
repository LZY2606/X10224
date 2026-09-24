package dsc.rules;

/** 规范化曲线点：规范坐标 K 与比热流 W/g；保留是否排除标记。 */
public record NormPoint(double tK, double hfPerMass, boolean excluded) {
}
