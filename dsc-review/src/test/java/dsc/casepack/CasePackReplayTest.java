package dsc.casepack;

import dsc.derive.BaselineResult;
import dsc.derive.FeatureResult;
import dsc.events.HistoryState;
import dsc.events.Judgment;
import dsc.model.NodeStatus;
import dsc.storage.Database;
import dsc.storage.Migrator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-end replay of the offline case: the narrative must actually traverse
 * all seven boundary situations, retain rejected/adopted candidates, expire
 * normalized derivations after the mass revision, and keep time travel honest.
 */
class CasePackReplayTest {

    private CasePack pack(Database db) {
        CasePack p = new CasePack(db);
        p.build();
        p.buildBoundaryPhase();
        return p;
    }

    private Set<String> codes(HistoryState s) {
        return s.events.stream().flatMap(e -> (e.getJudgments() == null
                ? List.<Judgment>of() : e.getJudgments()).stream())
                .map(Judgment::code).collect(Collectors.toSet());
    }

    @Test
    void replaysAllSevenBoundarySituations(@TempDir Path dir) {
        try (Database db = new Database(dir.resolve("case.db").toString())) {
            CasePack p = pack(db);
            HistoryState s = p.service().state(CasePack.HISTORY_ID);
            Set<String> codes = codes(s);
            assertTrue(codes.contains("SIGN_NOT_FROM_FILENAME"), "reversed/unknown sign");
            assertTrue(codes.contains("NONUNIFORM_TEMPERATURE_GRID"), "non-uniform grid");
            assertTrue(codes.contains("SEGMENT_NOT_MIXED"), "cycle switching / no mixing");
            assertTrue(codes.contains("MASS_REVISION_INVALIDATES_DERIVATIONS"), "mass version change");
            assertTrue(codes.contains("BASELINE_PARALLEL_SHIFT"), "parallel baselines");
            assertTrue(codes.contains("EMPTY_REGION"), "empty interval");
            assertTrue(codes.contains("LOCAL_SOURCE_AMENDMENT"), "local source amendment");
            // overlapping transitions: overlap region keeps several MULTIPLE baselines
            List<BaselineResult> overlapBl = s.baselinesForRegion(p.overlapId);
            assertTrue(overlapBl.size() >= 2);
        }
    }

    @Test
    void heatingAndCoolingAreNeverMixed(@TempDir Path dir) {
        try (Database db = new Database(dir.resolve("case.db").toString())) {
            CasePack p = pack(db);
            HistoryState s = p.service().state(CasePack.HISTORY_ID);
            assertEquals("HEATING", s.segment(CasePack.SEG_HEAT_1).direction().name());
            assertEquals("COOLING", s.segment(CasePack.SEG_COOL_1).direction().name());
            // every region references exactly one segment of one cycle
            s.regions.values().forEach(rr -> assertNotNull(s.segment(rr.region.segmentId())));
            // crystallization region lives on the cooling segment only
            assertEquals(CasePack.SEG_COOL_1, s.regions.get(p.crystId).region.segmentId());
        }
    }

    @Test
    void massRevisionExpiresNormalizedDerivationsAndOldValuesRemain(@TempDir Path dir) {
        try (Database db = new Database(dir.resolve("case.db").toString())) {
            CasePack p = pack(db);
            HistoryState s = p.service().state(CasePack.HISTORY_ID);
            // two mass versions exist
            assertTrue(s.masses.size() >= 2);
            // current melt features are tied to the new mass id
            List<FeatureResult> melts = s.featuresForRegion(p.meltId);
            assertFalse(melts.isEmpty());
            assertEquals(s.currentMass().massId(), melts.get(0).massIdUsed());
            // history at the mass checkpoint still shows the old mass basis
            HistoryState before = p.service().stateAt(CasePack.HISTORY_ID, p.buildBoundaryPhaseCheckpoints().massCheckpoint());
            List<FeatureResult> old = before.featuresForRegion(p.meltId);
            assertNotEquals(s.currentMass().massId(), old.get(0).massIdUsed());
        }
    }

    @Test
    void conflictingDecisionsAreBothAuditable(@TempDir Path dir) {
        try (Database db = new Database(dir.resolve("case.db").toString())) {
            CasePack p = pack(db);
            HistoryState s = p.service().state(CasePack.HISTORY_ID);
            List<dsc.events.Event> accepts = s.events.stream()
                    .filter(e -> e.getType() == dsc.events.EventType.CANDIDATE_ACCEPTED).toList();
            assertTrue(accepts.size() >= 2, "two reviewers' decisions both remain events");
            assertEquals("reviewer-A", accepts.get(0).getActor());
            assertEquals("reviewer-B", accepts.get(1).getActor());
            // unaccepted baseline candidates are still present, not overwritten
            long retained = s.baselines.values().stream()
                    .filter(b -> b.regionId().equals(p.meltId) && !s.rejected.containsKey(b.id())).count();
            assertTrue(retained >= 2);
        }
    }

    @Test
    void emptyRegionStaysIndeterminate(@TempDir Path dir) {
        try (Database db = new Database(dir.resolve("case.db").toString())) {
            CasePack p = pack(db);
            HistoryState s = p.service().state(CasePack.HISTORY_ID);
            boolean anyIndeterminate = s.baselines.values().stream()
                    .anyMatch(b -> b.regionId().equals(p.emptyId) && b.status() == NodeStatus.INDETERMINATE);
            assertTrue(anyIndeterminate);
        }
    }
}
