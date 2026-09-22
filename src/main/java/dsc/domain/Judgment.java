package dsc.domain;

import dsc.domain.Enums.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Machine-readable finding attached to an event. The three headline invariants
 * each have a stable {@code code}:
 *   DIRECTION_NOT_GUESSED  - sign convention is taken from instrument metadata, never the file name;
 *   SEGMENTS_NOT_MIXED     - heating and cooling scans are never joined in one baseline/integral;
 *   BASELINES_DIVERGE      - multiple plausible baselines are kept as distinct candidates;
 *   MASS_INVALIDATES       - a mass revision expires every mass-normalized derivation.
 */
public final class Judgment {
    public String code;
    public Severity severity;
    public boolean satisfied;
    public String detail;
    public List<String> affectedObjectIds = new ArrayList<>();
    public List<String> evidenceEventIds = new ArrayList<>();

    public Judgment() {}
    public Judgment(String code, Severity severity, boolean satisfied, String detail) {
        this.code = code; this.severity = severity; this.satisfied = satisfied; this.detail = detail;
    }
}
