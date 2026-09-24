package dsc.fixtures;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Writes the offline case pack "热转折案：DSC 基线、相变积分与扫描历史审阅器".
 *
 * Deterministic and self-contained: running it regenerates exactly the same
 * fixtures, so the repository carries real DSC-shaped material (per-segment
 * instrument output, declared endo/exo convention, mass, scan rate,
 * atmosphere, calibration) rather than generic tables.
 *
 * Invoked by:  ./gradlew generateCase
 */
public final class GenerateCasePack {

    public static final Path CASE_DIR = Path.of("src/main/resources/cases/pet-history");

    public static void main(String[] args) throws IOException {
        Files.createDirectories(CASE_DIR);

        // Initial state of the sample history ----------------------------------
        write("heat1_instrument.csv", SyntheticDsc.heatingCsv(
                30, 280, 251, 0.004, true, 78, 140, 250, 42, 0.0));
        write("cool1_instrument.csv", SyntheticDsc.coolingCsv(
                40, 270, 231, 0.004, true, 190, 77, 0.0));
        write("heat2_instrument.csv", SyntheticDsc.heatingCsv(
                30, 280, 251, 0.004, true, 80, 138, 249, 101, 0.0));

        // Boundary cases, each replayable on its own branch --------------------
        write("edge_reversed_convention.csv", SyntheticDsc.heatingCsv(
                30, 280, 251, 0.004, false, 78, 140, 250, 202, 0.0));
        write("edge_nonuniform_temperature.csv", SyntheticDsc.heatingCsv(
                30, 280, 251, 0.004, true, 78, 140, 250, 303, 2.5));
        write("edge_overlapping_transitions.csv", SyntheticDsc.overlappingCsv(
                200, 275, 151, 0.003, 404));
        write("edge_cycle_switch.csv", SyntheticDsc.reversingCsv());

        // One fragment carrying a genuinely unparseable row: it must stay visible.
        String bad = SyntheticDsc.heatingCsv(30, 120, 91, 0.003, true, 78, 140, 250, 505, 0.0);
        String[] lines = bad.split("\\R");
        StringBuilder withBad = new StringBuilder(lines[0]).append('\n');
        for (int i = 1; i < lines.length; i++) {
            if (i == 40) withBad.append("N/A,0.002\n");
            else withBad.append(lines[i]).append('\n');
        }
        write("edge_unparseable_cell.csv", withBad.toString());

        write("manifest.json", manifest());
        System.out.println("case pack written to " + CASE_DIR.toAbsolutePath());
    }

    private static String manifest() {
        return """
                {
                  "caseId": "thermal-transition-dsc-review",
                  "title": "热转折案：DSC 基线、相变积分与扫描历史审阅器",
                  "material": "PET granulate (synthetic, deterministic)",
                  "sampleId": "PET-2026-09-19-A",
                  "initialMass": { "value": 8.42, "unit": "mg", "raw": "8.42 mg" },
                  "revisedMass": { "value": 8.37, "unit": "mg", "raw": "8.37 mg", "atEvent": "mass revision" },
                  "scanRate": { "value": 10.0, "unit": "K/min", "raw": "10 K/min" },
                  "atmosphere": "N2, 50 mL/min",
                  "temperatureCalibration": "indium onset 429.75 K; correction +0.2 K applied at import",
                  "narrative": [
                    "导入后先核对仪器输出、质量、扫描速率、气氛、温度标定与切线分别存档",
                    "一次参数修订（切线窗口）",
                    "一次只影响局部依赖的源数据改动（cool1 标定补丁不重算 heat2）",
                    "一次互相冲突的人工决定（两条合法基线）",
                    "质量修订令全部归一化派生过期",
                    "回退到任意旧事件可恢复当时的依赖版本与结论"
                  ],
                  "segments": [
                    { "id": "heat1", "cycle": 1, "direction": "HEATING",
                      "file": "heat1_instrument.csv",
                      "events": [ "GLASS_TRANSITION@70-90C", "CRYSTALLISATION@120-165C", "MELTING@230-270C" ] },
                    { "id": "cool1", "cycle": 1, "direction": "COOLING",
                      "file": "cool1_instrument.csv",
                      "events": [ "CRYSTALLISATION@170-215C" ] },
                    { "id": "heat2", "cycle": 2, "direction": "HEATING",
                      "file": "heat2_instrument.csv",
                      "events": [ "GLASS_TRANSITION@72-92C", "MELTING@228-270C" ] }
                  ],
                  "edgeCases": [
                    { "id": "reversed-heatflow", "file": "edge_reversed_convention.csv",
                      "expect": "吸热方向来自仪器头 ENDO_DOWN；焓符号反向，不靠文件名判断" },
                    { "id": "non-uniform-temperature", "file": "edge_nonuniform_temperature.csv",
                      "expect": "非等间隔温度下使用真实梯形积分并给 INFO 诊断" },
                    { "id": "overlapping-transitions", "file": "edge_overlapping_transitions.csv",
                      "expect": "重叠转折并列保留候选并给 OVERLAPPING_TRANSITIONS 诊断" },
                    { "id": "cycle-switch", "file": "edge_cycle_switch.csv",
                      "expect": "非单调温度序列拒绝整段分析并建议拆分，加热/冷却不混用" },
                    { "id": "mass-revision", "expect": "新质量版本后所有 J/g 派生 STALE" },
                    { "id": "parallel-baselines", "expect": "不同切线窗口产生并列基线，差异保留" },
                    { "id": "empty-interval", "expect": "区间内少于两点 -> EMPTY_INTERVAL，无伪造数值" },
                    { "id": "unparseable-cell", "file": "edge_unparseable_cell.csv",
                      "expect": "N/A 行作为原始行问题保留，不为画图丢弃" }
                  ]
                }
                """;
    }

    private static void write(String name, String content) throws IOException {
        Files.writeString(CASE_DIR.resolve(name), content.replace("\n", System.lineSeparator()),
                StandardCharsets.UTF_8);
    }
}
