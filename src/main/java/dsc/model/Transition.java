package dsc.model;

import java.util.List;

/**
 * A characterised thermal event on one segment: glass transition,
 * crystallisation or melting. Sign conventions are applied using the
 * segment's measured direction and polarity, never inferred from a file
 * name. Heating and cooling transitions are kept on separate segments.
 */
public record Transition(
        String id,
        String studyId,
        String segmentId,
        String baselineId,
        int version,
        Kind kind,
        double onsetTK,
        double peakTK,
        double endsetTK,
        /** Integrated enthalpy normalised by sample mass, J/g; NaN when stale/unavailable. */
        double enthalpyJg,
        /** Total propagated uncertainty on enthalpy, J/g. */
        double enthalpyErrorJg,
        List<ErrorSource> errorSources,
        List<String> dependsOn,
        boolean stale,
        int createdSeq,
        int staleSeq
) {

    public enum Kind {
        GLASS("玻璃化转变"),
        CRYSTALLISATION("结晶"),
        MELTING("熔融"),
        OVERLAP("重叠转折"),
        UNRESOLVED("无法唯一决定");

        private final String label;

        Kind(String label) { this.label = label; }

        public String label() { return label; }
    }

    /** One contribution to the enthalpy error budget. */
    public record ErrorSource(String code, String description, double contributionJg,
                              String sourceId) {
    }

    public Transition markStale(int seq) {
        return new Transition(id, studyId, segmentId, baselineId, version, kind, onsetTK, peakTK,
                endsetTK, Double.NaN, Double.NaN, errorSources, dependsOn, true, createdSeq, seq);
    }

    public Transition recomputed(int newVersion, double onset, double peak, double endset,
                                 double enthalpy, double error, List<ErrorSource> sources,
                                 List<String> dependsOn) {
        return new Transition(id, studyId, segmentId, baselineId, newVersion, kind, onset, peak,
                endset, enthalpy, error, sources, dependsOn, false, createdSeq, 0);
    }
}
