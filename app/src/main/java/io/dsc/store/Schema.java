package io.dsc.store;

/**
 * SQLite schema. Version 1. Migrations run inside the open transaction and are
 * recorded in schema_meta; opening an older file upgrades it without losing
 * events.
 */
public final class Schema {
    private Schema() {}

    public static final int CURRENT_VERSION = 1;

    public static final String CREATE = """
        CREATE TABLE IF NOT EXISTS schema_meta (
            key TEXT PRIMARY KEY,
            value TEXT NOT NULL
        );

        CREATE TABLE IF NOT EXISTS cases (
            case_id TEXT PRIMARY KEY,
            title TEXT NOT NULL,
            material TEXT NOT NULL,
            created_at TEXT NOT NULL,
            rule_version TEXT NOT NULL,
            tags TEXT NOT NULL,
            current_branch TEXT NOT NULL
        );

        CREATE TABLE IF NOT EXISTS branches (
            branch_id TEXT PRIMARY KEY,
            case_id TEXT NOT NULL,
            name TEXT NOT NULL,
            parent_branch_id TEXT,
            fork_after_seq INTEGER,
            resolution TEXT,
            resolution_event_id TEXT,
            tags TEXT NOT NULL DEFAULT '[]'
        );

        CREATE TABLE IF NOT EXISTS events (
            event_id TEXT PRIMARY KEY,
            case_id TEXT NOT NULL,
            branch_id TEXT NOT NULL,
            seq INTEGER NOT NULL,
            type TEXT NOT NULL,
            occurred_at INTEGER NOT NULL,
            actor TEXT NOT NULL,
            payload TEXT NOT NULL,
            prev_hash TEXT,
            hash TEXT NOT NULL,
            UNIQUE(branch_id, seq)
        );
        CREATE INDEX IF NOT EXISTS idx_events_branch ON events(branch_id, seq);

        -- raw archived bodies of direct sources (instrument outputs, mass texts...)
        CREATE TABLE IF NOT EXISTS raw_bodies (
            ref TEXT PRIMARY KEY,
            case_id TEXT NOT NULL,
            kind TEXT NOT NULL,
            content TEXT NOT NULL,
            media_type TEXT NOT NULL
        );

        -- optimistic concurrency bookkeeping per branch
        CREATE TABLE IF NOT EXISTS branch_heads (
            branch_id TEXT PRIMARY KEY,
            head_seq INTEGER NOT NULL,
            head_hash TEXT NOT NULL,
            version_token INTEGER NOT NULL
        );

        -- export/import identity log
        CREATE TABLE IF NOT EXISTS archive_log (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            case_id TEXT NOT NULL,
            op TEXT NOT NULL,
            at INTEGER NOT NULL,
            detail TEXT
        );
        """;

    public static final String[] MIGRATIONS = {
            // migration 1 -> 2 template: ALTER TABLE ... ; future rules stay here
    };
}
