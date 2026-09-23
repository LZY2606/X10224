package dsc.model;

/**
 * One sample of an instrument output. tC and hfMw are null for rows that could
 * not be converted; the original textual representations are always retained
 * so no information is discarded just to draw the chart.
 */
public record RawPoint(
        int index,
        String timeS,      // raw text, seconds
        String tempText,   // raw text as imported
        String hfText,     // raw text as imported
        Double tC,         // parsed/normalized temperature in degC, or null
        Double hfMw        // parsed/normalized heat flow in mW, or null
) {}
