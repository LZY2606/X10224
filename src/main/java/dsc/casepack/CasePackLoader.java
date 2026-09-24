package dsc.casepack;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dsc.domain.RawPoint;
import dsc.domain.Units;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * 读取仓库自带离线案例包。仪器 CSV 以文本流逐行解析：
 * 原始字符串原样保留，换算在派生层进行，无法换算的点带诊断保留。
 */
public final class CasePackLoader {
    private static final ObjectMapper M = new ObjectMapper();
    public static final String RESOURCE_ROOT = "/casepack";

    private CasePackLoader() {
    }

    public static CasePack loadFromResources() throws IOException {
        try (InputStream in = CasePackLoader.class.getResourceAsStream(RESOURCE_ROOT + "/manifest.json")) {
            if (in == null) {
                throw new IOException("案例包 manifest 不存在");
            }
            JsonNode root = M.readTree(in);
            return map(root);
        }
    }

    public static CasePack loadFromDirectory(Path dir) throws IOException {
        JsonNode root = M.readTree(Files.readString(dir.resolve("manifest.json"), StandardCharsets.UTF_8));
        return map(root);
    }

    private static CasePack map(JsonNode root) {
        List<CasePack.SegmentDef> segs = new ArrayList<>();
        for (JsonNode s : root.get("segments")) {
            JsonNode rate = s.get("rate");
            JsonNode atm = s.get("atmosphere");
            segs.add(new CasePack.SegmentDef(
                    s.get("segmentId").asText(),
                    s.get("file").asText(),
                    s.path("cycle").asInt(1),
                    s.get("programDirection").asText(),
                    s.get("declaredSign").asText(),
                    rate.get("value").asText(), rate.get("unit").asText(),
                    atm.get("gas").asText(),
                    atm.get("flowRaw").asText(), atm.get("flowUnit").asText()));
        }
        List<CasePack.IntervalDef> ivs = new ArrayList<>();
        for (JsonNode s : root.get("initialIntervals")) {
            ivs.add(new CasePack.IntervalDef(
                    s.get("intervalId").asText(), s.get("segmentId").asText(),
                    s.get("t0C").asDouble(), s.get("t1C").asDouble(),
                    s.get("transitionId").asText(), s.path("hint").asText("")));
        }
        List<CasePack.SourceDef> srcs = new ArrayList<>();
        for (JsonNode s : root.get("sources")) {
            srcs.add(new CasePack.SourceDef(
                    s.get("id").asText(), s.get("kind").asText(), s.get("refId").asText(),
                    s.path("version").asInt(1), s.get("originalRep").asText(), s));
        }
        return new CasePack(root.get("caseId").asText(), root.get("title").asText(),
                root.get("schemaVersion").asInt(1), root, segs, ivs, srcs,
                root.get("beats"), root.get("edgeCases"));
    }

    public record CsvData(String header, String tUnit, String hfUnit,
                          String declaredSign, List<RawPoint> points) {
    }

    public static CsvData parseSegmentCsv(Path csv) throws IOException {
        return parse(Files.readString(csv, StandardCharsets.UTF_8));
    }

    public static CsvData parseSegmentResource(String resourcePath) throws IOException {
        try (InputStream in = CasePackLoader.class.getResourceAsStream(resourcePath)) {
            if (in == null) {
                throw new IOException("缺少 fixture: " + resourcePath);
            }
            return parse(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        }
    }

    /** 解析仪器输出文本；header 保留原始表示。 */
    public static CsvData parse(String text) {
        List<RawPoint> points = new ArrayList<>();
        String header = "";
        String tUnit = "°C";
        String hfUnit = "mW";
        String sign = "UNKNOWN";
        long seq = 0;
        for (String line : text.split("\\r?\\n")) {
            if (line.isBlank()) {
                continue;
            }
            if (line.startsWith("#")) {
                header = line;
                tUnit = attr(line, "t_unit", tUnit);
                hfUnit = attr(line, "hf_unit", hfUnit);
                sign = attr(line, "sign", sign);
                continue;
            }
            if (line.toLowerCase().startsWith("index")) {
                continue;
            }
            String[] parts = line.split(",", -1);
            if (parts.length < 3) {
                continue;
            }
            seq++;
            String tRaw = parts[1].trim();
            String hfRaw = parts[2].trim();
            Units.Parse tp = Units.temperatureK(tRaw, tUnit);
            Units.Parse hp = Units.powerW(hfRaw, hfUnit);
            boolean excluded = tp.value == null || hp.value == null;
            String diag = excluded
                    ? String.join("; ", nz(tp.diagnostic), nz(hp.diagnostic))
                    : null;
            points.add(new RawPoint(seq, tRaw, hfRaw, tp.value, hp.value, excluded, diag));
        }
        return new CsvData(header, tUnit, hfUnit, sign, points);
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    private static String attr(String header, String key, String dflt) {
        for (String part : header.replace("#", "").split(";")) {
            String[] kv = part.split("=", 2);
            if (kv.length == 2 && kv[0].trim().equals(key)) {
                return kv[1].trim();
            }
        }
        return dflt;
    }
}
