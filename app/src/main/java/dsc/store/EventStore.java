package dsc.store;

import com.fasterxml.jackson.databind.JsonNode;
import dsc.event.Event;
import dsc.event.EventType;

import java.util.List;

/** Append-only event ledger with version pointers and optimistic concurrency. */
public interface EventStore extends AutoCloseable {

    record NewEvent(String eventId, EventType type, String author,
                    String parentEventId, String branchId, List<String> basisRefs,
                    JsonNode payload) {
        public NewEvent {
            refs = basisRefs == null ? List.of() : List.copyOf(basisRefs);
        }
    }

    /** Result of a successful append: the new head and sequence. */
    record AppendResult(String eventId, long seq, String headEventId) { }

    /**
     * Append events atomically as one new version. Either every event is visible afterwards
     * (and the head pointer advances) or none of them are.
     *
     * @param expectedHead head the editor started from; null for project creation
     */
    AppendResult append(String projectId, String expectedHead, String newBranchId,
                        List<NewEvent> events, WriteBarrier barrier);

    default AppendResult append(String projectId, String expectedHead, List<NewEvent> events) {
        return append(projectId, expectedHead, null, events, WriteBarrier.NOOP);
    }

    List<Event> readAll(String projectId);

    /** Events of one branch plus ancestors, in sequence order (replay path). */
    List<Event> readBranch(String projectId, String branchId);

    String headEventId(String projectId);

    String currentBranch(String projectId);

    /** Move the current pointer to an existing branch (continue an accepted line); does not create events. */
    void switchBranch(String projectId, String branchId);

    List<String> listBranches(String projectId);

    /** Project ids present in the file. */
    List<String> listProjects();

    /** Copy everything to another store file (export). */
    void exportTo(String targetFile);

    /** Current logical schema version of the database. */
    int schemaVersion();
}
