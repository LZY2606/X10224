package dsc.store;

import java.util.ArrayList;
import java.util.List;

/** 最小 CSV 解析：跳过 # 注释行与表头（首列非数字），逐字保留数据行原文。 */
public final class CsvDocuments {

    private CsvDocuments() {
    }

    public static List<String[]> parseRows(String csv) {
        List<String[]> rows = new ArrayList<>();
        for (String line : csv.split("\\r?\\n", -1)) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                continue;
            }
            String[] cols = trimmed.split(",", -1);
            String first = cols[0].trim();
            try {
                Double.parseDouble(first);
            } catch (NumberFormatException e) {
                continue; // 表头
            }
            String[] data = new String[cols.length];
            for (int i = 0; i < cols.length; i++) {
                data[i] = cols[i].trim();
            }
            rows.add(data);
        }
        return rows;
    }
}
