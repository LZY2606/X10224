package dsc;

import dsc.casepack.CaseBootstrap;
import dsc.casepack.CasePack;
import dsc.casepack.FixtureWriter;
import dsc.storage.Database;
import dsc.web.WebServer;
import io.javalin.Javalin;

import java.nio.file.Path;

/**
 * Entry point. First run creates ./data/dsc-review.db, writes the offline
 * fixtures and seeds the thermal-turning case through the application service.
 */
public final class Main {

    public static void main(String[] args) throws Exception {
        String host = "127.0.0.1";
        int port = 5343;
        for (int i = 0; i < args.length - 1; i++) {
            if (args[i].equals("--host")) host = args[i + 1];
            if (args[i].equals("--port")) port = Integer.parseInt(args[i + 1]);
        }
        Path dataDir = Path.of("data");
        java.nio.file.Files.createDirectories(dataDir);
        Path dbPath = dataDir.resolve("dsc-review.db");
        Path fixtureDir = dataDir.resolve("fixtures/thermal-turning-case");

        FixtureWriter.writeAll(Path.of("src/main/resources/fixtures/thermal-turning-case"));
        CaseBootstrap.copyFixturesTo(fixtureDir);

        boolean fresh = CaseBootstrap.needsSeed(dbPath);
        Database db = new Database(dbPath.toString());
        CasePack pack = fresh ? CaseBootstrap.seed(db) : new CasePack(db);

        WebServer server = new WebServer(db, pack, fixtureDir);
        Javalin app = server.start(host, port);
        System.out.println("DSC review server: http://" + host + ":" + port);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> { app.stop(); db.close(); }));
    }
}
