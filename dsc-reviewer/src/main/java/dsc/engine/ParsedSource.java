package dsc.engine;

import dsc.domain.Diagnostic;

import java.util.List;

/**
 * Result of parsing an instrument export. {@code rawText} is preserved verbatim.
 * Rows that cannot be converted are not dropped: they appear in {@code rawRows}
 * and a diagnostic points at the offending line.
 */
public record ParsedSource(String rawText, List<Pt> points, List<String[]> rawRows,
                           List<Diagnostic> diagnostics, boolean nonUniform) {
}
