package dsc.core;

import java.util.List;

/**
 * Parsed instrument output for one file. Series are kept as declared (a file may
 * contain multiple cycles / heat and cool segments). Parse diagnostics travel
 * with the envelope; unconvertible units stay visible instead of aborting import.
 */
public record RawScan(
        String fileName,
        String instrument,
        String temperatureUnit,
        String heatflowUnit,
        String timeUnit,
        String rateRaw,
        String atmosphereRaw,
        List<RawSeries> series,
        List<Diagnostic> diagnostics
) {
}
