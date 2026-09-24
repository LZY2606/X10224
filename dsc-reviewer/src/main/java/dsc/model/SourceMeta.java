package dsc.model;

import java.util.List;

/**
 * The six separately archived direct sources mandated by the review protocol:
 * instrument output, sample mass, scan rate, atmosphere, temperature calibration and the
 * user-selected tangents. Each archive keeps provenance (origin file, checksum, archived-at,
 * raw text excerpt) so every displayed value can be traced to a source version.
 */
public record SourceMeta(String sourceId, String kind, String origin, String checksum,
                         String archivedAt, String rawExcerpt, List<String> notes) {
}
