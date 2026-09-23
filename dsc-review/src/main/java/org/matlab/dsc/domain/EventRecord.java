package org.matlab.dsc.domain;

import java.util.List;
import java.util.Map;

/**
 * Append-only ledger entry. The sequence is the optimistic-concurrency token:
 * a client editing "from seq N" must commit with expectedSeq == N.
 *
 * @param seq            global monotonically increasing order
 * @param type           event category
 * @param sampleId       affected sample history
 * @param actor          reviewer / system
 * @param basis          machine-readable judgment basis (see JudgmentCodes)
 * @param affectedRefs   objects created/rejected/staled by this event
 * @param payload        event detail (including decision, interval, codes)
 * @param branchId       branch under comparison, if any
 * @param expectedSeq    OCC base sequence claimed by the writer
 */
public record EventRecord(long seq, EventType type, String sampleId, String actor,
                          String basis, List<String> affectedRefs,
                          Map<String, Object> payload, String branchId,
                          long expectedSeq) {
    public static final String SYSTEM_ACTOR = "system";
}
