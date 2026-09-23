package org.matlab.dsc.domain;

import java.util.List;

/**
 * One instrument output row exactly as imported. {@code raw} preserves the
 * original cell representations; {@code flags} marks rows that could not be
 * converted (they stay in place, flagged, and are excluded from fitting).
 */
public record RawPoint(int index, String rawTemperature, String rawHeatFlow,
                       String rawTime, List<String> flags) {
    public boolean usable() {
        return flags == null || flags.isEmpty();
    }
}
