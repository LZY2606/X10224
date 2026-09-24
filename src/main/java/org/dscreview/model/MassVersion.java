package org.dscreview.model;

import java.util.List;

/** A separately archived sample-mass record. Revising it expires normalized derivations. */
public record MassVersion(
        String massId,
        Quant mass,
        String recordedAt,
        String reason,
        int versionNumber,
        List<Diagnostic> diagnostics) {
}
