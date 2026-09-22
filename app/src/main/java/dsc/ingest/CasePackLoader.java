package dsc.ingest;

import com.fasterxml.jackson.databind.ObjectMapper;
import dsc.domain.Models.AtmosphereRecord;
import dsc.domain.Models.Direction;
import dsc.domain.Models.MassRecord;
import dsc.domain.Models.Point;
import dsc.domain.Models.Run;
import dsc.domain.Models.Tangent;
import dsc.domain.Models.TempCalibration;
import dsc.domain.Q;
import dsc.domain.Units;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 读取“热转折案”案例包。所有原始表示原样保留；只在派生层规范化。
 * 无法转换的值保留在 Point/记录上并附 Q.error，同时进 diagnostics。
 */
public final class CasePackLoader {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private CasePackLoader() {
    }

    public static CasePack loadFromDirectory(Path dir) throws IOException {
        CasePack pack = new CasePack();
        Path manifest = dir.resolve("manifest.json");
        if (!Files.isRegularFile(manifest)) {
            throw new IOException("missing manifest.json in case pack " + dir);
        }
        try (InputStream in = Files.newInputStream(manifest)) {
            @SuppressWarnings("unchecked")
            Map<String, Object> meta = MAPPER.readValue(in, Map.class);
            pack.manifestVersion = str(meta.get("manifestVersion"));
            pack.caseId = str(meta.get("caseId"));
            pack.title = str(meta.get("title"));
            pack.sampleId = str(meta.get("sampleId"));
            pack.description = str(meta.get("description"));
        }

        Path runsDir = dir.resolve("runs");
        if (Files.isDirectory(runsDir)) {
            List<Path> runFiles = new ArrayList<>();
            try (var stream = Files.list(runsDir)) {
                stream.filter(p -> p.getFileName().toString().endsWith(".csv"))
                        .sorted().forEach(runFiles::add);
            }
            for (Path runFile : runFiles) {
                pack.runs.add(parseRun(runFile, pack));
            }
        }

        Path massFile = dir.resolve("mass.csv");
        if (Files.isRegularFile(massFile)) {
            for (CsvRow row : readCsv(massFile)) {
                if (row.skip) {
                    continue;
                }
                MassRecord rec = new MassRecord();
                rec.id = row.get("id");
                rec.sampleId = fallback(row.get("sampleId"), pack.sampleId);
                rec.since = row.get("since");
                rec.note = row.get("note");
                rec.mass = Units.mass(row.get("mass"), row.get("massUnit"));
                if (!rec.mass.valid()) {
                    pack.diagnostics.add("mass " + rec.id + ": " + rec.mass.error()
                            + " (raw='" + rec.mass.raw() + "') — kept in place, normalization skipped");
                }
                pack.masses.add(rec);
            }
        }

        Path atmFile = dir.resolve("atmosphere.csv");
        if (Files.isRegularFile(atmFile)) {
            for (CsvRow row : readCsv(atmFile)) {
                if (row.skip) {
                    continue;
                }
                AtmosphereRecord rec = new AtmosphereRecord();
                rec.id = row.get("id");
                rec.sampleId = fallback(row.get("sampleId"), pack.sampleId);
                rec.gas = row.get("gas");
                String flow = row.get("flowRate");
                String flowUnit = row.get("flowUnit");
                if (flow != null && flowUnit != null) {
                    rec.flowRate = Q.of(parseOrNaN(flow), flow, flowUnit);
                }
                rec.note = row.get("note");
                pack.atmospheres.add(rec);
            }
        }

        Path calFile = dir.resolve("calibration.csv");
        if (Files.isRegularFile(calFile)) {
            for (CsvRow row : readCsv(calFile)) {
                if (row.skip) {
                    continue;
                }
                TempCalibration rec = new TempCalibration();
                rec.id = row.get("id");
                rec.sampleId = fallback(row.get("sampleId"), pack.sampleId);
                rec.standard = row.get("standard");
                rec.appliedToRunId = row.get("runId");
                String corr = row.get("correctionK");
                rec.correction = corr == null ? null : Q.of(parseOrNaN(corr), corr, "K");
                pack.calibrations.add(rec);
            }
        }

        Path tangentFile = dir.resolve("tangents.csv");
        if (Files.isRegularFile(tangentFile)) {
            for (CsvRow row : readCsv(tangentFile)) {
                if (row.skip) {
                    continue;
                }
                Tangent t = new Tangent();
                t.id = row.get("id");
                t.runId = row.get("runId");
                t.intervalId = row.get("intervalId");
                t.side = row.get("side");
                String at = row.get("anchorTemp");
                String atu = fallback(row.get("anchorTempUnit"), "K");
                t.anchorTemperature = at == null ? null : Units.temperature(at, atu);
                String ah = row.get("anchorHeatFlow");
                String ahu = row.get("anchorHeatFlowUnit");
                t.anchorHeatFlow = ah == null ? null : Units.heatFlow(ah, ahu);
                String slope = row.get("slope");
                t.slope = slope == null ? 0.0 : parseOrNaN(slope);
                t.note = row.get("note");
                pack.tangents.add(t);
            }
        }
        return pack;
    }

