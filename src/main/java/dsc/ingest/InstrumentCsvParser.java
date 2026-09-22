package dsc.ingest;

import dsc.model.Diagnostic;
import dsc.model.HeatFlowSign;
import dsc.model.Point;
import dsc.model.Run;
import dsc.model.ScanMode;
import dsc.model.Segment;
import dsc.model.Units;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 仪器输出解析器。格式为自描述 CSV：
 *   #key: value 头部（含温度/热流单位与吸放热方向约定）
 *   #SEGMENT: id,mode(声明)
 *   index,temperature,heatflow 数据行；空行分段。
 * 所有原始 token 原样保留；无法转换的点留在原位置并附诊断。
 */
public final class InstrumentCsvParser {

    public record ParseResult(Run run, List<Diagnostic> diagnostics) {
    }

    private InstrumentCsvParser() {
    }

    public static ParseResult parse(String runId, String fileName, String content) {
        List<Diagnostic> diagnostics = new ArrayList<>();
        Map<String, String> meta = new LinkedHashMap<>();
        List<Point> points = new ArrayList<>();
        List<int[]> segmentSpans = new ArrayList<>();
        List<String> segmentDeclared = new ArrayList<>();
        List<String> segmentIds = new ArrayList<>();

        String[] lines = content.split("\\r?\\n");
        int segStart = 0;
        int autoSeg = 0;
        String currentId = null;
        ScanMode currentMode = ScanMode.UNKNOWN;
        boolean headerSeen = false;

        for (String rawLine : lines) {
            String line = rawLine.strip();
            if (line.isEmpty()) {
                if (!points.isEmpty() && segStart < points.size()) {
                    segmentSpans.add(new int[]{segStart, points.size()});
                    segmentIds.add(currentId != null ? currentId : runId + "-S" + (autoSeg + 1));
                    segmentDeclared.add(currentMode.name());
                    segStart = points.size();
                    autoSeg++;
                }
                continue;
            }
            if (line.startsWith("#")) {
                String body = line.substring(1);
                int colon = body.indexOf(':');
                if (colon < 0) {
                    diagnostics.add(Diagnostic.warn("BAD_HEADER_LINE",
                            "无法识别的头部行，已保留原文: " + line).withFragment(rawLine));
                    continue;
                }
                String key = body.substring(0, colon).trim().toLowerCase();
                String value = body.substring(colon + 1).trim();
                if (key.equals("segment")) {
                    if (!points.isEmpty() && segStart < points.size()) {
                        segmentSpans.add(new int[]{segStart, points.size()});
                        segmentIds.add(currentId != null ? currentId : runId + "-S" + (autoSeg + 1));
                        segmentDeclared.add(currentMode.name());
                        segStart = points.size();
                        autoSeg++;
                    }
                    String[] parts = value.split(",");
                    currentId = parts[0].trim();
                    currentMode = parts.length > 1 ? ScanMode.fromToken(parts[1].trim())
                            : ScanMode.UNKNOWN;
                    headerSeen = true;
                } else {
                    meta.put(key, value);
                }
                continue;
            }
            if (line.toLowerCase().startsWith("index")) {
                headerSeen = true;
                continue;
            }
            String[] cells = line.split(",", -1);
            int idx;
            try {
                idx = Integer.parseInt(cells[0].trim());
            } catch (NumberFormatException e) {
                points.add(Point.invalid(points.size(), cells[0].trim(),
                        cells.length > 2 ? cells[2].trim() : "", "序号不可解析"));
                continue;
            }
            String rawT = cells.length > 1 ? cells[1].trim() : "";
            String rawHf = cells.length > 2 ? cells[2].trim() : "";
            try {
                double tNum = Double.parseDouble(rawT);
                String tUnit = meta.getOrDefault("temperature-unit", "°C");
                if (!Units.isKnown(tUnit) || !Units.isTemperature(tUnit)) {
                    throw new Units.UnknownUnitException(tUnit);
                }
                double tk = Units.toCanonical(tNum, tUnit, "ITS-90");
                double hfNum = Double.parseDouble(rawHf);
                String hfUnit = meta.getOrDefault("heatflow-unit", "mW");
                if (!Units.isKnown(hfUnit) || Units.isTemperature(hfUnit)) {
                    throw new Units.UnknownUnitException(hfUnit);
                }
                double hfW = Units.toCanonical(hfNum, hfUnit, null);
                points.add(Point.valid(idx, rawT, rawHf, tk, hfW));
            } catch (RuntimeException ex) {
                points.add(Point.invalid(idx, rawT, rawHf, ex.getMessage()));
            }
        }
        if (segStart < points.size()) {
            segmentSpans.add(new int[]{segStart, points.size()});
            segmentIds.add(currentId != null ? currentId : runId + "-S" + (autoSeg + 1));
            segmentDeclared.add(currentMode.name());
        }

        HeatFlowSign sign = HeatFlowSign.fromToken(meta.get("heatflow-sign"));
        if (sign == HeatFlowSign.UNKNOWN) {
            diagnostics.add(Diagnostic.warn("HEATFLOW_SIGN_UNKNOWN",
                    "仪器输出缺少 #heatflow-sign 声明；方向不能靠文件名猜测，等待人工指定")
                    .withRef(fileName));
        }
        if (!headerSeen) {
            diagnostics.add(Diagnostic.error("NO_DATA_HEADER",
                    "未见 index,temperature,heatflow 表头，文件可能不是仪器输出").withRef(fileName));
        }

        List<Segment> segments = new ArrayList<>();
        for (int s = 0; s < segmentSpans.size(); s++) {
            int a = segmentSpans.get(s)[0];
            int b = segmentSpans.get(s)[1];
            List<Point> segPoints = new ArrayList<>(points.subList(a, b));
            List<Diagnostic> segDiag = new ArrayList<>();
            ScanMode declared = ScanMode.valueOf(segmentDeclared.get(s));
            if (declared == ScanMode.UNKNOWN) {
                segDiag.add(Diagnostic.warn("SEGMENT_MODE_UNDECLARED",
                        "段 " + segmentIds.get(s) + " 未声明加热/冷却，将按数据走向判定")
                        .withRef(segmentIds.get(s)));
            }
            segments.add(new Segment(segmentIds.get(s), runId, s + 1, declared,
                    List.copyOf(segPoints), segDiag));
        }
        Run run = new Run(runId, fileName, Map.copyOf(meta), sign, List.copyOf(segments),
                List.of());
        return new ParseResult(run, diagnostics);
    }
}
