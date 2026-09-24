package org.materials.dsc.engine;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EngineMathTest {

    @Test
    void nonUniformGridIsDetected() {
        double[] uniform = {0, 1, 2, 3, 4};
        assertFalse(EngineMath.stepStats(uniform).nonUniform());
        double[] jittered = {0, 1.02, 3.1, 3.6, 5.4};
        assertTrue(EngineMath.stepStats(jittered).nonUniform());
    }

    @Test
    void trapezoidRespectsNonUniformSpacing() {
        double[] t = {0, 2, 2.5, 5};
        double[] y = {1, 1, 1, 1};
        assertEquals(5.0, EngineMath.trapezoidCelsius(t, y, 0, 3), 1e-12);
    }

    @Test
    void linearFitRecoversLine() {
        double[] x = {0, 1, 2, 3};
        double[] y = {2, 4, 6, 8};
        EngineMath.Line line = EngineMath.fit(x, y, 0, 3);
        assertEquals(2.0, line.slope(), 1e-12);
        assertEquals(2.0, line.intercept(), 1e-12);
        assertEquals(0.0, line.rms(), 1e-12);
    }
}
