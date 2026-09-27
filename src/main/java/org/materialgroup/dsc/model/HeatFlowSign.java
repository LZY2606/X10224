package org.materialgroup.dsc.model;

/**
 * 热流轴的记录方向。DSC 仪器的“吸热向上/向下”并不统一，
 * 必须来自仪器输出或显式的仪器取向信息，不能从文件名猜测。
 */
public enum HeatFlowSign {
    /** 轴上正向 = 吸热（endotherm up）。 */
    ENDOTHERM_UP,
    /** 轴上正向 = 放热（exotherm up，即吸热向下）。 */
    EXOTHERM_UP,
    /** 仪器输出没有声明方向，禁止据此判定吸放热。 */
    UNKNOWN;

    /**
     * 以“吸热为正”的内部分析基准，解释原始轴上数值的符号。
     *
     * @return +1 表示原始正向即吸热；-1 表示原始正向为放热；0 表示方向未知
     */
    public int endoPositiveFactor() {
        return switch (this) {
            case ENDOTHERM_UP -> 1;
            case EXOTHERM_UP -> -1;
            case UNKNOWN -> 0;
        };
    }
}
