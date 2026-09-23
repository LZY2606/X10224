package dsc.model;

import java.util.List;

/**
 * Imported instrument output, kept exactly as archived: original column
 * representations plus parse failures, so unconverted values stay in place
 * with diagnostics instead of being dropped.
 */
public record RawSequence(
        String sourceId,
        String sequenceId,
        String declaredDirection,     // free text from metadata, not trusted alone
        SignConvention signConvention,
        String tempUnit,
        String hfUnit,
        List<RawPoint> points,
        List<Diagnostic> diagnostics
) {}
