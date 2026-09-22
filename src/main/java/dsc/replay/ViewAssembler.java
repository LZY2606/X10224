package dsc.replay;

import dsc.domain.Derivation;
import dsc.domain.Event;
import dsc.domain.Feature;
import dsc.domain.ScanSegment;
import dsc.domain.SourceDoc;
import dsc.domain.ViewState;
import dsc.engine.RunAnalyzer;
import dsc.store.Json;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 把重放得到的来源、分析结果、事件、判定状态组装成界面读回的 ViewState。 */
public final class ViewAssembler {

    private ViewAssembler() {
    }

    public record Snapshot(
            ViewState state,
            List<Derivation> derivations,
            Map<String, RunAnalyzer.AnalyzedRun> analyzed
    ) {
    }

    public static Snapshot assemble(long atSeq, String branch,
                                    List<SourceDoc> sources,
                                    SourceBundle bundle,
                                    Map<String, RunAnalyzer.AnalyzedRun> analyzed,
                                    List<Event> events,
                                    List<Derivation> derivations,
                                    List<String> rebuildNotes) {
        List<ViewState.RunView> runViews = new ArrayList<>();
        List<ViewState.DiagnosticView> diagnostics = new ArrayList<>();
        for (SourceBundle.RunSources runSources : bundle.runs().values()) {
            RunAnalyzer.AnalyzedRun run = analyzed.get(runSources.runId());
            if (run == null) {
                continue;
            }
            List<String> runDiags = new ArrayList<>(run.diagnostics());
            for (String note : rebuildNotes) {
                if (note.contains(runSources.runId())) {
                    runDiags.add("REBUILD:" + note);
                }
            }
            runViews.add(new ViewState.RunView(
                    runSources.runId(),
                    runSources.sequence(),
                    run.parsed().headerOr("declared_direction", "unknown"),
                    run.parsed().headerOr("sign_convention", "exo_up"),
                    run.segments(),
                    run.points(),
                    run.features(),
                    runDiags));
            for (String diag : runDiags) {
                diagnostics.add(new ViewState.DiagnosticView(runSources.runId(),
                        null, diag.split("[:@]")[0], "warning", diag,
                        sourceChain(runSources)));
            }
            for (Feature feature : run.features()) {
                for (String diag : feature.diagnostics()) {
                    diagnostics.add(new ViewState.DiagnosticView(runSources.runId(),
                            feature.id(), diag.split("[:@]")[0], "warning", diag,
                            sourceChain(runSources)));
                }
            }
        }
        List<String> openConflicts = events.stream()
                .filter(e -> Event.STATUS_CONFLICT.equals(e.status())
                        || Event.STATUS_DEFERRED.equals(e.status()))
                .map(Event::summary)
                .toList();
        ViewState state = new ViewState(atSeq, branch, sources,
                runViews, events, openConflicts, diagnostics);
        return new Snapshot(state, derivations, analyzed);
    }

    public static String sourceChain(SourceBundle.RunSources runSources) {
        StringBuilder sb = new StringBuilder();
        appendChain(sb, runSources.instrument());
        appendChain(sb, runSources.mass());
        appendChain(sb, runSources.rate());
        appendChain(sb, runSources.atmosphere());
        appendChain(sb, runSources.calibration());
        appendChain(sb, runSources.tangent());
        return sb.toString();
    }

    private static void appendChain(StringBuilder sb, SourceDoc source) {
        if (source != null) {
            if (!sb.isEmpty()) {
                sb.append(" -> ");
            }
            sb.append(source.id()).append("@v").append(source.version());
        }
    }
}
