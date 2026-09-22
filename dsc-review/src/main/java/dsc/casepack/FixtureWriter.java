package dsc.casepack;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Writes the committed offline domain materials under
 * resources/fixtures/thermal-turning-case. These are DSC instrument outputs
 * with headers (convention/rate/cycle), not generic tables.
 */
public final class FixtureWriter {

    public record CsvFile(String name, String header, List<SyntheticDsc.Row> rows) {}

    private FixtureWriter() {}

    public static List<CsvFile> files() {
        return List.of(
                new CsvFile("DSC_run_heating.txt",
                        "# instrument=DSC-Q2000; direction=data-derived; convention=exo-up; "
                                + "cycle=1; rate=10.0 °C/min; t-unit=°C; hf-unit=mW",
                        SyntheticDsc.heatingCurve(40, 190, false, false)),
                new CsvFile("DSC_run_cooling.txt",
                        "# instrument=DSC-Q2000; direction=data-derived; convention=exo-up; "
                                + "cycle=1; rate=-10.0 °C/min; t-unit=°C; hf-unit=mW",
                        SyntheticDsc.coolingCurve(190, 40, false)),
                new CsvFile("reversed_run_01.txt",
                        "# instrument=DSC-Q2000; direction=data-derived; convention=unknown; "
                                + "cycle=2; rate=10.0 °C/min; t-unit=°C; hf-unit=mW; note=reversed-signal-wiring",
                        SyntheticDsc.heatingCurve(40, 190, false, true)),
                new CsvFile("rerun_heating_cycle2.txt",
                        "# instrument=DSC-Q2000; direction=data-derived; convention=exo-up; "
                                + "cycle=2; rate=10.0 °C/min; t-unit=°C; hf-unit=mW; note=non-uniform-grid",
                        SyntheticDsc.withBadCells(SyntheticDsc.heatingCurve(40, 190, true, false), 50, 51)),
                new CsvFile("overlap_run.txt",
                        "# instrument=DSC-Q2000; direction=data-derived; convention=exo-up; "
                                + "cycle=2; rate=10.0 °C/min; t-unit=°C; hf-unit=mW; note=overlapping-melts",
                        SyntheticDsc.overlappingCurve()));
    }

    public static void writeAll(Path dir) throws IOException {
        Files.createDirectories(dir);
        for (CsvFile f : files()) {
            StringBuilder sb = new StringBuilder();
            sb.append(f.header).append('\n');
            sb.append("# temperature_C, heatFlow_mW\n");
            for (SyntheticDsc.Row r : f.rows) sb.append(r.t).append(',').append(r.hf).append('\n');
            Files.writeString(dir.resolve(f.name), sb.toString(), StandardCharsets.UTF_8);
        }
        Files.writeString(dir.resolve("README.txt"), README, StandardCharsets.UTF_8);
    }

    private static final String README = """
            热转折案：DSC 基线、相变积分与扫描历史审阅器 —— 离线案例材料

            本目录是领域材料，不是通用表格。每个仪器输出保留头部元数据：
              instrument / direction=data-derived / convention / cycle / rate / units

            文件清单：
              DSC_run_heating.txt        cycle 1 加热：玻璃化 ~72°C、冷结晶 ~115°C、熔融 ~155°C
              DSC_run_cooling.txt       cycle 1 冷却：结晶 ~140°C
              reversed_run_01.txt       反向热流且 convention=unknown（符号判定不得靠文件名）
              rerun_heating_cycle2.txt  cycle 2 加热：非等间隔温度，含两个无法转换的单元格(n/a)
              overlap_run.txt           重叠转折：151/156.5°C 双熔融峰

            规则：方向由温度序列计算；加热与冷却段不混用；所有量携带单位；
            无法转换的值就地保留诊断，不为画图丢弃。
            """;
}
