package dsc.domain;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * An independently versioned derivation output. Every recompute produces a new
 * row; the "current" pointer for a logical key moves only after the whole
 * transaction commits. The output is self-describing JSON; every displayed
 * value inside it carries a stable @id.
 */
public record Derivation(
        String id,            // content-addressed, stable across export/import
        String key,           // logical key, e.g. NORMALIZED:seg-1 or EVENT:seg-1:iv-3
        String kind,          // NORMALIZED / BASELINE / EVENT / SUMMARY
        long inputEventSeq,   // max event seq included
        String branch,
        String ruleVersion,
        String deps,          // JSON array of dependency derivation ids
        JsonNode output,
        JsonNode diagnostics,
        String status) {      // OK / INVALID / EMPTY / DIR_MIX_REJECTED / MIXED

    public static final String OK = "OK";
    public static final String INVALID = "INVALID";
    public static final String EMPTY = "EMPTY";
    public static final String DIR_MIX = "DIR_MIX_REJECTED";
    public static final String MIXED = "MIXED";
}
