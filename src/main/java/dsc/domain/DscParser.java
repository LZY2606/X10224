package dsc.domain;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 解析仓库内 .dsc 文本档案。
 * 格式：
 *   @sample id/name
 *   MASS raw unit            （可多行，revision 按出现顺序）
 *   RATE raw unit            ATMOSPHERE gas flow
 *   CAL note slope offsetK
 *   SEGMENT label rate(K/min) direction convention hf-unit [cycle=...]
 *   TANGENT segment kind leftT:C rightT:C leftSlope rightSlope adopted by=...
 *   温度 热流                （数据行，原始字符串原样保留）
 *   ===                      （段分隔）
 */
public final class DscParser {

    public static DscCaseFile parse(Path path) {
        try {
            return parse(Files.readString(path), path.getFileName().toString());
        } catch (Exception e) {
            throw new IllegalArgumentException("读取档案失败: " + path, e);
        }
    }

    public static DscCaseFile parse(String text, String fileName) {
        List<String> diag = new ArrayList<>();
        String sampleId = "S-" + fileName.replaceAll("[^A-Za-z0-9]", "").toUpperCase();
        String sampleName = fileName;
        String instrument = "unknown";
        String acquiredAt = "";
        HeatFlowConvention fileConvention = HeatFlowConvention.UNDECLARED;
        String hfUnit = "mW";

        List<Qty> masses = new ArrayList<>();
        List<Atmosphere> atms = new ArrayList<>();
        List<TempCalibration> cals = new ArrayList<>();
        List<TangentSelection> tangents = new ArrayList<>();

        String instrumentOutputId = "IO-" + DeterministicId.shortHash(fileName);
        List<Segment> segments = new ArrayList<>();

        String segLabel = "seg" + (segments.size() + 1);
        double segRate = Double.NaN;
        ScanDirection segDeclared = ScanDirection.UNKNOWN;
        HeatFlowConvention segConv = HeatFlowConvention.UNDECLARED;
        String segHf = "mW";
        String segCycle = null;
        List<Point> segPoints = new ArrayList<>();
        int segSeq = 0;

        String[] lines = text.split("\\R");
        for (int ln = 0; ln < lines.length; ln++) {
            String rawLine = lines[ln];
            String line = rawLine.trim();
            if (line.isEmpty() || line.startsWith("#")) continue;

            if (line.startsWith("@sample")) {
                String body = line.substring("@sample".length()).trim();
                String[] parts = body.split("\\|", 2);
                sampleId = parts[0].trim();
                if (parts.length == 2) sampleName = parts[1].trim();
            } else if (line.startsWith("@instrument")) {
                instrument = line.substring("@instrument".length()).trim();
            } else if (line.startsWith("@acquired")) {
                acquiredAt = line.substring("@acquired".length()).trim();
            } else if (line.startsWith("@convention")) {
                fileConvention = parseConvention(line.substring("@convention".length()).trim());
            } else if (line.startsWith("@hfunit")) {
                hfUnit = line.substring("@hfunit".length()).trim();
            } else if (line.startsWith("MASS")) {
                List<String> t = tokens(line, 2);
                masses.add(Qty.mass(t.get(0), t.size() > 1 ? t.get(1) : ""));
            } else if (line.startsWith("RATE")) {
                // RATE raw unit ; ATMOSPHERE 在自己的行里
                List<String> t = tokens(line, 3);
                // RATE 只有两列即可；多余 token 容忍
            } else if (line.startsWith("ATMOSPHERE")) {
                List<String> t = tokens(line, 3);
                String gas = t.size() > 1 ? t.get(1) : "";
                Double flow = t.size() > 2 ? Qty.parseNumber(t.get(2)) : null;
                atms.add(new Atmosphere(line, gas, flow));
            } else if (line.startsWith("CAL")) {
                List<String> t = tokens(line, 4);
                if (t.size() < 4) { diag.add("CAL 行字段不足: " + line); continue; }
                Double slope = Qty.parseNumber(t.get(1));
                Double off = Qty.parseNumber(t.get(2));
                cals.add(new TempCalibration(
                        "CAL-" + cals.size() + "-" + DeterministicId.shortHash(line),
                        cals.size(), instrumentOutputId,
                        slope == null ? 1.0 : slope,
                        off == null ? 0.0 : off, t.get(3)));
                if (slope == null || off == null) diag.add("CAL 含不可解析数值，退回恒等并保留: " + line);
            } else if (line.startsWith("SEGMENT")) {
                if (!segPoints.isEmpty() || segSeq > 0) flush(segments, instrumentOutputId,
                        segLabel, segRate, segDeclared, segConv, segHf, segCycle, segPoints, diag);
                segSeq++;
                segPoints = new ArrayList<>();
                List<String> t = tokens(line, 6);
                segLabel = t.get(1);
                Double rate = Qty.parseNumber(t.get(2));
                segRate = rate == null ? Double.NaN : rate;
                segDeclared = parseDirection(t.get(3));
                segConv = parseConvention(t.get(4));
                if (segConv == HeatFlowConvention.UNDECLARED) segConv = fileConvention;
                segHf = t.get(5);
                if (segHf == null || segHf.isBlank() || segHf.equals("-")) segHf = hfUnit;
                segCycle = null;
                for (int i = 6; i < t.size(); i++) {
                    if (t.get(i).startsWith("cycle=")) segCycle = t.get(i).substring(6);
                }
            } else if (line.startsWith("TANGENT")) {
                List<String> t = tokens(line, 9);
                if (t.size() < 8) { diag.add("TANGENT 行字段不足: " + line); continue; }
                String sid = "SEG-" + DeterministicId.shortHash(instrumentOutputId + "|" + t.get(1));
                Double lt = parseTemp(t.get(2));
                Double rt = parseTemp(t.get(3));
                Double ls = Qty.parseNumber(t.get(4));
                Double rs = Qty.parseNumber(t.get(5));
                boolean adopted = t.get(6).equalsIgnoreCase("adopted");
                String by = t.get(7).startsWith("by=") ? t.get(7).substring(3) : t.get(7);
                int rev = tangents.size();
                tangents.add(new TangentSelection("TAN-" + rev + "-" + DeterministicId.shortHash(line),
                        sid, rev, by, lt, rt,
                        ls == null ? 0 : ls, rs == null ? 0 : rs,
                        "two-tangent", adopted));
                if (ls == null || rs == null) diag.add("TANGENT 斜率不可解析，按 0 处理并保留: " + line);
            } else if (line.equals("===")) {
                flush(segments, instrumentOutputId, segLabel, segRate, segDeclared,
                        segConv, segHf, segCycle, segPoints, diag);
                segPoints = new ArrayList<>();
                segSeq = 0;
            } else {
                // 数据行：最多两列，原始字符串保留
                String[] p = line.split("\\s+");
                if (p.length < 2) {
                    diag.add("无法识别的行（已保留在诊断中）: " + line);
                    continue;
                }
                String rt = p[0], rh = p[1];
                Qty tq = Qty.temperature(rt, "°C");
                Qty hq = Qty.heatFlow(rh, segHf, false);
                if (!tq.usable()) {
                    diag.add("点温度不可转换，保留原始值，该点不参与派生: " + line);
                    continue;
                }
                if (!hq.usable()) {
                    diag.add("点热流不可转换，保留原始值，该点不参与派生: " + line);
                    continue;
                }
                segPoints.add(new Point(segPoints.size(), rt, rh, tq.canonical, hq.canonical));
            }
        }
        flush(segments, instrumentOutputId, segLabel, segRate, segDeclared,
                segConv, segHf, segCycle, segPoints, diag);

        InstrumentOutput io = new InstrumentOutput(instrumentOutputId, instrument, fileName,
                fileConvention, hfUnit, acquiredAt, List.copyOf(segments));
        return new DscCaseFile(io, List.copyOf(masses), List.copyOf(atms),
                List.copyOf(cals), List.copyOf(tangents), List.copyOf(diag));
    }

