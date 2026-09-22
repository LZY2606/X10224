package dsc.service;

import com.fasterxml.jackson.databind.JsonNode;
import dsc.events.Event;
import dsc.events.EventType;
import dsc.events.HistoryState;
import dsc.events.Reducer;
import dsc.storage.Database;
import dsc.storage.EventStore;
import dsc.storage.Json;
import dsc.storage.Migrator;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Whole-history export/import.
 *
 * <p>The bundle is the event log in original order plus the identity index and
 * a content hash. Import creates a fresh database and replays the bundle, so
 * "export → delete local DB → import" reproduces the same entity ids, the same
 * event order and the same provenance links (dependencies/supersedes).
 */
public final class ExportImport {

    private ExportImport() {}

    public static Map<String, Object> exportBundle(EventStore store, String historyId, String name) {
        List<Event> log = store.readAll(historyId);
        Map<String, Object> bundle = new LinkedHashMap<>();
        bundle.put("format", "dsc-history-bundle");
        bundle.put("formatVersion", 1);
        bundle.put("exportedAt", Instant.now().toString());
        bundle.put("historyId", historyId);
        bundle.put("name", name);
        bundle.put("schemaVersion", Migrator.currentVersion());
        bundle.put("eventCount", log.size());
        List<Map<String, Object>> events = new ArrayList<>();
        List<String> orderedIds = new ArrayList<>();
        for (Event e : log) {
            events.add(eventRow(e));
            if (e.getEntityId() != null && !orderedIds.contains(e.getEntityId())) {
                orderedIds.add(e.getEntityId());
            }
        }
        bundle.put("events", events);
        bundle.put("identityOrder", orderedIds);
        bundle.put("bundleHash", dsc.core.Ids.sha12(historyId + "|" + log.size()
                + "|" + (log.isEmpty() ? 0 : log.get(log.size() - 1).getSeq())));
        return bundle;
    }

    private static Map<String, Object> eventRow(Event e) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("seq", e.getSeq());
        row.put("type", e.getType().name());
        row.put("entityId", e.getEntityId());
        row.put("schemaVersion", e.getSchemaVersion());
        row.put("at", e.getAt().toString());
        row.put("actor", e.getActor());
        row.put("comment", e.getComment());
        row.put("payload", e.getPayload());
        row.put("judgments", e.getJudgments());
        row.put("baseSeq", e.getBaseSeq());
        row.put("dependencies", e.getDependencies());
        row.put("supersedes", e.getSupersedes());
        return row;
    }

    public static void writeBundle(EventStore store, String historyId, String name, Path target)
            throws IOException {
        Files.createDirectories(target.toAbsolutePath().getParent());
        Json.MAPPER.writerWithDefaultPrettyPrinter().writeValue(target.toFile(),
                exportBundle(store, historyId, name));
    }

    public static long importBundle(Path dbPath, Path bundlePath) throws IOException {
        Map<?, ?> bundle = Json.MAPPER.readValue(bundlePath.toFile(), Map.class);
        return importBundleMap(dbPath, bundle);
    }

    /** Import into a brand-new database file; returns the replayed event count. */
    public static long importBundleMap(Path dbPath, Map<?, ?> bundle) {
        String historyId = (String) bundle.get("historyId");
        String name = String.valueOf(bundle.get("name"));
        Files0.deleteIfExists(dbPath);
        Files0.deleteIfExists(Path.of(dbPath + "-wal"));
        Files0.deleteIfExists(Path.of(dbPath + "-shm"));
        try (Database db = new Database(dbPath.toString())) {
            EventStore store = new EventStore(db);
            store.openHistory(historyId, name, Instant.now());
            Reducer reducer = new Reducer();
            List<Event> rebuilt = new ArrayList<>();
            long expectedSeq = 0;
            for (Object item : (List<?>) bundle.get("events")) {
                Map<?, ?> row = (Map<?, ?>) item;
                expectedSeq++;
                Event e = new Event();
                e.setType(EventType.valueOf((String) row.get("type")));
                e.setEntityId((String) row.get("entityId"));
                e.setAt(Instant.parse((String) row.get("at")));
                e.setActor((String) row.get("actor"));
                e.setComment((String) row.get("comment"));
                JsonNode payload = row.get("payload") == null ? null
                        : Json.MAPPER.valueToTree(row.get("payload"));
                e.setPayload(payload);
                if (row.get("judgments") != null) {
                    e.setJudgments(Json.MAPPER.convertValue(row.get("judgments"),
                            Json.MAPPER.getTypeFactory().constructCollectionType(List.class,
                                    dsc.events.Judgment.class)));
                }
                if (row.get("baseSeq") != null) e.setBaseSeq(((Number) row.get("baseSeq")).longValue());
                if (row.get("dependencies") != null) {
                    e.setDependencies(Json.MAPPER.convertValue(row.get("dependencies"),
                            Json.MAPPER.getTypeFactory().constructCollectionType(List.class, String.class)));
                }
                e.setSupersedes((String) row.get("supersedes"));
                rebuilt.add(e);
            }
            // One atomic replay batch: after commit every seq/identity is present;
            // a crash during import leaves the old (nonexistent) DB, never half.
            store.commitBatch(historyId, rebuilt, 0L);
            HistoryState state = new Reducer().fold(historyId, name, store.readAll(historyId));
            verifyIdentities(bundle, state, expectedSeq);
            return expectedSeq;
        }
    }

    private static void verifyIdentities(Map<?, ?> bundle, HistoryState s, long count) {
        if (s.headSeq != count) {
            throw new IllegalStateException("replay count mismatch: " + s.headSeq + " != " + count);
        }
        @SuppressWarnings("unchecked")
        List<String> expected = (List<String>) bundle.get("identityOrder");
        List<String> actual = new ArrayList<>();
        for (Event e : s.events) {
            if (e.getEntityId() != null && !actual.contains(e.getEntityId())) actual.add(e.getEntityId());
        }
        if (!actual.equals(expected)) {
            throw new IllegalStateException("identity order changed across export/import:\n expected="
                    + expected + "\n actual=" + actual);
        }
    }

    /** Tiny indirection so tests can mock deletions if needed; keeps IO in one place. */
    static final class Files0 {
        static void deleteIfExists(Path p) {
            try { Files.deleteIfExists(p); } catch (IOException ignored) {}
        }
    }
}
