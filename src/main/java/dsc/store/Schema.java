package dsc.store;

/**
 * SQLite schema。事实层是只增的 events 表；sources 保存六类来源文档（含仪器输出逐字 CSV）。
 * meta 保存当前 schema 版本与项目级修订标记（乐观并发）。
 */
public final class Schema {

    public static final int CURRENT_VERSION = 2;

    public static final String V1 = """
            CREATE TABLE IF NOT EXISTS meta (
              key TEXT PRIMARY KEY,
              value TEXT NOT NULL
            );
            CREATE TABLE IF NOT EXISTS events (
              seq INTEGER PRIMARY KEY,
              type TEXT NOT NULL,
              actor TEXT NOT NULL,
              basis TEXT NOT NULL,
              payload TEXT NOT NULL,
              timestamp TEXT NOT NULL
            );
            CREATE TABLE IF NOT EXISTS sources (
              kind TEXT NOT NULL,
              source_id TEXT NOT NULL,
              version INTEGER NOT NULL,
              doc TEXT NOT NULL,
              PRIMARY KEY (kind, source_id, version)
            );
            """;

    public static final String V2 = """
            ALTER TABLE sources ADD COLUMN content_hash TEXT NOT NULL DEFAULT '';
            CREATE INDEX IF NOT EXISTS idx_events_type ON events(type);
            """;

    private Schema() {
    }
}
