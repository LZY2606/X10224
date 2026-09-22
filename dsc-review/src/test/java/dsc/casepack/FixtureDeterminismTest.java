package dsc.casepack;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class FixtureDeterminismTest {

    @Test
    void fixtureGenerationIsDeterministic(@TempDir Path dir) throws IOException {
        Path a = dir.resolve("a");
        Path b = dir.resolve("b");
        FixtureWriter.writeAll(a);
        FixtureWriter.writeAll(b);
        for (FixtureWriter.CsvFile f : FixtureWriter.files()) {
            assertArrayEquals(Files.readAllBytes(a.resolve(f.name())),
                    Files.readAllBytes(b.resolve(f.name())),
                    f.name() + " must regenerate byte-identically");
        }
    }

    @Test
    void bundledFixturesExistAndAreDomainMaterials(@TempDir Path dir) throws IOException {
        FixtureWriter.writeAll(dir);
        List<String> names = FixtureWriter.files().stream().map(FixtureWriter.CsvFile::name).toList();
        for (String name : names) {
            Path file = dir.resolve(name);
            assertTrue(Files.exists(file));
            String head = Files.readString(file).lines().findFirst().orElseThrow();
            assertTrue(head.contains("convention="), name + " header must declare convention");
            assertTrue(head.contains("cycle="), name + " header must declare cycle");
        }
        // bad cells were committed in the cycle-2 fixture
        String cycle2 = Files.readString(dir.resolve("rerun_heating_cycle2.txt"));
        assertTrue(cycle2.contains("n/a"));
    }

    @Test
    void csvImporterPreservesRawTokensAndReadsConventionFromHeader(@TempDir Path dir) throws IOException {
        FixtureWriter.writeAll(dir);
        var reversed = CaseCsvImporter.load(dir.resolve("reversed_run_01.txt"), "segX", 9, 20);
        assertEquals("unknown", reversed.conventionRaw, "convention from header, not file name");
        assertEquals(2, reversed.cycle);
        var heat = CaseCsvImporter.load(dir.resolve("DSC_run_heating.txt"), "segY", 1, 20);
        assertEquals("exo-up", heat.conventionRaw);
        assertFalse(heat.rows.isEmpty());
    }
}
