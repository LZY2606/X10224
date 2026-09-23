package dsc.model;

import java.util.List;

/**
 * Raw instrument output for one scan segment (a heating or a cooling leg of a
 * thermal cycle). The trace belongs to exactly one direction; heating and
 * cooling traces are never merged for baseline work.
 */
public record InstrumentTrace(
        String id,
        int version,
        String sampleId,
        int cycleIndex,
        Direction direction,
        ExoSign exoSign,
        String sourceFile,
        List<Reading> rows,
        List<String> columnHeaders,
        String importedAt,
        String note) {

    public int usableRows() {
        int n = 0;
        for (Reading r : rows) {
            if (r.fullyUsable()) {
                n++;
            }
        }
        return n;
    }
}
