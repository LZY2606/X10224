package org.materials.dsc.domain;

/**
 * 吸放热方向约定。规范约定：endothermic（吸热，如熔融）为正热流。
 * 仪器若声明 exoUp（放热朝上），原始信号必须整体取负——方向只来自仪器头元数据。
 * 焓 ΔH = ∫ canonicalHeatFlow dt / mass(g)：熔融 > 0，结晶 < 0，
 * 与加热/冷却段无关，也不从文件名推断。
 */
public final class SignConvention {

    public static final String ENDOTHERMIC_UP = "endo-up";
    public static final String EXOTHERMIC_UP = "exo-up";

    private SignConvention() {
    }

    /** 把原始 mW 信号换算到“吸热为正”的规范热流。 */
    public static double canonicalHeatFlowMilliwatt(double rawMilliwatt, boolean exoUp) {
        return exoUp ? -rawMilliwatt : rawMilliwatt;
    }

    /** 规范热流除以质量(mg)再乘 1000，得到 mW/g。 */
    public static double normalizeByMass(double canonicalMilliwatt, double massMilligram) {
        return canonicalMilliwatt / massMilligram * 1000.0;
    }

    /** 由规范焓符号判定事件性质。 */
    public static String eventKind(double deltaHJoulePerGram) {
        if (deltaHJoulePerGram > 0) {
            return "endothermic";
        }
        if (deltaHJoulePerGram < 0) {
            return "exothermic";
        }
        return "zero";
    }
}
