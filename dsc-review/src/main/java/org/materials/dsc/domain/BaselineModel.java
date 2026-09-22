package org.materials.dsc.domain;

import java.util.List;

/** 基线模型：对积分窗口内的规范化热流给出一条候选基线。 */
public interface BaselineModel {

    String key();

    String label();

    /**
     * @param windowX 窗口内温度（K）
     * @param windowY 窗口内规范化热流（mW/g）
     * @param context 左/右切线窗口数据（可能为空）
     */
    Fit fit(List<Double> windowX, List<Double> windowY, TangentContext context);

    record Fit(List<Double> y, double residualRms, List<String> diagnostics) {
    }

    record TangentContext(List<double[]> leftPoints, List<double[]> rightPoints) {
        public static TangentContext empty() {
            return new TangentContext(List.of(), List.of());
        }
    }
}
