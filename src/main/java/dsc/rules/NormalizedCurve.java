package dsc.rules;

import java.util.List;

/**
 * 归一化结果。status=INVALID 时 points 仍保留（可能为空或部分），
 * 诊断记录无法转换、反向热流方向等问题。
 */
public record NormalizedCurve(String segmentId,
                              String direction,
                              String signBasis,
                              String signStatus,
                              double massG,
                              String massVersion,
                              List<NormPoint> points,
                              List<String> diagnostics) {

    public boolean invalid() {
        return "INVALID".equals(signStatus) || massG <= 0;
    }
}
