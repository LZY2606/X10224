package dsc.domain;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.ArrayList;
import java.util.List;

/**
 * A flagged condition (unconvertible cell, polarity mismatch, overlap, empty interval,
 * invalidated derivation...) with a source chain the reviewer can drill into.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public final class Anomaly {
    public String id;
    public String code;
    public String severity;
    public String message;
    public String segmentId;
    public String intervalId;
    public String objectId;
    public final List<String> sourceChain = new ArrayList<>();
    public long eventSeq;
    public String status;

    public Anomaly() {
    }
}
