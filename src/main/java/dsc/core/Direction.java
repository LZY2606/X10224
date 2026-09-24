package dsc.core;

/**
 * 扫描方向判定。吸放热方向不能靠文件名猜测：方向由序列自身的温度斜率
 * （{@code derive}）推导，并与仪器/元数据声明（{@code declared}）分开存档；
 * 两者不一致时不静默选一个，而是留下异常。
 * 加热段与冷却段的积分符号约定互不混用。
 */
public enum Direction {
    HEATING("heating"),
    COOLING("cooling"),
    UNKNOWN("unknown");

    public final String code;
    Direction(String code) { this.code = code; }

    public static Direction fromDeclared(String s) {
        if (s == null) return UNKNOWN;
        String t = s.trim().toLowerCase();
        if (t.contains("heat") || t.equals("up") || t.contains("升温") || t.contains("加热")) return HEATING;
        if (t.contains("cool") || t.equals("down") || t.contains("降温") || t.contains("冷却")) return COOLING;
        return UNKNOWN;
    }

    /**
     * 由温度列推导方向。
     * @param monotonicFraction 单调步占比（外部按温度列计算），返回 HEATING/COOLING/UNKNOWN
     */
    public static Direction derive(double[] temp, boolean nonUniformAllowed) {
        if (temp == null || temp.length < 2) return UNKNOWN;
        int up = 0, down = 0, flat = 0;
        for (int i = 1; i < temp.length; i++) {
            if (temp[i] > temp[i - 1]) up++;
            else if (temp[i] < temp[i - 1]) down++;
            else flat++;
        }
        int total = temp.length - 1;
        if (up >= 0.9 * total) return HEATING;
        if (down >= 0.9 * total) return COOLING;
        return UNKNOWN;
    }
}
