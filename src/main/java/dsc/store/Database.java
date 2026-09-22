package dsc.store;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * SQLite 打开、模式创建与旧版本迁移。
 * 迁移按 schema_version 顺序执行；旧库打开时自动升级到当前版本。
 */
public final class Database {
    public static final int CURRENT_SCHEMA = 2;

    private Database() {}

    public static Connection open(String path) {
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
            throw new RuntimeException("open database " + path, e);
        }
    }

    static int schemaVersion(Connection c) throws SQLException {
        try (Statement s = c.createStatement()) {
            s.execute("CREATE TABLE IF NOT EXISTS schema_version(version INTEGER NOT NULL)");
            var rs = s.executeQuery("SELECT COALESCE(MAX(version),0) FROM schema_version");
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    public static void migrate(Connection c) throws SQLException {
        int v = schemaVersion(c);
        if (v < 1) {
            try (Statement s = c.createStatement()) {
                // v1 基线模式：来源、派生、指针、事件分别存档
                s.execute("CREATE TABLE IF NOT EXISTS source_record(" +
                        "id TEXT PRIMARY KEY, kind TEXT NOT NULL, version INTEGER NOT NULL," +
                        "state TEXT NOT NULL, payload TEXT NOT NULL, created_by_event TEXT)");
                s.execute("CREATE TABLE IF NOT EXISTS derivation(" +
                        "id TEXT PRIMARY KEY, kind TEXT NOT NULL, node_key TEXT NOT NULL, version INTEGER NOT NULL," +
                        "state TEXT NOT NULL, inputs TEXT NOT NULL, payload TEXT NOT NULL, created_by_event TEXT)");
                s.execute("CREATE TABLE IF NOT EXISTS current_source(kind TEXT PRIMARY KEY, record_id TEXT NOT NULL)");
                s.execute("CREATE TABLE IF NOT EXISTS current_derivation(node_key TEXT PRIMARY KEY, derivation_id TEXT NOT NULL)");
                s.execute("CREATE TABLE IF NOT EXISTS event(" +
                        "id TEXT PRIMARY KEY, seq INTEGER NOT NULL UNIQUE, ts TEXT NOT NULL," +
                        "actor TEXT NOT NULL, type TEXT NOT NULL, payload TEXT NOT NULL, causes TEXT NOT NULL)");
                s.execute("CREATE TABLE IF NOT EXISTS counters(name TEXT PRIMARY KEY, value INTEGER NOT NULL)");
                s.execute("INSERT OR REPLACE INTO schema_version(version) VALUES(1)");
            }
        }
        if (v < 2) {
            try (Statement s = c.createStatement()) {
                // v2：冲突分支与诊断分别存档
                s.execute("CREATE TABLE IF NOT EXISTS conflict(" +
                        "id TEXT PRIMARY KEY, base_ref TEXT NOT NULL, branches TEXT NOT NULL," +
                        "status TEXT NOT NULL, resolution TEXT, created_event TEXT)");
                s.execute("CREATE TABLE IF NOT EXISTS diagnostic(" +
                        "id TEXT PRIMARY KEY, owner_ref TEXT NOT NULL, severity TEXT NOT NULL," +
                        "code TEXT NOT NULL, message TEXT NOT NULL, context TEXT NOT NULL)");
                s.execute("INSERT OR REPLACE INTO schema_version(version) VALUES(2)");
            }
        }
    }

    /** 启动恢复：写入中断留下的 building 行整体清除，指针保持指向旧完整版本。 */
    public static void recoverInterruptedWrites(Connection c) {
        try (Statement s = c.createStatement()) {
            s.execute("DELETE FROM source_record WHERE state='building'");
            s.execute("DELETE FROM derivation WHERE state='building'");
        } catch (SQLException e) {
            throw new RuntimeException("recover", e);
        }
    }
}
