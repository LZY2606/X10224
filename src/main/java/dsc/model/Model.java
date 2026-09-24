package dsc.model;

import java.util.List;

/** Core domain vocabulary for the DSC baseline / phase-transition reviewer. */
public final class Model {
    private Model() {}

    /** The six separately archived source kinds. */
    public enum SourceKind {
        INSTRUMENT_OUTPUT, SAMPLE_MASS, SCAN_RATE, ATMOSPHERE, TEMP_CALIBRATION, USER_TANGENT
    }

    public enum Direction { HEATING, COOLING }

    /** Endo/exo plotting convention comes only from instrument metadata, never the file name. */
    public enum Convention { ENDO_UP, EXO_UP, AMBIGUOUS }

    public enum EventType { GLASS_TRANSITION, CRYSTALLIZATION, MELTING }

    public enum CandidateStatus { ADOPTED, RETAINED, REJECTED }

    /** Machine-readable judgment statuses attached to case events. */
    public enum Judgment { PASS, AMBIGUOUS, STALE, EMPTY, CONFLICT, DIAGNOSTIC, RECOMPUTED }

    /** A measured quantity always carries a unit and a coordinate basis. */
    public record Quantity(double value, String unit, String basis) {
        public String json() {
            return "{\"value\":" + value + ",\"unit\":\"" + unit + "\",\"basis\":\"" + basis + "\"}";
        }
    }

    /** Raw imported point: original representation is preserved; normalization happens in derived layers. */
    public record Point(double timeS, double tempRaw, double heatFlowRaw) {}

    public record SegmentView(
            String id,
            String sourceId,
            int index,
            Direction direction,
            Convention convention,
            String tempUnit,
            String heatFlowUnit,
            boolean heatFlowConvertible,
            List<Point> points) {}

    public record BaselineEval(
            String candidateId,
            String kind,
            double onset,
            double peak,
            double endset,
            double enthalpymJ,
            double deltaCp_mJ_per_sK,
            String[] errorSources) {}
}
