package dsc.domain;

import java.util.List;

/**
 * One physical sample's complete thermal履历: several heating/cooling segments
 * across thermal cycles, sharing the evolving sample mass and metadata.
 */
public record SampleHistory(
        String id,
        String material,
        List<Segment> segments
) {
    public Segment segment(String segmentId) {
        return segments.stream().filter(s -> s.id().equals(segmentId)).findFirst().orElse(null);
    }
}
