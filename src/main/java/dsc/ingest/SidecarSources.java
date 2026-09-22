package dsc.ingest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dsc.model.Diagnostic;
import dsc.model.Units;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

/**
 * 旁置来源解析：质量、扫描速率、气氛、温度标定、切线选择。
 * 每个字段保留原始字符串表示，规范化只写进 canonical* 字段；
 * 无法转换时 canonical 置 null 并附诊断，原始表示绝不丢失。
 */
public final class SidecarSources {

    public record Parsed(ObjectNode json, List<Diagnostic> diagnostics) {
    }

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private SidecarSources() {
    }

    public static Parsed parse(String sourceType, String rawJson) {
        List<Diagnostic> diag = new ArrayList<>();
        try {
            ObjectNode node = (ObjectNode) MAPPER.readTree(rawJson);
            node.put("__sourceType", sourceType);
            node.put("__rawSha256", sha256(rawJson));
            switch (sourceType) {
                case "mass" -> annotateMass(node, diag);
                case "scan-rate" -> annotateRate(node, diag);
                case "atmosphere" -> annotateAtmosphere(node, diag);
                case "calibration" -> annotateCalibration(node, diag);
                case "tangent" -> annotateTangent(node, diag);
                default -> diag.add(Diagnostic.warn("UNKNOWN_SOURCE_TYPE",
                        "未知来源类型 " + sourceType + "，仅存档不派生"));
            }
            return new Parsed(node, diag);
        } catch (Exception e) {
            diag.add(Diagnostic.error("SIDECAR_PARSE_FAILED",
                    sourceType + " 解析失败，原文保留: " + e.getMessage()));
            ObjectNode fallback = MAPPER.createObjectNode();
            fallback.put("__sourceType", sourceType);
            fallback.put("__parseError", e.getMessage());
            return new Parsed(fallback, diag);
        }
    }

    private static void annotateMass(ObjectNode node, List<Diagnostic> diag) {
        String raw = node.path("mass").asText("");
        String unit = node.path("unit").asText("mg");
        try {
            double v = Double.parseDouble(raw.replaceAll("[^0-9eE+\\-.]", ""));
            if (!Units.isKnown(unit) || Units.isTemperature(unit)) {
                throw new Units.UnknownUnitException(unit);
            }
            node.put("canonicalGrams", Units.toCanonical(v, unit, null));
            node.put("canonicalUnit", Units.GRAM);
        } catch (RuntimeException ex) {
            node.putNull("canonicalGrams");
            diag.add(Diagnostic.error("MASS_NOT_CONVERTIBLE",
                    "质量值无法转换: " + ex.getMessage()).withFragment(raw));
        }
    }

    private static void annotateRate(ObjectNode node, List<Diagnostic> diag) {
        for (String seg : java.util.List.of("H1", "C1", "H2")) {
            String field = "rate-" + seg;
            if (node.has(field)) {
                annotateOneRate(node, diag, field);
            }
        }
        if (node.has("rate")) {
            annotateOneRate(node, diag, "rate");
        }
    }

    private static void annotateOneRate(ObjectNode node, List<Diagnostic> diag, String field) {
        String raw = node.get(field).asText("");
        String unit = node.path("unit").asText("K/min");
        try {
            double v = Double.parseDouble(raw.replaceAll("[^0-9eE+\\-.]", ""));
            if (!Units.isKnown(unit)) {
                throw new Units.UnknownUnitException(unit);
            }
            node.with("canonical").put(field, Units.toCanonical(v, unit, null));
        } catch (RuntimeException ex) {
            diag.add(Diagnostic.error("RATE_NOT_CONVERTIBLE",
                    "扫描速率无法转换: " + ex.getMessage()).withFragment(raw));
        }
    }

    private static void annotateAtmosphere(ObjectNode node, List<Diagnostic> diag) {
        if (node.path("gas").asText("").isBlank()) {
            diag.add(Diagnostic.warn("ATMOSPHERE_GAS_MISSING", "气氛缺少气体名称，仅存档"));
        }
        String flowRaw = node.path("flow").asText("");
        if (!flowRaw.isBlank()) {
            try {
                double v = Double.parseDouble(flowRaw.replaceAll("[^0-9eE+\\-.]", ""));
                node.put("canonicalFlowMlPerMin", v);
            } catch (RuntimeException ex) {
                diag.add(Diagnostic.warn("ATMOSPHERE_FLOW_UNPARSEABLE",
                        "气氛流量无法解析，原值保留").withFragment(flowRaw));
            }
        }
    }

    private static void annotateCalibration(ObjectNode node, List<Diagnostic> diag) {
        String raw = node.path("offset").asText("");
        String unit = node.path("unit").asText("°C");
        try {
            double v = Double.parseDouble(raw.replaceAll("[^0-9eE+\\-.]", ""));
            if (!Units.isTemperature(unit)) {
                throw new Units.UnknownUnitException(unit);
            }
            node.put("canonicalOffsetK", v);
            node.put("canonicalBasis", "T_indicated = T_true + offset");
        } catch (RuntimeException ex) {
            node.putNull("canonicalOffsetK");
            diag.add(Diagnostic.error("CALIBRATION_NOT_CONVERTIBLE",
                    "温度标定偏移无法转换: " + ex.getMessage()).withFragment(raw));
        }
    }

    private static void annotateTangent(ObjectNode node, List<Diagnostic> diag) {
        for (String key : Map.of("startK", "startK", "endK", "endK", "anchorBandK", "anchorBandK")
                .keySet()) {
            if (node.has(key)) {
                try {
                    node.get(key).asText();
                    double v = Double.parseDouble(node.get(key).asText());
                    node.with("canonical").put(key, v);
                } catch (RuntimeException ex) {
                    diag.add(Diagnostic.error("TANGENT_COORD_BAD",
                            "切线坐标 " + key + " 无法解析，原表示保留"));
                }
            }
        }
        if (node.path("segmentId").asText("").isBlank()) {
            diag.add(Diagnostic.error("TANGENT_SEGMENT_MISSING",
                    "切线选择必须绑定单一程序段，禁止跨加热/冷却段"));
        }
    }

    public static String sha256(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(s.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
