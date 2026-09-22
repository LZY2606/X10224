package dsc.store;

import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/** 建库与 v1 -> v2 迁移。迁移脚本与 fixture 都在仓库内。 */
public final class Schema {

    public static final int CURRENT_VERSION = 2;

    private Schema() {
    }

    public static void init(Connection connection) throws SQLException {
        int version = userVersion(connection);
        if (version == 0) {
            applyResource(connection, "/db/schema_v2.sql");
            return;
        }
        if (version < 2) {
            applyResource(connection, "/db/schema_v1_delta.sql");
            try (Statement statement = connection.createStatement()) {
                statement.execute("PRAGMA user_version = 2");
            }
        }
    }

    /** 供迁移测试：显式建立旧版 v1 库。 */
    public static void createLegacyV1(Connection connection) throws SQLException {
        applyResource(connection, "/db/schema_v1.sql");
        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA user_version = 1");
        }
    }

    static int userVersion(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement();
             var rs = statement.executeQuery("PRAGMA user_version")) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    private static void applyResource(Connection connection, String resource) throws SQLException {
        String sql;
        try {
            sql = new String(Schema.class.getResourceAsStream(resource).readAllBytes());
        } catch (IOException e) {
            throw new IllegalStateException("缺少 schema 资源 " + resource, e);
        }
        try (Statement statement = connection.createStatement()) {
            for (String part : sql.split(";")) {
                String trimmed = part.trim();
                if (!trimmed.isEmpty() && !trimmed.startsWith("--")) {
                    statement.execute(trimmed);
                }
            }
        }
    }
}
