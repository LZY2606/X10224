package dsc.store;

/** SQLite schema. Entity rows are versioned snapshots keyed by stable domain identity. */
public final class Schema {
    public static final int CURRENT_VERSION = 2;

    public static final String V1 = """
        CREATE TABLE IF NOT EXISTS meta (
            key TEXT PRIMARY KEY,
            value TEXT NOT NULL
        );
        CREATE TABLE IF NOT EXISTS samples (
            id TEXT NOT NULL,
            version INTEGER NOT NULL,
            name TEXT NOT NULL,
            material TEXT NOT NULL,
            event_seq INTEGER NOT NULL,
            PRIMARY KEY (id, version)
        );
        CREATE TABLE IF NOT EXISTS segments (
            id TEXT NOT NULL,
            version INTEGER NOT NULL,
            sample_id TEXT NOT NULL,
            cycle_id TEXT NOT NULL,
            label TEXT NOT NULL,
            ord INTEGER NOT NULL,
            direction TEXT NOT NULL,
            event_seq INTEGER NOT NULL,
            PRIMARY KEY (id, version)
        );
        CREATE TABLE IF NOT EXISTS sources (
            id TEXT NOT NULL,
            version INTEGER NOT NULL,
            sample_id TEXT NOT NULL,
            segment_id TEXT,
            kind TEXT NOT NULL,
            label TEXT NOT NULL,
            raw_text TEXT NOT NULL,
            unit TEXT,
            ref TEXT,
            origin TEXT NOT NULL,
            metadata TEXT NOT NULL DEFAULT '{}',
            event_seq INTEGER NOT NULL,
            PRIMARY KEY (id, version)
        );
        CREATE TABLE IF NOT EXISTS intervals (
            id TEXT NOT NULL,
            version INTEGER NOT NULL,
            segment_id TEXT NOT NULL,
            label TEXT NOT NULL,
            start_k REAL NOT NULL,
            end_k REAL NOT NULL,
            event_seq INTEGER NOT NULL,
            PRIMARY KEY (id, version)
        );
        CREATE TABLE IF NOT EXISTS baselines (
            id TEXT NOT NULL,
            version INTEGER NOT NULL,
            interval_id TEXT NOT NULL,
            kind TEXT NOT NULL,
            coef_a REAL NOT NULL,
            coef_b REAL NOT NULL,
            coef_c REAL NOT NULL,
            join_k REAL,
            rms_w REAL,
            decision TEXT NOT NULL,
            decided_by_event TEXT,
            rationale TEXT NOT NULL,
            event_seq INTEGER NOT NULL,
            PRIMARY KEY (id, version)
        );
        CREATE TABLE IF NOT EXISTS features (
            id TEXT NOT NULL,
            version INTEGER NOT NULL,
            baseline_id TEXT NOT NULL,
            kind TEXT NOT NULL,
            onset_k REAL NOT NULL,
            peak_k REAL NOT NULL,
            endset_k REAL NOT NULL,
            energy_j REAL NOT NULL,
            enthalpy_jkg REAL NOT NULL,
            error_sources TEXT NOT NULL,
            annotated TEXT NOT NULL,
            stale INTEGER NOT NULL DEFAULT 0,
            event_seq INTEGER NOT NULL,
            PRIMARY KEY (id, version)
        );
        CREATE TABLE IF NOT EXISTS events (
            seq INTEGER PRIMARY KEY AUTOINCREMENT,
            id TEXT NOT NULL UNIQUE,
            type TEXT NOT NULL,
            sample_id TEXT,
            subject_id TEXT,
            payload TEXT NOT NULL,
            parent_event_id TEXT,
            actor TEXT NOT NULL,
            created_at_ms INTEGER NOT NULL
        );
        CREATE TABLE IF NOT EXISTS current_pointer (
            entity_id TEXT PRIMARY KEY,
            version INTEGER NOT NULL,
            kind TEXT NOT NULL
        );
        """;

    /** v2 adds per-feature dependency hashes so the graph can explain staleness precisely. */
    public static final String V2 = """
        ALTER TABLE features ADD COLUMN deps_hash TEXT;
        """;

    private Schema() {}
}
