package dsc.model;

import dsc.units.Quantity;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ProjectState {
    public String projectId;
    public String name;
    public String currentBranch = Branch.MAIN;
    public String lastRuleVersion;
    public long seq;
    public final Map<String, SourceBlob> blobs = new LinkedHashMap<>();
    public final Map<String, Segment> segments = new LinkedHashMap<>();
    public final List<MassRecord> massHistory = new java.util.ArrayList<>();
    public final List<CalibrationRecord> calibrationHistory = new java.util.ArrayList<>();
    public final List<AtmosphereRecord> atmosphereHistory = new java.util.ArrayList<>();
    public final Map<String, RateRecord> rates = new LinkedHashMap<>();
    public final Map<String, RawSeries> rawSeries = new LinkedHashMap<>();
    public final Map<String, Interval> intervals = new LinkedHashMap<>();
    public final Map<String, TangentSelection> tangents = new LinkedHashMap<>();
    public final Map<String, BaselineCandidate> baselines = new LinkedHashMap<>();
    public final Map<String, ThermalEvent> thermalEvents = new LinkedHashMap<>();
    public final Map<String, Integration> integrations = new LinkedHashMap<>();
    public final List<Diagnostic> diagnostics = new java.util.ArrayList<>();
    public final List<Judgment> judgments = new java.util.ArrayList<>();
    public final List<Decision> decisions = new java.util.ArrayList<>();
    public final Map<String, Branch> branches = new LinkedHashMap<>();
    public final List<DomainEvent> events = new java.util.ArrayList<>();

    public MassRecord currentMass() {
        return massHistory.isEmpty() ? null : massHistory.get(massHistory.size() - 1);
    }

    public CalibrationRecord currentCalibration() {
        return calibrationHistory.isEmpty() ? null : calibrationHistory.get(calibrationHistory.size() - 1);
    }
}
