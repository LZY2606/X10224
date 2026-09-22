package dsc.domain;

import dsc.domain.Enums.*;
import java.util.ArrayList;
import java.util.List;

/**
 * A single heating or cooling scan belonging to one sample history.
 * Holds raw rows verbatim plus parse diagnostics; normalization (canonical
 * endo-up heat flow, even-grid statistics) is computed on demand and never
 * mutates the source.
 */
public final class Trace {
    private String traceId;
    private String sourceEventId;
    private String fileName;
    private SegmentKind segmentKind;
    private ScanDirection declaredDirection;   // from instrument/program metadata
    private ScanDirection detectedDirection;   // slope sign from data
    private HeatFlowConvention declaredConvention;
    private List<SamplePoint> points = new ArrayList<>();
    private List<String> diagnostics = new ArrayList<>();
    private String atmosphere;                  // e.g. "N2 50 mL/min"
    private Quantity scanRate;                  // K/min, original preserved
    private String calibrationRef;              // temperature calibration record id
    private TemperatureGrid grid;
    private double meanStepC, maxStepC, minStepC;

    public Trace() {}

    public List<Integer> usableRows() {
        List<Integer> rows = new ArrayList<>();
        for (SamplePoint p : points) if (p.usable()) rows.add(p.index());
        return rows;
    }

    public double slopeSign() {
        List<Integer> u = usableRows();
        if (u.size() < 2) return 0;
        double firstT = points.get(u.get(0)).t();
        double lastT = points.get(u.get(u.size() - 1)).t();
        return Math.signum(lastT - firstT);
    }

    /** Canonical heat flow in mW with endotherm positive. Input stays untouched. */
    public double canonicalHeatFlowMw(SamplePoint p) {
        double hf = p.hf();
        return declaredConvention == HeatFlowConvention.ENDO_DOWN ? -hf : hf;
    }

    public String getTraceId() { return traceId; }
    public void setTraceId(String traceId) { this.traceId = traceId; }
    public String getSourceEventId() { return sourceEventId; }
    public void setSourceEventId(String sourceEventId) { this.sourceEventId = sourceEventId; }
    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }
    public SegmentKind getSegmentKind() { return segmentKind; }
    public void setSegmentKind(SegmentKind segmentKind) { this.segmentKind = segmentKind; }
    public ScanDirection getDeclaredDirection() { return declaredDirection; }
    public void setDeclaredDirection(ScanDirection declaredDirection) { this.declaredDirection = declaredDirection; }
    public ScanDirection getDetectedDirection() { return detectedDirection; }
    public void setDetectedDirection(ScanDirection detectedDirection) { this.detectedDirection = detectedDirection; }
    public HeatFlowConvention getDeclaredConvention() { return declaredConvention; }
    public void setDeclaredConvention(HeatFlowConvention declaredConvention) { this.declaredConvention = declaredConvention; }
    public List<SamplePoint> getPoints() { return points; }
    public void setPoints(List<SamplePoint> points) { this.points = points; }
    public List<String> getDiagnostics() { return diagnostics; }
    public void setDiagnostics(List<String> diagnostics) { this.diagnostics = diagnostics; }
    public String getAtmosphere() { return atmosphere; }
    public void setAtmosphere(String atmosphere) { this.atmosphere = atmosphere; }
    public Quantity getScanRate() { return scanRate; }
    public void setScanRate(Quantity scanRate) { this.scanRate = scanRate; }
    public String getCalibrationRef() { return calibrationRef; }
    public void setCalibrationRef(String calibrationRef) { this.calibrationRef = calibrationRef; }
    public TemperatureGrid getGrid() { return grid; }
    public void setGrid(TemperatureGrid grid) { this.grid = grid; }
    public double getMeanStepC() { return meanStepC; }
    public void setMeanStepC(double meanStepC) { this.meanStepC = meanStepC; }
    public double getMaxStepC() { return maxStepC; }
    public void setMaxStepC(double maxStepC) { this.maxStepC = maxStepC; }
    public double getMinStepC() { return minStepC; }
    public void setMinStepC(double minStepC) { this.minStepC = minStepC; }
}
