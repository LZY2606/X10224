package dsc.domain;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * One imported instrument output. Original tokens are retained verbatim; the
 * parallel arrays in SI units contain null where conversion failed.
 */
public record InstrumentSeries(
        String runId,
        String fileName,
        List<String> headerLines,
        Map<String, String> metadata,
        List<String> columnNames,
        List<String[]> rawRows,
        int timeColumn,
        int temperatureColumn,
        int heatFlowColumn,
        double[] timeS,
        Double[] temperatureK,
        Double[] heatFlowW,
        List<Map<String, Object>> diagnostics
) {

    public int pointCount() {
        return rawRows.size();
    }

    public Map<String, Object> summary() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("runId", runId);
        m.put("fileName", fileName);
        m.put("pointCount", pointCount());
        m.put("columns", columnNames);
        m.put("metadata", metadata);
        m.put("diagnostics", diagnostics);
        return m;
    }
}
