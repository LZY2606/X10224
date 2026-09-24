package dsc.store;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * SQLite 打开与迁移。schema v1 为基础表；v2 增加 records.status_detail 与 events.heads（事件时刻的头指针快照）。
 */
public final class Database {
    public static final int SCHEMA = 2;

    public static Connection open(Path path) {
        try {
            Connection c = DriverManager.getConnection("jdbc:sqlite:" + path);
            try (Statement s = c.createStatement()) {
                s.execute("PRAGMA journal_mode=WAL");
                s.execute("PRAGMA foreign_keys=ON");
                s.execute("PRAGMA busy_timeout=5000");
            }
            migrate(c);
            return c;
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public static int version(Connection c) {
        try (Statement s = c.createStatement()) {
            s.execute("CREATE TABLE IF NOT EXISTS schema_version(version INTEGER NOT NULL)");
            try (ResultSet rs = s.executeQuery("SELECT COALESCE(MAX(version),0) FROM schema_version")) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private static void setVersion(Connection c, int v) {
        try (Statement s = c.createStatement()) {
            s.execute("DELETE FROM schema_version");
            s.execute("INSERT INTO schema_version(version) VALUES(" + v + ")");
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    /** v1 基线 DDL，独立暴露以便迁移测试构造旧库。 */
    public static void createV1(Connection c) {
        try (Statement s = c.createStatement()) {
            s.executeUpdate("CREATE TABLE IF NOT EXISTS records(id TEXT NOT NULL, version INTEGER NOT NULL,"
                    + " kind TEXT NOT NULL, type TEXT NOT NULL, base_version INTEGER NOT NULL,"
                    + " inputs TEXT NOT NULL, status TEXT NOT NULL, payload TEXT NOT NULL,"
                    + " created_seq INTEGER NOT NULL, PRIMARY KEY(id, version))");
            s.executeUpdate("CREATE TABLE IF NOT EXISTS heads(id TEXT PRIMARY KEY, version INTEGER NOT NULL)");
            s.executeUpdate("CREATE TABLE IF NOT EXISTS deps(child_id TEXT NOT NULL, child_version INTEGER NOT NULL,"
                    + " parent_id TEXT NOT NULL, parent_version INTEGER NOT NULL)");
            s.executeUpdate("CREATE TABLE IF NOT EXISTS events(seq INTEGER PRIMARY KEY AUTOINCREMENT,"
                    + " id TEXT NOT NULL, type TEXT NOT NULL, actor TEXT NOT NULL, payload TEXT NOT NULL, at TEXT NOT NULL)");
            s.executeUpdate("CREATE TABLE IF NOT EXISTS conflicts(id TEXT PRIMARY KEY, entity_id TEXT NOT NULL,"
                    + " base_version INTEGER NOT NULL, head_version INTEGER NOT NULL, branch_version INTEGER NOT NULL,"
                    + " status TEXT NOT NULL, payload TEXT NOT NULL)");
            s.executeUpdate("CREATE TABLE IF NOT EXISTS pointers(name TEXT PRIMARY KEY, id TEXT NOT NULL, version INTEGER NOT NULL)");
            setVersion(c, 1);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public static void migrate(Connection c) {
        int v = version(c);
        try (Statement s = c.createStatement()) {
            if (v < 1) {
                createV1(c);
                v = 1;
            }
            if (v < 2) {
                s.executeUpdate("ALTER TABLE records ADD COLUMN status_detail TEXT NOT NULL DEFAULT ''");
                s.executeUpdate("ALTER TABLE events ADD COLUMN heads TEXT NOT NULL DEFAULT '{}'");
                setVersion(c, 2);
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private Database() {}
}
