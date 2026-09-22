package dsc.ingest;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 仪器 CSV：
 * <pre>
 * sample_id: S1
 * sequence: R1
 * declared_direction: heating
 * sign_convention: exo_up
 * ---
 * index,t,T,heatflow,...
 * 0,0,25,...
 * </pre>
 * 表头原始行逐字保留进来源档案；分隔符 {@code ---} 之后是数据表。
 */
public final class CsvRunParser {

    private CsvRunParser() {
    }

    public static ParsedRun parse(String fileName, String content) {
        String[] lines = content.split("\\r?\\n");
        Map<String, String> header = new LinkedHashMap<>();
        List<String> rawHeaderLines = new ArrayList<>();
        int dataStart = 0;
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.equals("---")) {
                dataStart = i + 1;
                break;
            }
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            int colon = line.indexOf(':');
            if (colon > 0 && !looksNumeric(line)) {
                String key = line.substring(0, colon).trim();
                String value = line.substring(colon + 1).trim();
                header.put(key, value);
                rawHeaderLines.add(line);
            }
        }
        List<String> columns = new ArrayList<>();
        for (String col : lines[dataStart].split(",")) {
            columns.add(col.trim());
        }
        List<String[]> rows = new ArrayList<>();
        for (int i = dataStart + 1; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.isEmpty()) {
                continue;
            }
            rows.add(line.split(","));
        }
        return new ParsedRun(fileName, header, columns, rows, rawHeaderLines);
    }

    private static boolean looksNumeric(String line) {
        String first = line.split(",")[0].trim();
        try {
            Double.parseDouble(first);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }
}
