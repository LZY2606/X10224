package dsc;

import java.security.MessageDigest;
import java.sql.*;
import java.time.Instant;
import java.util.*;

/**
 * Versioned record store.
 * - Every save appends an immutable record version; the head pointer switches in the
 *   SAME transaction, so a crash mid-write leaves either the old head or a complete new one.
 * - Saving against a stale base version creates a resolvable conflict instead of overwriting.
 * - Every judgment is an append-only, machine-readable event; historical views are
 *   reconstructed by folding head_changed events, never by overwriting history.
 */
public final class Store {
    private final Db db;
    public Store(Db db) { this.db = db; }
}
