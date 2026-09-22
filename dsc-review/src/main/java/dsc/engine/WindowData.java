package dsc.engine;

import dsc.domain.ScanDirection;

/**
 * Extracted, calibrated, sign-normalized points inside an analysis window.
 * Index arrays are original row indices.
 */
record WindowData(
        String windowId,
        String windowVersionId,
        String segmentId,
        ScanDirection direction,
        int[] rows,
        double[] tK,
        double[] timeS,
        double[] heatFlowW,
        double[] spacingK,
        boolean reverseConvention,
        boolean signUnknown,
        boolean empty,
        java.util.List<java.util.Map<String, Object>> diagnostics
) {
}
