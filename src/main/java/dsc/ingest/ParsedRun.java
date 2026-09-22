package dsc.ingest;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** CSV 仪器输出的解析结果：表头键值、列表头、原始行全部保留。 */
public record ParsedRun(
        String fileName,
        Map<String, String> header,
        List<String> columnNames,
        List<String[]> rows,
        List<String> rawHeaderLines
) {

    public String headerOr(String key, String fallback) {
        return header.getOrDefault(key, fallback);
    }

    public int columnIndex(String name) {
        for (int i = 0; i < columnNames.size(); i++) {
            if (columnNames.get(i).equalsIgnoreCase(name)) {
                return i;
            }
        }
        return -1;
    }

    public double[][] column(String... candidates) {
        int idx = -1;
        for (String name : candidates) {
            idx = columnIndex(name);
            if (idx >= 0) {
                break;
            }
        }
        if (idx < 0) {
            throw new IllegalArgumentException("缺少数据列，候选: " + String.join("/", candidates));
        }
        double[][] out = new double[rows.size()][2];
        int target = idx;
        for (int i = 0; i < rows.size(); i++) {
            out[i][0] = i;
            out[i][1] = Double.parseDouble(rows.get(i)[target].trim());
        }
        return out;
    }

    public static Map<String, String> emptyHeader() {
        return new LinkedHashMap<>();
    }
}
