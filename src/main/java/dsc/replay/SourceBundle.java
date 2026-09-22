package dsc.replay;

import dsc.domain.SourceDoc;
import dsc.domain.SourceKind;

import java.util.List;
import java.util.Map;

/** 某次重放（截至某事件）时，按 run 聚合好的六类来源。 */
public record SourceBundle(
    String sampleId,
    List<SourceDoc> global,
    Map<String, RunSources> runs
) {

    public record RunSources(
            String runId,
            String sequence,
            SourceDoc instrument,
            SourceDoc mass,
            SourceDoc rate,
            SourceDoc atmosphere,
            SourceDoc calibration,
            SourceDoc tangent
    ) {
        public SourceDoc byKind(SourceKind kind) {
            return switch (kind) {
                case INSTRUMENT_OUTPUT -> instrument;
                case MASS -> mass;
                case SCAN_RATE -> rate;
                case ATMOSPHERE -> atmosphere;
                case TEMP_CALIBRATION -> calibration;
                case TANGENT_SELECTION -> tangent;
            };
        }
    }
}
