package dsc.domain;

import java.util.LinkedHashMap;
import java.util.Map;

/** A physical sample with a versioned mass history. */
public record Sample(
        String id,
        String name,
        String materialId,
        Map<String, String> attributes) {

    public Sample {
        attributes = attributes == null ? new LinkedHashMap<>() : new LinkedHashMap<>(attributes);
    }
}
