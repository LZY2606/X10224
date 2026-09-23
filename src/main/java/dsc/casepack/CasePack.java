package dsc.casepack;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import dsc.derive.Engine;
import dsc.derive.Workspace;
import dsc.store.Store;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 离线案例包「热转折案」：材料组把加热与冷却 DSC 序列放进同一样品履历，
 * 为玻璃化、结晶和熔融事件建立可重算的基线与积分。
 * 所有 fixture 都在仓库内（/casepack），测试与演示共用。
 */
public class CasePack {
    private static final Gson GSON = new Gson();

    public static String resourceText(String name) {
        try (InputStream in = CasePack.class.getResourceAsStream("/casepack/" + name)) {
            if (in == null) throw new IllegalStateException("missing casepack resource: " + name);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static Map<String, Object> resourceJson(String name) {
        return GSON.fromJson(resourceText(name), new TypeToken<Map<String, Object>>() {}.getType());
    }

    /** 初始导入：仪器输出、样品质量、扫描速率、气氛、温度标定、用户切线分别存档。 */
    public static List<Map<String, Object>> importAll(Workspace ws, String sample) throws SQLException {
        List<Map<String, Object>> log = new ArrayList<>();
        importInstrument(ws, sample, "run1_heating", "run1_heating.csv");
        importInstrument(ws, sample, "run1_cooling", "run1_cooling.csv");
        importInstrument(ws, sample, "run2_heating", "run2_heating.csv");
        importInstrument(ws, sample, "run3_unknown_units", "run3_unknown_units.csv");
        importInstrument(ws, sample, "misleading_name", "misleading_name.csv");
        ws.importSource(sample, Engine.SRC_MASS, "mass", resourceJson("mass_v1.json"));
        ws.importSource(sample, Engine.SRC_RATE, "rate", resourceJson("scan_rate.json"));
        ws.importSource(sample, Engine.SRC_ATM, "atm", resourceJson("atmosphere.json"));
        ws.importSource(sample, Engine.SRC_CAL, "cal", resourceJson("temp_calibration.json"));
        Map<String, Object> derived = ws.engine().deriveAll(sample);
        ws.store().appendEvent(sample, "CASEPACK_IMPORTED", Map.of(
                "note", "初始导入完成：五份仪器输出与质量/速率/气氛/标定分别存档",
                "propagation", derived.get("report")), null);
        log.add(step("import", "初始导入", "仪器输出×5、质量 v1、扫描速率、气氛、温度标定分别存档；派生层完成首轮归一化"));
        return log;
    }

    private static void importInstrument(Workspace ws, String sample, String run, String file) throws SQLException {
        String text = resourceText(file);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("fileName", file);
        payload.put("rawText", text);
        ws.importSource(sample, Engine.SRC_INST, run, payload);
    }

    /**
     * 场景重放：边界组合 + 参数修订 + 局部源改动 + 互相冲突的人工决定。
     * 返回每一步的可见日志（输入片段、判定状态、受影响对象）。
     */
    public static List<Map<String, Object>> runScenario(Workspace ws, String sample) throws Exception {
        List<Map<String, Object>> log = new ArrayList<>();

        // 1) 审阅者建立事件窗口：玻璃化 / 结晶 / 熔融（重叠双蜂）/ 冷却结晶 / 空区间
        ws.createWindow(sample, "run1_heating", 0, 45, 95);
        ws.createWindow(sample, "run1_heating", 0, 100, 145);
        ws.createWindow(sample, "run1_heating", 0, 160, 200);
        ws.createWindow(sample, "run1_cooling", 0, 120, 155);
        ws.createWindow(sample, "run1_heating", 0, 400, 410); // 空区间 -> 诊断
        log.add(step("windows", "建立事件窗口",
                "玻璃化 45-95°C、结晶 100-145°C、熔融 160-200°C（重叠转折）、冷却结晶 120-155°C、空区间 400-410°C（触发 EMPTY_INTERVAL 诊断）"));

        // 2) 用户李采纳玻璃化的 SIGMOID 基线
        String glassWin = "win-run1_heating-0-45-95";
        Store.Version dec = ws.store().current(sample, Engine.DEC_BASELINE, glassWin).orElseThrow();
        ws.decide(sample, glassWin, "SIGMOID", "ADOPT", "李", dec.id());
        log.add(step("adopt-glass", "采纳基线（李）", "玻璃化事件采纳 SIGMOID 基线，其余候选保留为 CANDIDATE"));

        // 3) 参数修订：质量 v1 -> v2，所有归一化派生过期并重算
        Map<String, Object> massV2 = resourceJson("mass_v2.json");
        Store.Version massCur = ws.store().current(sample, Engine.SRC_MASS, "mass").orElseThrow();
        Map<String, Object> prop = ws.reviseSource(sample, Engine.SRC_MASS, "mass", massV2, massCur.id());
        log.add(step("mass-revision", "参数修订：样品质量 v2",
                "质量修订后所有归一化派生过期，依赖图传播重算：" + summarize(prop)));

        // 4) 局部源改动：只更新熔融窗口的用户切线，影响被限制在该事件
        String meltWin = "win-run1_heating-0-160-200";
        Map<String, Object> tangent = resourceJson("tangents_melting.json");
        Store.Version tanCur = ws.store().current(sample, Engine.SRC_TANGENT, meltWin).orElse(null);
        Map<String, Object> prop2 = ws.reviseSource(sample, Engine.SRC_TANGENT, meltWin, tangent,
                tanCur == null ? null : tanCur.id());
        log.add(step("local-tangent", "局部源改动：熔融窗口用户切线",
                "只有熔融事件的分析被重算，其余节点仍有效：" + summarize(prop2)));

        // 5) 互相冲突的人工决定：李与王从同一版本采纳不同基线
        String crysWin = "win-run1_heating-0-100-145";
        Store.Version base = ws.store().current(sample, Engine.DEC_BASELINE, crysWin).orElseThrow();
        ws.decide(sample, crysWin, "LINEAR", "ADOPT", "李", base.id());
        try {
            ws.decide(sample, crysWin, "LEFT_TANGENT", "ADOPT", "王", base.id());
            throw new IllegalStateException("expected conflict");
        } catch (Store.Conflict conflict) {
            // 王基于过期基准版本 -> 冲突 -> 保留双方候选
            Map<String, Object> myPayload = new LinkedHashMap<>(conflict.current.payload());
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> cands = (List<Map<String, Object>>) myPayload.get("candidates");
            for (Map<String, Object> c : cands) {
                if ("LEFT_TANGENT".equals(c.get("kind"))) c.put("status", "ADOPTED");
            }
            @SuppressWarnings("unchecked")
            List<String> adopted = new ArrayList<>((List<String>) myPayload.get("adopted"));
            adopted.add("LEFT_TANGENT");
            myPayload.put("adopted", adopted);
            ws.resolveKeepBoth(sample, crysWin, conflict.current, myPayload, "李", "王");
            log.add(step("conflict", "互相冲突的人工决定",
                    "李采纳 LINEAR、王采纳 LEFT_TANGENT（同一基准版本）-> 冲突 -> keep-both 解决，双方候选均保留"));
        }

        // 6) 空区间与反向热流等诊断已在派生层记录，这里汇总可见结论
        ws.store().appendEvent(sample, "SCENARIO_COMPLETE", Map.of(
                "note", "场景重放完成：反向热流、非等间隔温度、重叠转折、循环段切换、质量版本变更、并列基线、空区间全部经过",
                "steps", log.size()), null);
        log.add(step("done", "重放完成", "可从最终状态反查哪条材料、哪版规则和哪次决定造成了差异"));
        return log;
    }

    @SuppressWarnings("unchecked")
    private static String summarize(Map<String, Object> propagation) {
        List<Map<String, Object>> report = (List<Map<String, Object>>) propagation.get("report");
        long recomputed = report.stream().filter(r -> !"VALID".equals(r.get("action"))).count();
        long valid = report.stream().filter(r -> "VALID".equals(r.get("action"))).count();
        return recomputed + " 项重算 / " + valid + " 项仍有效";
    }

    private static Map<String, Object> step(String id, String title, String detail) {
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("step", id);
        s.put("title", title);
        s.put("detail", detail);
        return s;
    }
}
