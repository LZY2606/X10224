package dsc.core;

/**
 * 吸放热方向判定：只能来自仪器头 / 标定 / 用户显式指定，
 * 绝不从文件名猜测。无法确定时保持 UNKNOWN 并给出诊断。
 */
public final class DirectionResolver {

    public enum EndoConvention { ENDO_UP, ENDO_DOWN, UNKNOWN }

    public record Direction(EndoConvention convention, String source, String diagnostic) {}

    private DirectionResolver() {}

    public static Direction resolve(InstrumentFile file, String fileName) {
        String h = file.headerOr("EndoDirection", "").trim().toLowerCase();
        boolean nameClaimsUp = fileName != null && fileName.toLowerCase().contains("endo-up");
        if (h.equals("up") || h.equals("endo-up") || h.equals("endoup")) {
            return new Direction(EndoConvention.ENDO_UP, "INSTRUMENT_HEADER",
                    nameClaimsUp ? null : null);
        }
        if (h.equals("down") || h.equals("endo-down") || h.equals("endodown")) {
            // 文件名与仪器头冲突时以仪器头为准，并记录诊断
            String diag = nameClaimsUp
                    ? "FILENAME_DIRECTION_IGNORED: 文件名暗示 endo-up，但仪器头声明 endo-down，采用仪器头"
                    : null;
            return new Direction(EndoConvention.ENDO_DOWN, "INSTRUMENT_HEADER", diag);
        }
        return new Direction(EndoConvention.UNKNOWN, "NONE",
                "DIRECTION_UNKNOWN: 仪器头未声明吸热方向，且规则禁止从文件名猜测");
    }
}
