package dsc.domain;

/**
 * 吸放热方向约定。
 *
 * 核心规则：吸放热方向不能靠文件名猜测。仪器输出头里的方向标记（或仪器型号手册）
 * 是唯一合法来源；来源缺失时进入 UNRESOLVED，相关峰方向与焓符号标记为不确定，
 * 不允许用文件名/段名/向上向下的视觉猜测来补齐。
 *
 * 内部统一约定：yWg &gt; 0 = 吸热（endotherm up）。仪器约定若为“放热向上”
 * （exo up，常见于部分 TA/Setaram 输出），导入时翻转符号。
 */
public enum DirectionConvention {
    /** 仪器输出：吸热向上，内部直接采用。 */
    ENDO_UP("endo-up"),
    /** 仪器输出：放热向上，导入翻转。 */
    EXO_UP("exo-up"),
    /** 方向来源缺失/冲突，方向不可唯一决定。 */
    UNRESOLVED("unresolved");

    public final String code;

    DirectionConvention(String code) {
        this.code = code;
    }

    public static DirectionConvention fromInstrumentHeader(String headerValue, String instrumentModelHint) {
        if (headerValue == null) {
            return UNRESOLVED;
        }
        String v = headerValue.trim().toLowerCase();
        if (v.contains("endo") && v.contains("up")) {
            return ENDO_UP;
        }
        if (v.contains("exo") && v.contains("up")) {
            return EXO_UP;
        }
        if (v.equals("endo+") || v.equals("endotherm_positive")) {
            return ENDO_UP;
        }
        if (v.equals("exo+") || v.equals("exotherm_positive")) {
            return EXO_UP;
        }
        // 注意：绝不根据文件名推断；型号手册提示只能作为诊断线索，仍需头标记确认。
        return UNRESOLVED;
    }

    /** 从仪器原始 y 值映射到内部约定（吸热为正）。 */
    public double toInternal(double rawHeatFlow) {
        return this == EXO_UP ? -rawHeatFlow : rawHeatFlow;
    }
}
