package dsc;

import java.util.List;
import java.util.Map;

/** Core records. Every domain object has a stable identity (id never reused across versions). */
public final class Model {

    /** A raw archived input. kind: instrument_output | sample_mass | scan_rate | atmosphere |
     *  temp_calibration | user_tangents. Raw text is never rewritten; revisions add new versions. */
    public record SourceDoc(String id, String kind, int version, String raw, Map<String, Object> attrs,
                            String hash, String supersedes, long createdSeq) {}

    /** Direction of a scan segment; heating and cooling are never mixed in one derivation. */
    public enum Direction { HEATING, COOLING }

    /** A contiguous scan segment derived from one instrument output version. */
    public record Segment(String id, String sourceId, int sourceVersion, Direction direction,
                          double rateKPerMin, String atmosphere, String signConvention,
                          double[] timeS, double[] tempC, double[] heatFlowMw) {
        public boolean endoUp() { return "endotherm=up".equals(signConvention); }
        public int size() { return timeS.length; }
    }

    /** status: writing -> complete (atomic publish) | abandoned (crash cleanup). */
    public record Derivation(String id, String kind, int version, Map<String, Object> inputs,
                             String status, Map<String, Object> result, List<Map<String, Object>> diagnostics,
                             long causeEventSeq, long createdSeq) {}

    /** Machine-readable judgement / audit record. seq is the global order. */
    public record Event(long seq, String id, String type, Map<String, Object> payload, List<String> parents) {}

    /** Two users edited from the same base version; both branches kept until resolved. */
    public record Conflict(String id, String key, String baseVersion, List<String> branches,
                           String status, String resolution) {}
}
