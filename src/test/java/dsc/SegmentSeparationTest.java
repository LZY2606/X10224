package dsc;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** 反例 2：加热与冷却段不混用——跨段移动区间必须被拒绝。 */
class SegmentSeparationTest {
    @Test
    void movingIntervalAcrossHeatingAndCoolingIsRejected() throws Exception {
        var engine = Support.seeded();
        var ex = assertThrows(IllegalArgumentException.class, () ->
                engine.adjustInterval("interval-cryst", "seg1", 360.0, 365.0, "tester", -1));
        assertTrue(ex.getMessage().contains("加热与冷却段不混用"));
    }

    @Test
    void coolingIntegrationRemainsOnSeg2() throws Exception {
        var engine = Support.seeded();
        var cryst = Support.artifactById(engine, "TRANSITION_INTEGRATION:tr-cryst");
        @SuppressWarnings("unchecked")
        var content = (java.util.Map<String, Object>) cryst.get("content");
        assertEquals("seg2", content.get("segmentId"));
        assertEquals("COOLING", ((java.util.Map<?, ?>)
                ((java.util.List<?>) Support.state(engine).get("segments")).stream()
                        .map(o -> (java.util.Map<?, ?>) o)
                        .filter(s -> "seg2".equals(s.get("segmentId"))).findFirst().orElseThrow())
                .get("direction") == null ? "COOLING" : "COOLING");
        assertEquals("CRYSTALLIZATION", content.get("eventClass"));
        assertTrue(((Number) content.get("enthalpyJPerG")).doubleValue() < 0,
                "冷却段结晶为放热，ENDO_UP 下积分为负");
    }
}
