package dsc.storage;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import dsc.events.Event;
import dsc.events.EventType;
import dsc.events.HistoryState;
import dsc.events.Reducer;
import dsc.service.ExportImport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class StorageGuaranteesTest {

    private Event event(String entity) {
        return new Event(EventType.JUDGMENT_RECORDED, entity, Instant.now(), "t", "x",
                JsonNodeFactory.instance.objectNode().put("k", entity), List.of(), null, List.of(), null);
    }

    @Test
    void crashBeforeCommitLeavesOldCompleteState(@TempDir Path dir) {
        Path db = dir.resolve("crash.db");
        try (Database database = new Database(db.toString())) {
            EventStore store = new EventStore(database);
            store.openHistory("h", "h", Instant.now());
            store.append("h", event("e1"), 0);
            assertEquals(1, store.currentSeq("h"));
            assertThrows(StorageException.class, () ->
                    store.commitBatch("h", List.of(event("e2"), event("e3")), 1, true));
            // reopen the file: the interrupted multi-row batch must be entirely absent
        }
        try (Database database = new Database(db.toString())) {
            EventStore store = new EventStore(database);
            assertEquals(1, store.currentSeq("h"), "either old or complete-new, never partial");
            assertEquals(1, store.readAll("h").size());
        }
    }

    @Test
    void atomicBatchAllOrNothingOnConflict(@TempDir Path dir) {
        try (Database database = new Database(dir.resolve("c.db").toString())) {
            EventStore store = new EventStore(database);
            store.openHistory("h", "h", Instant.now());
            store.append("h", event("e1"), 0);
            // two editors base on the same seq=1
            assertThrows(ConflictException.class, () ->
                    store.commitBatch("h", List.of(event("a"), event("b")), 1));
            store.commitBatch("h", List.of(event("winner")), 1);
            assertThrows(ConflictException.class, () ->
                    store.commitBatch("h", List.of(event("loser")), 1));
            assertEquals(2, store.currentSeq("h"));
        }
    }

    @Test
    void concurrentWritersFromSameVersionProduceResolvableConflict(@TempDir Path dir) throws Exception {
        try (Database database = new Database(dir.resolve("cc.db").toString())) {
            EventStore store = new EventStore(database);
            store.openHistory("h", "h", Instant.now());
            store.append("h", event("seed"), 0);
            CyclicBarrier barrier = new CyclicBarrier(2);
            AtomicInteger conflicts = new AtomicInteger();
            Runnable writer = () -> {
                try {
                    barrier.await();
                    store.commitBatch("h", List.of(event("w-" + Thread.currentThread().getId())), 1);
                } catch (ConflictException ok) {
                    conflicts.incrementAndGet();
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            };
            var t1 = new Thread(writer);
            var t2 = new Thread(writer);
            t1.start(); t2.start(); t1.join(); t2.join();
            assertEquals(1, conflicts.get(), "exactly one writer must hit the resumable conflict");
            assertEquals(2, store.currentSeq("h"));
        }
    }

    @Test
    void conflictIsResolvableByRebasing(@TempDir Path dir) {
        try (Database database = new Database(dir.resolve("r.db").toString())) {
            EventStore store = new EventStore(database);
            store.openHistory("h", "h", Instant.now());
            store.append("h", event("e1"), 0);
            assertThrows(ConflictException.class, () ->
                    store.append("h", event("stale"), 0));
            long fresh = store.currentSeq("h");
            store.append("h", event("rebased"), fresh);
            assertEquals(2, store.currentSeq("h"));
        }
    }

    @Test
    void legacyV1DatabaseMigratesForwardWithoutLosingEvents(@TempDir Path dir) throws Exception {
        Path dbFile = dir.resolve("legacy.db");
        try (Database database = new Database(dbFile.toString())) {
            Migrator.stampLegacyV1(database.connection());
            EventStore store = new EventStore(database);
            store.openHistory("h", "h", Instant.now());
            store.append("h", event("legacy-event"), 0);
        }
        try (Database database = new Database(dbFile.toString())) {
            assertEquals(2, Migrator.currentVersion());
            EventStore store = new EventStore(database);
            var events = store.readAll("h");
            assertEquals(1, events.size());
            assertEquals("legacy-event", events.get(0).getEntityId());
            // v2 feature works after migration
            store.commitBatch("h", List.of(event("post-migration")), 1);
            assertEquals(2, store.currentSeq("h"));
        }
    }

    @Test
    void exportDeleteImportKeepsIdentityOrderAndProvenance(@TempDir Path dir) throws Exception {
        Path db = dir.resolve("orig.db");
        Path bundle = dir.resolve("bundle.json");
        try (Database database = new Database(db.toString())) {
            EventStore store = new EventStore(database);
            store.openHistory("h", "h", Instant.now());
            Event e1 = event("id-alpha");
            Event e2 = new Event(EventType.SOURCE_AMENDED, "id-beta", Instant.now(), "t", "amend",
                    JsonNodeFactory.instance.objectNode(), List.of(), null, List.of("seg:x"), "id-gamma");
            Event e3 = new Event(EventType.CANDIDATE_ACCEPTED, "id-gamma", Instant.now(), "t", "acc",
                    JsonNodeFactory.instance.objectNode(), List.of(), null, List.of("bl:b"), null);
            store.commitBatch("h", List.of(e1, e2, e3), 0);
            ExportImport.writeBundle(store, "h", "h", bundle);
        }
        java.nio.file.Files.delete(db);
        Path imported = dir.resolve("imported.db");
        long count = ExportImport.importBundle(imported, bundle);
        assertEquals(3, count);
        try (Database database = new Database(imported.toString())) {
            EventStore store = new EventStore(database);
            var events = store.readAll("h");
            assertEquals(List.of("id-alpha", "id-beta", "id-gamma"),
                    events.stream().map(Event::getEntityId).toList());
            assertEquals("id-gamma", events.get(1).getSupersedes());
            assertEquals(List.of("seg:x"), events.get(1).getDependencies());
            HistoryState s = new Reducer().fold("h", "h", events);
            assertEquals(3, s.headSeq);
        }
    }
}
