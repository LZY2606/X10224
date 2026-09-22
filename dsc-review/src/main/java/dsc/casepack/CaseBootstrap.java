package dsc.casepack;

import dsc.events.HistoryState;
import dsc.service.DscService;
import dsc.service.DscServiceDerive;
import dsc.storage.Database;
import dsc.storage.EventStore;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;

/**
 * Seeds a fresh database with the thermal-turning case. Fixtures are copied
 * out of the jar/resources; the story is then scripted through the same
 * application service the UI and tests use.
 */
public final class CaseBootstrap {

    private CaseBootstrap() {}

    public static boolean needsSeed(Path dbPath) {
        return !Files.exists(dbPath);
    }

    public static CasePack seed(Database db) {
        CasePack pack = new CasePack(db);
        pack.build();
        pack.buildBoundaryPhase();
        return pack;
    }

    public static List<String> fixtureResourceNames() {
        return List.of("DSC_run_heating.txt", "DSC_run_cooling.txt", "reversed_run_01.txt",
                "rerun_heating_cycle2.txt", "overlap_run.txt", "README.txt");
    }

    public static void copyFixturesTo(Path targetDir) throws IOException {
        Files.createDirectories(targetDir);
        for (String name : fixtureResourceNames()) {
            try (InputStream in = CaseBootstrap.class.getResourceAsStream("/fixtures/thermal-turning-case/" + name)) {
                if (in == null) throw new IOException("missing bundled fixture " + name);
                Files.copy(in, targetDir.resolve(name), StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }
}
