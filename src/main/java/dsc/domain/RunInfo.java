package dsc.domain;

/**
 * Immutable instrument-output header. Direction and sign come from explicit
 * fields here, never from the file name.
 */
public record RunInfo(
        String id,
        String sampleId,
        String instrumentSerial,
        String fileName,
        String cycleLabel,
        int seqIndex,
        Qty scanRate,
        String atmosphere,
        Qty atmosphereFlow,
        TempCalibration tempCalibration,
        HeatFlowSign declaredSign,
        ScanDirection declaredDirection,
        String importedEventId) {}
