package dsc.domain;

import java.util.Objects;

/**
 * 领域量：永远保留原始表示 raw/unit；normalized 为规范化到内部基准后的 SI 同族值。
 * 无法转换时 normalized == null 且 error 给出诊断，原始值留在原位置，不会被丢弃。
 * 温度内部基准 K；热流 W（总量族）或 W/g（比热流族）；扫描速率 K/s；时间 s；质量 kg。
 */
public record Q(Double value, String raw, String unit, String error) {

    public static Q of(double si, String raw, String unit) {
        return new Q(si, raw, unit, null);
    }

    public static Q bad(String raw, String unit, String error) {
        return new Q(null, raw, unit, error);
    }

    public boolean valid() {
        return value != null && error == null;
    }

    @Override
    public String toString() {
        if (valid()) {
            return String.format("%.6g%s", value, unit == null ? "" : " " + unit);
        }
        return "UNCONVERTED(" + raw + (unit != null ? " " + unit : "") + ")";
    }

    public String display() {
        if (valid()) {
            return String.format("%.4g%s", value, unit == null ? "" : " " + unit);
        }
        return raw + (unit != null ? " " + unit : "") + " ⚠" + error;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Q q)) return false;
        return Objects.equals(value, q.value) && Objects.equals(raw, q.raw)
                && Objects.equals(unit, q.unit) && Objects.equals(error, q.error);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value, raw, unit, error);
    }
}
