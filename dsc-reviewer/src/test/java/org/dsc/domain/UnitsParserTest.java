package org.dsc.domain;

import org.dsc.domain.Models.HeatFlowConvention;
import org.dsc.domain.Models.ScanDirection;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class UnitsParserTest {

    @Test
    void temperatureUnitsAllConvertToKelvin() {
        assertEquals(373.15, Units.toKelvin("100 C").value, 1e-9);
        assertEquals(373.15, Units.toKelvin("100 °C").value, 1e-9);
        assertEquals(373.15, Units.toKelvin("373.15 K").value, 1e-9);
        assertEquals(373.15, Units.toKelvin("212 F").value, 1e-6);
    }

    @Test
    void unconvertedValueStaysWithDiagnostic() {
        Units.Conversion bad = Units.toKelvin("N/A K");
        assertFalse(bad.ok());
        assertTrue(bad.diagnostic().contains("unparseable"));
        Units.Conversion weirdUnit = Units.heatFlowToWPerG("0.1 BTU/fortnight");
        assertFalse(weirdUnit.ok());
        assertTrue(weirdUnit.diagnostic().contains("power unit"));
    }

    @Test
    void heatFlowNormalization() {
        // mW/mg = W/g
        assertEquals(0.5, Units.heatFlowToWPerG("0.5 mW/mg").value, 1e-12);
        assertEquals(0.5e-3, Units.heatFlowToWPerG("0.5 mW/g").value, 1e-12);
        assertEquals(0.5e-3, Units.heatFlowToWPerG("0.5 W/kg").value, 1e-12);
    }

    @Test
    void directionComesFromHeaderNotFilename() {
        String block = """
            SEGMENT seq=1 direction=cooling convention=exo-up rate="-10 K/min" atmosphere="N2"
            0, 500 K, 0.1 mW/mg
            6, 499 K, 0.1 mW/mg
            """;
        var parsed = DscParser.parse("s1", 1, block);
        assertEquals(ScanDirection.COOLING, parsed.segment.direction());
        assertEquals(HeatFlowConvention.EXO_UP, parsed.segment.convention());
    }

    @Test
    void missingHeaderBlocksClassificationAndFlagsIt() {
        String block = """
            0, 300 K, 0.1 mW/mg
            6, 301 K, 0.1 mW/mg
            """;
        var parsed = DscParser.parse("s1", 1, block);
        assertEquals(ScanDirection.UNKNOWN, parsed.segment.direction());
        assertTrue(parsed.segment.diagnostics().stream()
                .anyMatch(d -> d.contains("must not be inferred")));
    }

    @Test
    void dirtyRowsAreKeptAsDiagnosticsNotDropped() {
        String block = """
            SEGMENT seq=4 direction=heating convention=exo-up rate="10 K/min" atmosphere="Air"
            0, 300 K, 0.1 mW/mg
            6, N/A K, 0.1 mW/mg
            12, 302 K, 0.1 BTU/fortnight
            18, 303 K, 0.1 mW/mg
            """;
        var parsed = DscParser.parse("s4", 4, block);
        assertEquals(2, parsed.segment.points().size(), "two good points only");
        assertEquals(2, parsed.parseDiagnostics.size(), "bad rows stay with diagnostics");
        assertTrue(parsed.rawLines.stream().anyMatch(l -> l.contains("N/A")));
    }

    @Test
    void nonUniformSpacingDetectedButNotResampled() {
        StringBuilder sb = new StringBuilder(
                "SEGMENT seq=1 direction=heating convention=exo-up rate=\"10 K/min\" atmosphere=\"N2\"\n");
        double t = 300;
        for (int i = 0; i < 30; i++) {
            sb.append(String.format("%.0f, %.2f K, 0.1 mW/mg%n", i * 6.0, t));
            t += (i < 15) ? 1.0 : 5.0;
        }
        var parsed = DscParser.parse("s1", 1, sb.toString());
        assertTrue(parsed.segment.nonUniformSpacing());
        assertEquals(30, parsed.segment.points().size());
    }
}
