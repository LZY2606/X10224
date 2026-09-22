package dsc.web;

import dsc.casepack.CaseBootstrap;
import dsc.casepack.CasePack;
import dsc.casepack.FixtureWriter;
import dsc.storage.Database;
import io.javalin.Javalin;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class WebSmokeTest {

    static Javalin app;
    static Database db;
    static int port;

    @BeforeAll
    static void up(@TempDir Path dir) throws IOException {
        Path dbFile = dir.resolve("web.db");
        db = new Database(dbFile.toString());
        CasePack pack = CaseBootstrap.seed(db);
        FixtureWriter.writeAll(dir.resolve("fixtures/thermal-turning-case"));
        app = new WebServer(db, pack, dir.resolve("fixtures")).start("127.0.0.1", 0);
        port = app.port();
    }

    @AfterAll
    static void down() {
        app.stop();
        db.close();
    }

    private HttpResponse<String> get(String path) throws Exception {
        return HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path)).build(),
                HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void historyApiHasStableIdentitiesAndJudgments() throws Exception {
        var res = get("/api/history");
        assertEquals(200, res.statusCode());
        String body = res.body();
        assertTrue(body.contains("thermal-turning-case"));
        assertTrue(body.contains("SIGN_NOT_FROM_FILENAME"));
        assertTrue(body.contains("EMPTY_REGION"));
        assertTrue(body.contains("MASS_REVISION_INVALIDATES_DERIVATIONS"));
        assertTrue(body.contains("onset"));
        assertTrue(body.contains("errorBudget"));
    }

    @Test
    void staticIndexIsServed() throws Exception {
        var res = get("/");
        assertEquals(200, res.statusCode());
        assertTrue(res.body().contains("热转折案"));
    }

    @Test
    void storyEndpointListsSteps() throws Exception {
        var res = get("/api/story");
        assertEquals(200, res.statusCode());
        assertTrue(res.body().contains("mass-revision"));
        assertTrue(res.body().contains("local-amend"));
        assertTrue(res.body().contains("conflict-decision"));
    }
}
