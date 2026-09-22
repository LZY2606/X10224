package dsc.core;

import dsc.casepack.CasePack;
import dsc.casepack.ScenarioRunner;
import dsc.domain.EnthalpyResult;
import dsc.domain.TransitionResult;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 焓变积分：非等间隔时间梯形积分必须回到合成曲线真值；
 * 反向热流（exo-up）导入后结晶焓符号必须为负；方向未决不得给符号。
 */
class EnthalpyIntegrationTest {

    private ProjectService seeded() {
        ProjectService svc = new ProjectService();
        new ScenarioRunner(svc).runAll();
        return svc;
    }

    private EnthalpyResult acceptedEnthalpy(ProjectService svc, String segmentId) {
        String accepted = svc.accepted(segmentId);
        List<EnthalpyResult> list = svc.enthalpies(segmentId);
        return list.stream().filter(e -> e.candidateId.equals(accepted)).findFirst()
                .orElse(list.get(0));
    }

    @Test
    void heatingMeltRecoversTruthEnthalpy() {
        ProjectService svc = seeded();
        EnthalpyResult en = acceptedEnthalpy(svc, "seg_heat1");
        assertEquals("OK", en.status);
        assertEquals("ENDOTHERMIC", en.heatDirection);
        assertTrue(en.signResolved);
        assertEquals(45.0, en.enthalpyJg, 1.2,
                "time-based trapezoid over 0.5 K grid should recover 45 J/g");
        assertTrue(en.errorSources.size() >= 4, "error sources must be itemized");
    }

    @Test
    void reversedHeatFlowCrystallizationIsExothermicNegative() {
        ProjectService svc = seeded();
        EnthalpyResult en = svc.enthalpies("seg_cool1").get(0);
        assertEquals("OK", en.status);
        assertEquals("EXOTHERMIC", en.heatDirection);
        assertTrue(en.enthalpyJg < 0, "exo-up instrument file must flip to negative internally");
        assertEquals(38.0, en.magnitudeJg, 1.2);
    }

    @Test
    void nonuniformGridUsesTimeTrapezoidWithoutResampling() {
        ProjectService svc = seeded();
        EnthalpyResult en = acceptedOrOnly(svc, "seg_nonuniform");
        assertEquals("OK", en.status);
        assertEquals(25.0, en.enthalpyJg, 1.5, "non-uniform grid integral must still recover truth");
        assertTrue(svc.reports.get("seg_nonuniform").diagnostics.stream()
                .anyMatch(d -> d.contains("NONUNIFORM_GRID")));
        boolean gridError = en.errorSources.stream().anyMatch(e -> e.code.equals("temperature_grid"));
        assertTrue(gridError);
    }

    @Test
    void unresolvedDirectionKeepsMagnitudeWithoutSign() {
        ProjectService svc = seeded();
        EnthalpyResult en = acceptedOrOnly(svc, "seg_mystery");
        assertFalse(en.signResolved);
        assertEquals("UNRESOLVED", en.heatDirection);
        assertTrue(Double.isNaN(en.enthalpyJg), "no guessed sign");
        assertEquals(30.0, en.magnitudeJg, 1.5);
        assertTrue(en.errorSources.stream().anyMatch(e -> e.code.equals("direction_unresolved")));
    }

    @Test
    void overlappingTransitionsKeepMultipleBaselineCandidates() {
        ProjectService svc = seeded();
        long okCandidates = svc.baselines("seg_overlap").stream().filter(b -> b.usable()).count();
        assertTrue(okCandidates >= 2, "multiple legal baselines for overlap must be retained");
        EnthalpyResult envelope = acceptedEnthalpy(svc, "seg_overlap");
        assertEquals(52.0, envelope.magnitudeJg, 2.5, "envelope baseline covers both peaks");
    }

    private EnthalpyResult acceptedOrOnly(ProjectService svc, String segmentId) {
        String accepted = svc.accepted(segmentId);
        List<EnthalpyResult> list = svc.enthalpies(segmentId);
        if (accepted != null) {
            return list.stream().filter(e -> e.candidateId.equals(accepted)).findFirst().orElseThrow();
        }
        return list.get(0);
    }
}
