package org.materials.dsc.engine;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.materials.dsc.domain.BaselineCandidate;
import org.materials.dsc.domain.CaseState;
import org.materials.dsc.domain.Direction;
import org.materials.dsc.domain.EnthalpyResult;
import org.materials.dsc.domain.Segment;
import org.materials.dsc.domain.ThermalSign;
import org.materials.dsc.domain.Transition;
import org.materials.dsc.io.CasePackLoader;
import org.materials.dsc.io.ScenarioReplay;
import org.materials.dsc.store.EventStore;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EnginePipelineTest {

    private EngineService engine;

    @BeforeEach
    void setUp() {
        engine = new EngineService(EventStore.memory());
    }

    @Test
    void mainCaseKeepsHeatingAndCoolingSeparate() {
        CaseState st = CasePackLoader.seedMainCase(engine);
        assertEquals(3, st.segments.size());
        long heating = st.segments.values().stream().filter(Segment::heating).count();
        long cooling = st.segments.values().stream().filter(Segment::cooling).count();
        assertEquals(2, heating);
        assertEquals(1, cooling);
        assertFalse(st.transitions.isEmpty());
        for (Transition tr : st.transitions.values()) {
            Segment seg = st.segments.get(tr.segmentId());
            assertEquals(seg.direction(), tr.segmentDirection());
        }
    }

    @Test
    void meltOnHeatingIsEndothermicAndCrystallizationOnCoolingExothermic() {
        CaseState st = CasePackLoader.seedMainCase(engine);
        Segment heat1 = st.segments.values().stream()
                .filter(s -> s.cycleLabel().equals("Heat1")).findFirst().orElseThrow();
        List<Transition> heat1Trs = st.transitions.values().stream()
                .filter(t -> t.segmentId().equals(heat1.id())).toList();
        assertEquals(1, heat1Trs.size());
        assertEquals(ThermalSign.ENDOTHERMIC, heat1Trs.get(0).thermalSign());

        Segment cool1 = st.segments.values().stream()
                .filter(s -> s.cycleLabel().equals("Cool1")).findFirst().orElseThrow();
        List<Transition> coolTrs = st.transitions.values().stream()
                .filter(t -> t.segmentId().equals(cool1.id())).toList();
        assertEquals(1, coolTrs.size());
        assertEquals(ThermalSign.EXOTHERMIC, coolTrs.get(0).thermalSign());
    }

    @Test
    void parallelBaselinesAreKeptAndAdoptionDoesNotErase() {
        CaseState st = CasePackLoader.seedMainCase(engine);
        Transition tr = st.transitions.values().stream()
                .filter(t -> t.segmentDirection() == Direction.HEATING)
                .findFirst().orElseThrow();
        engine.deriveBaselines(st.info.id(), tr.id(), null, null, null, null);
        st = engine.load(st.info.id());
        List<BaselineCandidate> cands = st.baselinesOf(tr.id());
        assertTrue(cands.size() >= 1);
        engine.adoptBaseline(st.info.id(), tr.id(), cands.get(0).id());
        st = engine.load(st.info.id());
        List<BaselineCandidate> after = st.baselinesOf(tr.id());
        assertEquals(cands.size(), after.size(), "未采纳候选必须保留");
        assertTrue(after.stream().anyMatch(BaselineCandidate::adopted));
        assertTrue(after.stream().anyMatch(b -> !b.adopted()));
    }

    @Test
    void analysisCarriesErrorBudgetAndStableValueIds() {
        CaseState st = CasePackLoader.seedMainCase(engine);
        Transition tr = st.transitions.values().stream()
                .filter(t -> t.segmentDirection() == Direction.HEATING)
                .findFirst().orElseThrow();
        engine.deriveBaselines(st.info.id(), tr.id(), null, null, null, null);
        st = engine.load(st.info.id());
        BaselineCandidate bl = st.baselinesOf(tr.id()).get(0);
        engine.adoptBaseline(st.info.id(), tr.id(), bl.id());
        st = engine.load(st.info.id());
        EnthalpyResult a = st.analyses.values().iterator().next();
        assertNotNull(a.enthalpy().valueId());
        assertNotNull(a.specificEnthalpy());
        assertTrue(a.errorBudget().size() >= 3);
        assertTrue(a.enthalpy().errors().stream()
                .anyMatch(e -> e.code().equals("baseline")));
    }

    @Test
    void massRevisionStalesNormalizedDerived() {
        CaseState st = CasePackLoader.seedMainCase(engine);
        Transition tr = st.transitions.values().stream()
                .filter(t -> t.segmentDirection() == Direction.HEATING)
                .findFirst().orElseThrow();
        engine.deriveBaselines(st.info.id(), tr.id(), null, null, null, null);
        st = engine.load(st.info.id());
        engine.adoptBaseline(st.info.id(), tr.id(), st.baselinesOf(tr.id()).get(0).id());
        engine.recordMassWithId(st.info.id(), CasePackLoader.MASS_V2, 0.00899, "称量修订 v2");
        st = engine.load(st.info.id());
        EnthalpyResult a = st.analyses.values().iterator().next();
        assertTrue(a.stale(), "质量修订后归一化派生必须过期");
        assertEquals("sample-mass-revised", a.invalidatedReason());
        assertTrue(a.specificEnthalpy().stale());
    }

    @Test
    void scenarioReportsAreDeterministic() {
        List<ScenarioReplay.ScenarioReport> reports = ScenarioReplay.runAll(engine);
        assertEquals(7, reports.size());
        for (ScenarioReplay.ScenarioReport r : reports) {
            assertFalse(r.findings().isEmpty(), r.id());
        }
        ScenarioReplay.ScenarioReport reverse = reports.get(0);
        assertTrue(reverse.findings().get(0).contains("endothermic"),
                "exo-down 仪器上的正峰必须规范化为吸热: " + reverse.findings());
    }
}