    private static void flush(List<Segment> segments, String ioId, String label, double rate,
                              ScanDirection declared, HeatFlowConvention conv, String hf,
                              String cycle, List<Point> pts, List<String> diag) {
        if (pts.isEmpty()) return;
        ScanDirection obs = Double.isNaN(rate) ? ScanDirection.UNKNOWN
                : rate > 0 ? ScanDirection.HEATING : rate < 0 ? ScanDirection.COOLING : ScanDirection.ISOTHERMAL;
        if (declared != ScanDirection.UNKNOWN && declared != obs) {
            diag.add("段 " + label + " 声明方向 " + declared + " 与速率符号 " + obs
                    + " 冲突；以速率为准并保留声明");
        }
        if (conv == HeatFlowConvention.UNDECLARED) {
            diag.add("段 " + label + " 未声明吸放热方向约定；事件方向将保持 UNKNOWN");
        }
        String sid = "SEG-" + DeterministicId.shortHash(ioId + "|" + label + "|" + segments.size());
        segments.add(new Segment(sid, ioId, label, obs, Double.isNaN(rate) ? 0 : rate,
                conv, hf, List.copyOf(pts), cycle));
    }

    private static List<String> tokens(String line, int min) {
        List<String> out = new ArrayList<>(List.of(line.trim().split("\\s+")));
        while (out.size() < min) out.add("");
        return out;
    }

    private static Double parseTemp(String s) {
        Qty q = Qty.temperature(s, "°C");
        return q.usable() ? q.canonical : null;
    }

    private static ScanDirection parseDirection(String s) {
        return switch (s.toLowerCase()) {
            case "heating", "heat", "h" -> ScanDirection.HEATING;
            case "cooling", "cool", "c" -> ScanDirection.COOLING;
            case "isothermal", "iso" -> ScanDirection.ISOTHERMAL;
            default -> ScanDirection.UNKNOWN;
        };
    }

    private static HeatFlowConvention parseConvention(String s) {
        return switch (s.toLowerCase()) {
            case "exo-up", "exo_up", "up" -> HeatFlowConvention.EXO_UP;
            case "exo-down", "exo_down", "down" -> HeatFlowConvention.EXO_DOWN;
            default -> HeatFlowConvention.UNDECLARED;
        };
    }
}
