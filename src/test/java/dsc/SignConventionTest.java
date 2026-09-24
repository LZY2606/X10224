package dsc;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 反例 1：吸放热方向不能靠文件名猜测。
 * seg1 文件名带 heating 但表头 EXO_POSITIVE，熔融为负向；规范化后必须转成 ENDO_UP 正向。
 */
class SignConventionTest {
    @Test
    void exoPositiveFileFlipsToCanonicalEndoUp() throws Exception {
        var engine = Support.seeded();
        var events = Support.eventTypes(engine);
        assertTrue(events.contains("SIGN_CONVENTION_RESOLVED"));
        var state = Support.state(engine);
        @SuppressWarnings("unchecked")
        var seg = ((List<Map<String, Object>>) state.get("segments")).stream()
                .filter(s -> "seg1".equals(s.get("segmentId"))).findFirst().orElseThrow();
        @SuppressWarnings("unchecked")
        var curve = (Map<String, Object>) seg.get("curve");
        assertEquals("ENDO_UP", curve.get("signBasis"));
        assertEquals("OK", curve.get("signStatus"));

        var melt = Support.artifactById(engine, "TRANSITION_INTEGRATION:tr-melt");
        @SuppressWarnings("unchecked")
        var content = (Map<String, Object>) melt.get("content");
        assertEquals("MELTING", content.get("eventClass"));
        assertEquals("ENDOTHERMIC", content.get("thermalSign"));
        double enthalpy = ((Number) content.get("enthalpyJPerG")).doubleValue();
        assertTrue(enthalpy > 0, "规范化后熔融（吸热向上）积分必须为正，原始文件中它是负向峰");
    }

    @Test
    void signEventMentionsFileNameIsNotUsed() throws Exception {
        var engine = Support.seeded();
        @SuppressWarnings("unchecked")
        var evs = (List<Map<String, Object>>) Support.state(engine).get("events");
        var signEvent = evs.stream().filter(e -> "SIGN_CONVENTION_RESOLVED".equals(e.get("type")))
                .findFirst().orElseThrow();
        String summary = signEvent.get("summary").toString();
        assertTrue(summary.contains("seg1_heating.csv"));
        assertTrue(summary.contains("不参与判断"));
    }
}
