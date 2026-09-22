package org.dsc.store;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 整库导出/导入：删除本地数据库后导入，事件身份、顺序与实体版本/依赖关系必须完全复原。
 * 导出格式按表名 -> 行数组，行按列名取值；导入用显式列插入，不依赖行序以外的任何状态。
 */
public final class Archive {
    private static final ObjectMapper M = new ObjectMapper();
    private static final List<String> TABLES = List.of(
            "events", "sources", "entities", "entity_pointers", "deps");

    private Archive() {}

    public static ObjectNode exportAll(Store store) {
        ObjectNode root = M.createObjectNode();
        root.put("format", "dsc-reviewer-archive");
        root.put("schemaVersion", Schema.CURRENT_VERSION);
        try (Statement st = store.connection().createStatement()) {
            for (String table : TABLES) {
                ArrayNode arr = M.createArrayNode();
                try (ResultSet rs = st.executeQuery("SELECT * FROM " + table)) {
                    ResultSetMetaData md = rs.getMetaData();
                    int n = md.getColumnCount();
                    while (rs.next()) {
                        ObjectNode row = M.createObjectNode();
                        for (int c = 1; c <= n; c++) {
                            String col = md.getColumnName(c);
                            String v = rs.getString(c);
                            if (v == null) row.putNull(col);
                            else row.put(col, v);
                        }
                        arr.add(row);
                    }
                }
                root.set(table, arr);
            }
        } catch (Exception e) {
            throw new Store.StoreException("export failed", e);
        }
        return root;
    }

    /** 导入到空库（或清空后）。events 按 seq 排序插入，保持来源链 parent 关系。 */
    public static void importAll(Store store, JsonNode root) {
        store.tx(() -> {
            try (Statement st = store.connection().createStatement()) {
                for (String t : TABLES) st.executeUpdate("DELETE FROM " + t);
                for (String table : TABLES) {
                    JsonNode rows = root.get(table);
                    if (rows == null) continue;
                    for (JsonNode r : rows) {
                        LinkedHashMap<String, String> cols = new LinkedHashMap<>();
                        r.fieldNames().forEachRemaining(c -> cols.put(c,
                                r.get(c).isNull() ? null : r.get(c).asText()));
                        String names = String.join(",", cols.keySet());
                        String qs = String.join(",", cols.keySet().stream().map(x -> "?").toList());
                        try (PreparedStatement ps = store.connection().prepareStatement(
                                "INSERT INTO " + table + "(" + names + ") VALUES(" + qs + ")")) {
                            int i = 0;
                            for (String v : cols.values()) {
                                ps.setString(++i, v);
                            }
                            ps.executeUpdate();
                        }
                    }
                }
            }
        });
    }

    public static String toJson(ObjectNode root) {
        try { return M.writerWithDefaultPrettyPrinter().writeValueAsString(root); }
        catch (Exception e) { throw new Store.StoreException("serialize failed", e); }
    }

    public static JsonNode parseJson(String text) {
        try { return M.readTree(text); }
        catch (Exception e) { throw new Store.StoreException("parse archive failed", e); }
    }
}
