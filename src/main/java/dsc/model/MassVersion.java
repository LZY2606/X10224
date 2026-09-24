package dsc.model;

import java.util.List;

/** 样品质量的一个独立版本。修订后所有归一化派生标记过期。 */
public record MassVersion(String versionId, RawValue massMg, String reason,
                          String actor, long timestamp, List<String> invalidates) {
    public MassVersion {
        invalidates = invalidates == null ? List.of() : List.copyOf(invalidates);
    }
}
