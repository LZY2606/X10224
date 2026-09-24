package dsc;

import dsc.casepack.CasePack;
import dsc.casepack.CasePackLoader;
import dsc.engine.AppEngine;
import dsc.store.Database;
import dsc.store.Repository;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public final class Support {
    private Support() {
    }

    public static Path tempDb() {
        try {
            Path p = Files.createTempFile("dsc-test-", ".sqlite");
            Files.deleteIfExists(p);
            return p;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static AppEngine newEngine(Path db) {
        return new AppEngine(new Repository(Database.open(db.toString())));
    }

    public static AppEngine seeded() throws Exception {
        AppEngine e = newEngine(tempDb());
        e.importCasePack(CasePackLoader.loadFromResources(), null);
        return e;
    }

    public static CasePack pack() throws Exception {
        return CasePackLoader.loadFromResources();
    }

    public static CasePackLoader.CsvData seg(String name) throws Exception {
        return CasePackLoader.parseSegmentResource(
                CasePackLoader.RESOURCE_ROOT + "/segments/" + name);
    }

    public static List<String> eventTypes(AppEngine e) {
        return ((List<?>) e.currentState().get("events")).stream()
                .map(o -> ((java.util.Map<?, ?>) o).get("type").toString()).toList();
    }

    @SuppressWarnings("unchecked")
    public static java.util.Map<String, Object> state(AppEngine e) {
        try {
            return e.currentState();
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    @SuppressWarnings("unchecked")
    public static java.util.List<java.util.Map<String, Object>> artifacts(AppEngine e) {
        return (java.util.List<java.util.Map<String, Object>>) state(e).get("artifacts");
    }

    public static java.util.Map<String, Object> artifactById(AppEngine e, String id) {
        return artifacts(e).stream().filter(a -> a.get("id").equals(id)).findFirst().orElseThrow();
    }
}
