package org.matlab.dsc.ingest;

import java.util.List;
import org.matlab.dsc.domain.Diagnostic;

/**
 * Parsed-but-not-normalised instrument output: header strings are preserved
 * verbatim, data rows are preserved verbatim, diagnostics already cover rows
 * the exporter knew were bad. The normalisation layer decides what converts.
 */
public record InstrumentFile(String origin, java.util.Map<String, String> header,
                             List<org.matlab.dsc.domain.RawPoint> points,
                             List<Diagnostic> declaredDiagnostics) {
}
