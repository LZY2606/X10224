package dsc.domain;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dsc.util.Json;

/**
 * 领域量：数值 + 单位 + 坐标基准 + 原始表示。
 * 导入层不做强制换算；无法转换时 value 为 NaN，raw 保留在原位并附诊断。
 */
public record Quant(double value, String unit, String basis, String raw, Double uncertainty) {

    public static Quant of(double value, String unit) {
        return new Quant(value, unit, null, null, null);
    }

    public static Quant of(double value, String unit, String basis) {
        return new Quant(value, unit, basis, null, null);
    }

    public static Quant unconvertible(String raw, String unit, String reason) {
        return new Quant(Double.NaN, unit, null, raw, null).withDiagnostic(reason);
    }

    public Quant withBasis(String b) {
        return new Quant(value, unit, b, raw, uncertainty);
    }

    public Quant withUncertainty(double u) {
        return new Quant(value, unit, basis, raw, u);
    }

    private Quant withDiagnostic(String reason) {
        this.diagnostic = reason;
        return this;
    }

    public String diagnostic;

    public ObjectNode toJson() {
        ObjectNode o = Json.obj();
        o.put("value", Double.isNaN(value) ? (Double) null : value);
        if (unit != null) {
            o.put("unit", unit);
        }
        if (basis != null) {
            o.put("basis", basis);
        }
        if (raw != null) {
            o.put("raw", raw);
        }
        if (uncertainty != null) {
            o.put("uncertainty", uncertainty);
        }
        if (diagnostic != null) {
            o.put("diagnostic", diagnostic);
        }
        return o;
    }

    public static Quant fromJson(JsonNode n) {
        if (n == null || n.isMissingNode()) {
            return null;
        }
        double v = n.hasNonNull("value") ? n.get("value").asDouble() : Double.NaN;
        Quant q = new Quant(v, text(n, "unit"), text(n, "basis"), text(n, "raw"),
                n.hasNonNull("uncertainty") ? n.get("uncertainty").asDouble() : null);
        if (n.hasNonNull("diagnostic")) {
            q.diagnostic = n.get("diagnostic").asText();
        }
        return q;
    }

    private static String text(JsonNode n, String k) {
        return n.hasNonNull(k) ? n.get(k).asText() : null;
    }
}
