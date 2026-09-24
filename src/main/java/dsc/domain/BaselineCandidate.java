package dsc.domain;

import java.util.List;

/**
 * 一条基线候选。多种合理基线（线性端点 / 切线台阶 / 多项式）必须并列保留差异，
 * 一次确认只是选择 current，不删除其它候选。
 */
public record BaselineCandidate(
        String id,
        String windowId,
        String method,                 // linear_endpoints / tangent_step / polynomial2
        double[] tempC,                // 与窗口内规范化点一一对应
        double[] hfMw,
        List<String> diagnostics,
        double quality,                // 简单拟合质量（残差 RMS），越大越差
        boolean admissible,
        String basedOnTangentId
) {}
