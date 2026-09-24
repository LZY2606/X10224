package org.materials.dsc.io;

import org.materials.dsc.domain.CaseState;
import org.materials.dsc.domain.Direction;
import org.materials.dsc.engine.EngineService;
import org.materials.dsc.engine.Segmenter;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 加载仓库内离线案例包 fixtures/casepack：
 *  - pet_sample_history.csv：主案例（Heat1/Cool1/Heat2 同一履历）
 *  - edge_*.csv：七类可重复边缘情形
 * 所有实体使用稳定 ID，保证导出→删库→导入后身份/顺序/来源关系不变。
 */
public final class CasePackLoader {
    public static final String CASE_ID = "case-pet-thermal-transition";
    public static final String OUTPUT_MAIN = "out-pet-history-v1";
    public static final String MASS_V1 = "mass-pet-v1";
    public static final String MASS_V2 = "mass-pet-v2";
    public static final String RATE_ID = "rate-pet-10";
    public static final String ATMO_ID = "atmo-pet-n2";
    public static final String CAL_ID = "cal-pet-indium";

    private CasePackLoader() {
    }

    public static String readResource(String resource) {
        try (InputStream in = CasePackLoader.class.getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalStateException("casepack resource missing: " + resource);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    public static String readFixtureFile(Path rawDir, String name) {
        try {
            return Files.readString(rawDir.resolve(name));
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    /** 初始版本：六类直接来源分别存档，随后分段、检测、并列基线。 */
    public static CaseState seedMainCase(EngineService engine) {
        return seedMainCase(engine, CASE_ID);
    }

    public static CaseState seedMainCase(EngineService engine, String caseId) {
        if (engine.listCases().contains(caseId)) {
            return engine.load(caseId);
        }
        engine.createCaseWithId(caseId, "热转折案：PET 样品加热/冷却履历", "PET (virtual reference)",
                "DSC 基线、相变积分与扫描历史审阅器离线案例");
        String csv = readResource("/casepack/raw/pet_sample_history.csv");
        engine.archiveInstrumentOutput(caseId, OUTPUT_MAIN, "pet_sample_history.csv", csv,
                mainPrograms(), null);
        engine.recordRateWithId(caseId, RATE_ID, 10.0, "10 °C/min (program)");
        engine.recordAtmosphereWithId(caseId, ATMO_ID, "N2", 50.0, "50 mL/min nitrogen");
        engine.recordCalibrationWithId(caseId, CAL_ID, 1.0, 0.0, "indium Tm 156.60°C",
                "出厂后单点复核");
        engine.recordMassWithId(caseId, MASS_V1, 0.00842, "称量 v1（分析天平）");
        engine.detectTransitions(caseId, OUTPUT_MAIN);
        return engine.load(caseId);
    }

    private static Map<String, Segmenter.ProgramInfo> mainPrograms() {
        Map<String, Segmenter.ProgramInfo> map = new LinkedHashMap<>();
        map.put("Heat1", new Segmenter.ProgramInfo(Direction.HEATING, 10.0, "Heat1"));
        map.put("Cool1", new Segmenter.ProgramInfo(Direction.COOLING, 10.0, "Cool1"));
        map.put("Heat2", new Segmenter.ProgramInfo(Direction.HEATING, 10.0, "Heat2"));
        return map;
    }
}
