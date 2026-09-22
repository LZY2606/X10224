package dsc.casepack;

import dsc.domain.InstrumentOutput;
import dsc.store.CsvDocuments;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * “热转折案：DSC 基线、相变积分与扫描历史审阅器”离线案例包。
 * 资源全部在仓库 src/main/resources/casepack 内，不使用通用表格替代。
 */
public final class CasePack {

    public static final String PACK_NAME = "热转折案：DSC 基线、相变积分与扫描历史审阅器";
    private static final String BASE = "/casepack/";

    private CasePack() {
    }

    public static String readResource(String name) {
        try (InputStream in = CasePack.class.getResourceAsStream(BASE + name)) {
            if (in == null) {
                throw new IllegalStateException("missing casepack resource: " + name);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    /** 从带注释头的案例 CSV 构造仪器输出（逐字保留 CSV 原文）。 */
    public static InstrumentOutput instrument(String outputId, String fileName) {
        String csv = readResource(fileName);
        String model = header(csv, "instrument_model", "UNKNOWN");
        String direction = headerOrNull(csv, "heat_flow_direction");
        String tUnit = header(csv, "temperature_unit", "C");
        String yUnit = header(csv, "heat_flow_unit", "W/g");
        String rateRaw = header(csv, "scan_rate", "10 K/min");
        List<String[]> rows = CsvDocuments.parseRows(csv);
        return new InstrumentOutput(outputId, fileName, model, direction, tUnit, yUnit,
                rateRaw, "N2 50 mL/min", "cal_in", csv, rows);
    }

    private static String header(String csv, String key, String dflt) {
        String v = headerOrNull(csv, key);
        return v == null ? dflt : v;
    }

    private static String headerOrNull(String csv, String key) {
        Matcher m = Pattern.compile("(?m)^#\\s*" + Pattern.quote(key) + "\\s*:\\s*(.*)$").matcher(csv);
        return m.find() ? m.group(1).trim() : null;
    }
}
