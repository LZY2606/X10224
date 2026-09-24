package dsc.rules;

import java.util.List;

/**
 * 并列基线候选。多个合理基线必须同时保留差异，不由系统吞成单一答案。
 *
 * @param candidateId 稳定身份（interval + 方法）
 * @param method      TANGENT_LINE / SIGMOID_STEP / PIECEWISE_OVERLAP
 * @param origin      AUTO / USER_TANGENT
 * @param line        [a,b] 使 y_base = a*T+b（piecewise 为各段系数序列，平铺）
 * @param knots       分段点（K）；线性/玻璃为 [t0,t1]
 * @param residualRms 曲线与基线残差 RMS（W/g），用于并列比较
 * @param rationale   选择依据/不确定说明
 */
public record BaselineCandidate(String candidateId, String method, String origin,
                                List<Double> line, List<Double> knots,
                                double residualRms, String rationale) {
}
