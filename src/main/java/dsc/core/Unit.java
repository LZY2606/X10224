package dsc.core;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * 物理单位与量纲。原始导入保留表示；仅当注册表能转换时才进入派生规范化。
 * 无法转换的值留在原位置并附诊断（见 {@link #convert}）。
 */
public final class Unit {
    public enum Dim { TEMPERATURE, MASS, HEAT_FLOW, RATE, TIME, DIMENSIONLESS, HEAT, UNKNOWN }

    public static final Unit C = new Unit("°C", Dim.TEMPERATURE, 1.0, 0.0);
    public static final Unit K = new Unit("K", Dim.TEMPERATURE, 1.0, -273.15);
    public static final Unit F = new Unit("°F", Dim.TEMPERATURE, 5.0 / 9.0, -32.0 * 5.0 / 9.0);
    public static final Unit MG = new Unit("mg", Dim.MASS, 1.0, 0.0);
    public static final Unit G = new Unit("g", Dim.MASS, 1000.0, 0.0);
    public static final Unit UG = new Unit("µg", Dim.MASS, 0.001, 0.0);
    // 热流以 mW 为内部归一
    public static final Unit MW = new Unit("mW", Dim.HEAT_FLOW, 1.0, 0.0);
    public static final Unit W = new Unit("W", Dim.HEAT_FLOW, 1000.0, 0.0);
    // 扫描速率
    public static final Unit C_PER_MIN = new Unit("°C/min", Dim.RATE, 1.0, 0.0);
    public static final Unit C_PER_SEC = new Unit("°C/s", Dim.RATE, 60.0, 0.0);
    public static final Unit K_PER_MIN = new Unit("K/min", Dim.RATE, 1.0, 0.0);
    public static final Unit MIN = new Unit("min", Dim.TIME, 1.0, 0.0);
    public static final Unit S = new Unit("s", Dim.TIME, 1.0 / 60.0, 0.0);
    // 焓以 J/g 为内部归一
    public static final Unit J_PER_G = new Unit("J/g", Dim.HEAT, 1.0, 0.0);
    public static final Unit J_PER_Gm = new Unit("mJ/mg", Dim.HEAT, 1.0, 0.0);
    public static final Unit KJ_PER_G = new Unit("kJ/g", Dim.HEAT, 1000.0, 0.0);
    public static final Unit NONE = new Unit("", Dim.DIMENSIONLESS, 1.0, 0.0);

    private static final Map<String, Unit> REGISTRY = new LinkedHashMap<>();
    static {
        for (Unit u : new Unit[]{C, K, F, MG, G, UG, MW, W, C_PER_MIN, C_PER_SEC, K_PER_MIN,
                MIN, S, J_PER_G, J_PER_Gm, KJ_PER_G, NONE}) {
            REGISTRY.put(u.symbol, u);
        }
    }

    private final String symbol;
    private final Dim dim;
    private final double factor; // normalized = value*factor + offset
    private final double offset;

    private Unit(String symbol, Dim dim, double factor, double offset) {
        this.symbol = symbol;
        this.dim = dim;
        this.factor = factor;
        this.offset = offset;
    }

    public String symbol() { return symbol; }
    public Dim dim() { return dim; }
    public double factor() { return factor; }
    public double offset() { return offset; }

    /** 解析单位符号；未知单位返回 null（调用方必须保留原值并附诊断，不得丢弃）。 */
    public static Unit parse(String symbol) {
        if (symbol == null || symbol.isBlank()) return NONE;
        return REGISTRY.get(symbol.trim());
    }

    public static boolean canConvert(String symbol, Dim expected) {
        Unit u = parse(symbol);
        return u != null && (u.dim == expected || expected == Dim.UNKNOWN);
    }

    /** 线性转换到内部归一单位；维度不匹配抛出诊断异常。 */
    public double toNormalized(double value) {
        return value * factor + offset;
    }

    public double fromNormalized(double normalized) {
        return (normalized - offset) / factor;
    }

    public static double convert(double value, String symbol, Dim expected) {
        Unit u = parse(symbol);
        if (u == null) throw new UnitException("未知单位，无法转换: '" + symbol + "'");
        if (u.dim != expected) {
            throw new UnitException("量纲不匹配: '" + symbol + "' 是 " + u.dim + "，需要 " + expected);
        }
        return u.toNormalized(value);
    }

    public static Set<String> knownSymbols() { return REGISTRY.keySet(); }

    @Override public String toString() { return symbol; }

    public static final class UnitException extends RuntimeException {
        public UnitException(String m) { super(m); }
    }
}
