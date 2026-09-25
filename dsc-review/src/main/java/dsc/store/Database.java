package dsc.store;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * SQLite 连接管理与 schema 迁移。
 * 旧版本数据库会顺序迁移到当前版本（测试覆盖 v1 -> v2）。
 */
public final class Database implements AutoCloseable {

    private final Connection connection;

    private Database(Connection connection) {
        this.connection = connection;
    }

    public static Database open(String path) {
        try {
            Class.forName("org.sqlite.JDBC");
            String url = "jdbc:sqlite:" + path;
            Connection conn = DriverManager.getConnection(url);
            conn.setAutoCommit(false);
            try (Statement st = conn.createStatement()) {
                st.execute("PRAGMA journal_mode=WAL");
                st.execute("PRAGMA foreign_keys=ON");
                st.execute("PRAGMA busy_timeout=5000");
                st.execute("PRAGMA synchronous=FULL");
            }
            conn.commit();
            Database db = new Database(conn);
            db.migrate();
            return db;
        } catch (ClassNotFoundException | SQLException e) {
            throw new IllegalStateException("cannot open database: " + path, e);
        }
    }

    public Connection connection() {
        return connection;
    }

    public void migrate() {
        try {
            int version = currentVersion();
            if (version == 0) {
                try (Statement st = connection.createStatement()) {
                    for (String ddl : Schema.V1_DDL) {
                        st.execute(ddl);
                    }
                    for (String ddl : Schema.V2_DDL) {
                        st.execute(ddl);
                    }
                }
                setVersion(Schema.CURRENT_VERSION);
                connection.commit();
                return;
            }
            if (version < 2) {
                try (Statement st = connection.createStatement()) {
                    for (String ddl : Schema.V2_DDL) {
                        st.execute(ddl);
                    }
                }
                setVersion(2);
            }
            connection.commit();
        } catch (SQLException e) {
            try {
                connection.rollback();
            } catch (SQLException ignored) {
            }
            throw new IllegalStateException("migration failed", e);
        }
    }

    private int currentVersion() throws SQLException {
        try (Statement st = connection.createStatement();
             ResultSet rs = st.executeQuery("PRAGMA user_version")) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    private void setVersion(int v) throws SQLException {
        try (Statement st = connection.createStatement()) {
            st.execute("PRAGMA user_version=" + v);
        }
    }

    public void rollback() {
        try {
            connection.rollback();
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    public void commit() {
        try {
            connection.commit();
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public void close() {
        try {
            connection.close();
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }
}
