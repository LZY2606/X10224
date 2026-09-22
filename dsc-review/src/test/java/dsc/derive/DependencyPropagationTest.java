package dsc.derive;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DependencyPropagationTest {

    @Test
    void propagatesOnlyToAffectedDescendants() {
        DependencyGraph g = new DependencyGraph();
        g.record("bl:A", List.of("seg:s1", "tan:t1"));
        g.record("feat:F1", List.of("bl:A", "mass:m1"));
        g.record("bl:B", List.of("seg:s2", "tan:t2"));
        g.record("feat:F2", List.of("bl:B", "mass:m1"));
        var affected = g.downstream("seg:s1");
        assertTrue(affected.contains("bl:A"));
        assertTrue(affected.contains("feat:F1"));
        assertFalse(affected.contains("bl:B"), "other segment's baseline stays valid");
        assertFalse(affected.contains("feat:F2"));
        // mass revision reaches every normalized derivation
        var massAffected = g.downstream("mass:m1");
        assertTrue(massAffected.contains("feat:F1"));
        assertTrue(massAffected.contains("feat:F2"));
    }
}
