package dsc.model;

import dsc.units.Quantity;

import java.util.List;

/**
 * One independently archived input to the analysis. Every kind of direct
 * evidence is stored separately ({@link Type}) instead of being folded into
 * a single spreadsheet row:
 *
 * <ul>
 *   <li>INSTRUMENT_TRACE  – raw instrument output (points kept verbatim),</li>
 *   <li>SAMPLE_MASS       – weighed sample mass (versioned on revision),</li>
 *   <li>SCAN_RATE         – programmed heating/cooling rate,</li>
 *   <li>ATMOSPHERE        – purge gas / flow metadata,</li>
 *   <li>TEMP_CALIBRATION  – temperature calibration (offset/scale, basis),</li>
 *   <li>TANGENT_PICK      – reviewer-selected tangent anchors.</li>
 * </ul>
 *
 * Each record has a stable {@code id}, a monotonically increasing
 * {@code version}, and keeps its original textual representation in
 * {@code rawText}.
 */
public record SourceRecord(
        String id,
        String studyId,
        Type type,
        int version,
        String supersedesId,
        String segmentId,
        String unit,
        double value,
        String rawText,
        String basis,
        String recordedAt,
        String recordedBy,
        List<SamplePoint> points,
        boolean retired,
        int createdSeq
) {

    public enum Type {
        INSTRUMENT_TRACE("仪器输出"),
        SAMPLE_MASS("样品质量"),
        SCAN_RATE("扫描速率"),
        ATMOSPHERE("气氛"),
        TEMP_CALIBRATION("温度标定"),
        TANGENT_PICK("用户选定的切线");

        private final String label;

        Type(String label) { this.label = label; }

        public String label() { return label; }
    }

    public SourceRecord withVersion(int newVersion, String newSupersedes) {
        return new SourceRecord(id, studyId, type, newVersion, newSupersedes, segmentId,
                unit, value, rawText, basis, recordedAt, recordedBy, points, retired);
    }

    public SourceRecord retired() {
        return new SourceRecord(id, studyId, type, version, supersedesId, segmentId,
                unit, value, rawText, basis, recordedAt, recordedBy, points, true);
    }

    public Quantity quantity() {
        return Quantity.of(value, unit, rawText);
    }

    public boolean isPointSource() {
        return type == Type.INSTRUMENT_TRACE;
    }
}