    private static Run parseRun(Path file, CasePack pack) throws IOException {
        Run run = new Run();
        run.id = stripCsv(file.getFileName().toString());
        List<String> headerCols = new ArrayList<>();
        List<Point> points = new ArrayList<>();
        String pendingScanRate = null;
        for (String rawLine : Files.readAllLines(file, StandardCharsets.UTF_8)) {
            String line = rawLine.strip();
            if (line.isEmpty()) {
                continue;
            }
            if (line.startsWith("#")) {
                int colon = line.indexOf(':');
                if (colon > 0) {
                    String key = line.substring(1, colon).trim();
                    String value = line.substring(colon + 1).trim();
                    if ("scanRate".equals(key) || "scanRateValue".equals(key)) {
                        pendingScanRate = value;
                    } else if ("scanRateUnit".equals(key)) {
                        Integer dirHint = run.declaredDirection == Direction.HEATING ? 1
                                : run.declaredDirection == Direction.COOLING ? -1 : null;
                        run.scanRate = Units.scanRate(
                                pendingScanRate == null ? "0" : pendingScanRate, value, dirHint);
                    } else {
                        applyHeader(run, key, value);
                    }
                }
                continue;
            }
            String[] cells = line.split(",", -1);
            if (headerCols.isEmpty()) {
                for (String c : cells) {
                    headerCols.add(c.trim());
                }
                continue;
            }
            CsvRow row = new CsvRow(headerCols, cells, false);
            String tRaw = row.get("time");
            String tempRaw = row.get("temperature");
            String hfRaw = row.get("heatFlow");
            Q t = tRaw == null ? Q.bad("", run.timeUnit, "missing time")
                    : Units.seconds(tRaw, run.timeUnit);
            Q temp = tempRaw == null ? Q.bad("", run.tempUnit, "missing temperature")
                    : Units.temperature(tempRaw, run.tempUnit);
            Q hf = hfRaw == null ? Q.bad("", run.heatFlowUnit, "missing heat flow")
                    : Units.heatFlow(hfRaw, run.heatFlowUnit);
            if (!t.valid()) {
                pack.diagnostics.add(run.id + " point#" + points.size() + " time: " + t.error());
            }
            if (!temp.valid()) {
                pack.diagnostics.add(run.id + " point#" + points.size()
                        + " temperature: " + temp.error() + " (raw='" + temp.raw() + "')");
            }
            if (!hf.valid()) {
                pack.diagnostics.add(run.id + " point#" + points.size()
                        + " heatFlow: " + hf.error() + " (raw='" + hfRaw + "')");
            }
            points.add(new Point(t, temp, hf));
        }
        run.points = points;
        if (run.sampleId == null) {
            run.sampleId = pack.sampleId;
        }
        run.heatFlowFamily = Units.heatFlowFamily(run.heatFlowUnit);
        return run;
    }

    private static void applyHeader(Run run, String key, String value) {
        switch (key) {
            case "id" -> run.id = value;
            case "label" -> run.label = value;
            case "cycleTag" -> run.cycleTag = value;
            case "sampleId" -> run.sampleId = value;
            case "instrument" -> run.instrument = value;
            case "fileHint" -> run.fileHint = value;
            case "tempUnit" -> run.tempUnit = value;
            case "timeUnit" -> run.timeUnit = value;
            case "heatFlowUnit" -> run.heatFlowUnit = value;
            case "declaredDirection" -> run.declaredDirection = parseDirection(value);
            default -> {
                // 忽略未知头键，保留原始文件本身（存档层不丢信息）
            }
        }
    }

    private static Direction parseDirection(String value) {
        if (value == null) {
            return Direction.UNKNOWN;
        }
        return switch (value.trim().toLowerCase()) {
            case "heating", "heat", "up" -> Direction.HEATING;
            case "cooling", "cool", "down" -> Direction.COOLING;
            case "isothermal" -> Direction.ISOTHERMAL;
            default -> Direction.UNKNOWN;
        };
    }

    private static List<CsvRow> readCsv(Path file) throws IOException {
        List<CsvRow> rows = new ArrayList<>();
        List<String> header = new ArrayList<>();
        boolean first = true;
        for (String rawLine : Files.readAllLines(file, StandardCharsets.UTF_8)) {
            String line = rawLine.strip();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            String[] cells = line.split(",", -1);
            if (first) {
                for (String c : cells) {
                    header.add(c.trim());
                }
                first = false;
            } else {
                rows.add(new CsvRow(header, cells, false));
            }
        }
        return rows;
    }

    private static String str(Object o) {
        return o == null ? null : o.toString();
    }

    private static String fallback(String value, String fallbackValue) {
        return value == null || value.isBlank() ? fallbackValue : value;
    }

    private static double parseOrNaN(String s) {
        try {
            return Double.parseDouble(s.trim());
        } catch (NumberFormatException e) {
            return Double.NaN;
        }
    }

    private static String stripCsv(String name) {
        return name.endsWith(".csv") ? name.substring(0, name.length() - 4) : name;
    }

    private static final class CsvRow {
        private final List<String> header;
        private final String[] cells;
        private final boolean skip;

        private CsvRow(List<String> header, String[] cells, boolean skip) {
            this.header = header;
            this.cells = cells;
            this.skip = skip;
        }

        private String get(String key) {
            int idx = header.indexOf(key);
            if (idx < 0 || idx >= cells.length) {
                return null;
            }
            String v = cells[idx].trim();
            return v.isEmpty() ? null : v;
        }
    }
}
