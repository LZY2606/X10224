package org.materials.dsc.io;

import org.materials.dsc.domain.Diagnostic;
import org.materials.dsc.domain.Direction;
import org.materials.dsc.domain.InstrumentOutput;
import org.materials.dsc.domain.SampleRow;
import org.materials.dsc.domain.SignConvention;
import org.materials.dsc.domain.Units;
import org.materials.dsc.engine.EngineMath;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 仪器输出 CSV 导入。
 * 文件头键值元数据（direction/sign-convention 必须显式给出），随后为数据块：
 *   Index, Segment, Temperature, HeatFlow
 * 行内允许坏值：保留原始字符串，normalized* 置空并挂诊断。
 */
public final class CsvInstrumentImporter {
    private CsvInstrumentImporter() {
    }

    public record Parsed(InstrumentOutput output, String[] rowSegmentLabels, List<String> segmentNames) {
    }

    public static Parsed importCsv(String id, String fileName, String content) {
        String sha = sha256(content);
        String[] lines = content.replace("\r\n", "\n").split("\n");
        Map<String, String> meta = new LinkedHashMap<>();
        int dataStart = 0;
        List<String> headers = new ArrayList<>();
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            if (line.isBlank()) {
                continue;
            }
            String trimmed = line.trim();
            if (trimmed.regionMatches(true, 0, "Index", 0, 5) || trimmed.startsWith("#data")) {
                dataStart = i + (trimmed.startsWith("#data") ? 1 : 0);
                if (!trimmed.startsWith("#data")) {
                    for (String h : splitCsv(trimmed)) {
                        headers.add(h.trim());
                    }
                    dataStart = i + 1;
                }
                break;
            }
            if (trimmed.startsWith("#")) {
                String body = trimmed.substring(1).trim();
                int colon = body.indexOf(':');
                if (colon > 0) {
                    meta.put(body.substring(0, colon).trim().toLowerCase(Locale.ROOT),
                            body.substring(colon + 1).trim());
                }
            } else {
                int colon = trimmed.indexOf(':');
                if (colon > 0 && !trimmed.substring(0, colon).contains(",")) {
                    meta.put(trimmed.substring(0, colon).trim().toLowerCase(Locale.ROOT),
                            trimmed.substring(colon + 1).trim());
                }
            }
        }

        String dirRaw = meta.getOrDefault("direction", null);
        String signRaw = meta.getOrDefault("sign-convention",
                meta.get("sign convention"));
        Direction direction = Direction.parse(dirRaw);
        SignConvention sign = SignConvention.parse(signRaw);
        List<Diagnostic> diag = new ArrayList<>();
        if (dirRaw == null) {
            diag.add(Diagnostic.error("diag-" + id + "-dir", "missing-direction",
                    "仪器未声明扫描方向；不允许从文件名推断", id));
        } else if (direction == Direction.UNKNOWN) {
            diag.add(Diagnostic.error("diag-" + id + "-dir", "unknown-direction",
                    "扫描方向标记无法识别，保留原值: " + dirRaw, id));
        }
        if (signRaw == null || sign == SignConvention.UNKNOWN) {
            diag.add(Diagnostic.error("diag-" + id + "-sign", "missing-sign-convention",
                    "仪器未声明热流符号约定（exo-up/exo-down），吸放热方向无法判定", id));
        }

        Units tempUnit = Units.fromToken(meta.getOrDefault("temperature-unit", "°C"))
                .orElse(Units.CELSIUS);
        Units hfUnit = Units.fromToken(meta.getOrDefault("heatflow-unit", "mW"))
                .orElse(Units.MILLIWATT);

        List<SampleRow> rows = new ArrayList<>();
        String[] segLabels = new String[lines.length];
        List<String> segNames = new ArrayList<>();
        int idxCol = 0;
        int segCol = headers.indexOf("Segment");
        int tCol = -1;
        int hfCol = -1;
        for (int c = 0; c < headers.size(); c++) {
            String h = headers.get(c).toLowerCase(Locale.ROOT);
            if (h.contains("temp") || h.equals("t")) {
                tCol = c;
            }
            if (h.contains("heat") || h.equals("hf") || h.contains("flow")) {
                hfCol = c;
            }
        }
        if (tCol < 0) {
            tCol = segCol >= 0 ? 2 : 1;
        }
        if (hfCol < 0) {
            hfCol = tCol + 1;
        }

        for (int i = dataStart; i < lines.length; i++) {
            String line = lines[i];
            if (line.isBlank()) {
                continue;
            }
            String[] cells = splitCsv(line.trim());
            int declaredIndex;
            try {
                declaredIndex = Integer.parseInt(cells[Math.min(idxCol, cells.length - 1)].trim());
            } catch (NumberFormatException ex) {
                declaredIndex = rows.size();
            }
            String label = segCol >= 0 && segCol < cells.length ? cells[segCol].trim() : "S1";
            segLabels[declaredIndex] = label;
            if (!segNames.contains(label)) {
                segNames.add(label);
            }
            String rawT = cell(cells, tCol);
            String rawH = cell(cells, hfCol);
            boolean bad = !isNumeric(rawT) || !isNumeric(rawH);
            Double nt = null;
            Double nh = null;
            if (!bad) {
                nt = tempUnit.toCanonical(Double.parseDouble(rawT.replace(",", "")));
                nh = hfUnit.toCanonical(Double.parseDouble(rawH.replace(",", "")));
            }
            rows.add(new SampleRow(declaredIndex, rawT, tempUnit.symbol(), rawH, hfUnit.symbol(),
                    nt, nh, bad));
            if (bad) {
                diag.add(Diagnostic.warn(String.format(Locale.ROOT, "diag-%s-row-%d", id, declaredIndex),
                        "unconvertible-row",
                        "第 " + declaredIndex + " 行含无法转换的值，已保留原始表示", id));
            }
        }

        double[] temps = rows.stream().filter(r -> !r.nonConvertible())
                .mapToDouble(SampleRow::normalizedT).toArray();
        EngineMath.StepStats stats = EngineMath.stepStats(temps);
        if (stats.nonUniform()) {
            diag.add(Diagnostic.warn("diag-" + id + "-nonuniform", "non-uniform-temperature",
                    String.format(Locale.ROOT,
                            "温度采样非等间隔（中位步长 %.4g°C，最大相对偏差 %.1f%%），积分使用非等距梯形法",
                            stats.medianStep(), stats.maxRelDeviation() * 100), id));
        }

        InstrumentOutput out = new InstrumentOutput(id, fileName,
                meta.getOrDefault("instrument", "(unknown)"),
                direction, sign, dirRaw, signRaw, tempUnit, hfUnit,
                rows, diag, stats.nonUniform(), stats.medianStep(),
                null, Instant.now().toString(), sha);
        return new Parsed(out, segLabels, segNames);
    }

    private static String cell(String[] cells, int c) {
        return c < cells.length ? cells[c].trim() : "";
    }

    private static boolean isNumeric(String s) {
        if (s == null || s.isBlank()) {
            return false;
        }
        try {
            Double.parseDouble(s.trim().replace(",", ""));
            return true;
        } catch (NumberFormatException ex) {
            return false;
        }
    }

    static String[] splitCsv(String line) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean inQuote = false;
        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);
            if (ch == '"') {
                inQuote = !inQuote;
            } else if (ch == ',' && !inQuote) {
                out.add(cur.toString());
                cur.setLength(0);
            } else {
                cur.append(ch);
            }
        }
        out.add(cur.toString());
        return out.toArray(String[]::new);
    }

    private static String sha256(String content) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(content.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }
}
