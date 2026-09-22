package dsc.domain;

import dsc.domain.Enums.*;
import java.util.ArrayList;
import java.util.List;

/**
 * A glass transition / crystallization / melt identified on exactly one scan.
 * Holds onset, peak and endset with stable per-value ids, and the enthalpy
 * derived from the currently accepted baseline candidate.
 */
public final class TransitionEvent {
    public String eventId;
    public String traceId;
    public TransitionType type;
    public String onsetId, peakId, endsetId;
    public double onsetC, peakC, endsetC;
    public String enthalpyId;
    public double enthalpyJPerG;
    public double enthalpyRawJ;
    public boolean endothermicCanonical;
    public List<ErrorSource> enthalpyErrorSources = new ArrayList<>();
    public List<String> candidateIds = new ArrayList<>();
    public String acceptedCandidateId;
    public String massVersionId;
    public String ruleVersion;
    public String note;
    public boolean ambiguous;

    public TransitionEvent() {}
}
