package dsc.web;

import dsc.casepack.CasePack;
import dsc.casepack.CasePackLoader;
import dsc.engine.AppEngine;
import dsc.store.Database;
import dsc.store.Json;
import dsc.store.Repository;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 应用单例：数据库 + 引擎。首次启动自动从仓库内离线案例包播种。
 */
public class DscService {
    private static DscService instance;
    private final Path dbPath;
    private AppEngine engine;

    private DscService(Path dbPath) {
        this.dbPath = dbPath;
        open();
    }

    public static synchronized DscService get() {
        if (instance == null) {
            Path dir = Path.of("data");
            try {
                Files.createDirectories(dir);
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
            instance = new DscService(dir.resolve("dsc-review.sqlite"));
        }
        return instance;
    }

    private void open() {
        engine = new AppEngine(new Repository(Database.open(dbPath.toString())));
    }

    public AppEngine engine() {
        return engine;
    }

    public synchronized boolean seeded() {
        try {
            return engine.currentState().get("caseId") != null;
        } catch (Exception e) {
            return false;
        }
    }

    /** 播种：加载仓库自带案例包并完成初始导入。 */
    public synchronized void seedFromCasePack(Path caseDir) throws Exception {
        CasePack pack = caseDir != null && Files.exists(caseDir.resolve("manifest.json"))
                ? CasePackLoader.loadFromDirectory(caseDir)
                : CasePackLoader.loadFromResources();
        engine.importCasePack(pack, caseDir);
    }

    /** 删除本地数据库后再从快照导入；身份/顺序/来源关系保持。 */
    public synchronized void wipeAndImportSnapshot(String json) throws Exception {
        Files.deleteIfExists(dbPath);
        for (String suffix : new String[]{"-wal", "-shm"}) {
            Files.deleteIfExists(Path.of(dbPath + suffix));
        }
        open();
        engine.importSnapshot(json);
    }

    public synchronized void wipeAndReseed() throws Exception {
        Files.deleteIfExists(dbPath);
        for (String suffix : new String[]{"-wal", "-shm"}) {
            Files.deleteIfExists(Path.of(dbPath + suffix));
        }
        open();
        seedFromCasePack(null);
    }

    public synchronized Map<String, Object> status() {
        try {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("seeded", seeded());
            if (seeded()) {
                m.put("revision", engine.revision());
                m.put("ruleVersion", engine.rules().version());
            }
            return m;
        } catch (Exception e) {
            return Map.of("seeded", false, "error", e.getMessage());
        }
    }
}
