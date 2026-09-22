package org.dsc.store;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.time.Instant;

/**
 * 机器可读取的事件。append-only：接受、否定、参数调整都产生新事件，
 * 回到任意旧事件时按其依赖版本恢复当时结论，绝不用最新结果覆盖历史。
 *
 * type 取值（判定依据也以 code 写入 payload.judgement，供界面与测试直接读回）：
 *  CASE_OPENED
 *  SOURCE_RECORDED        仪器输出/质量/速率/气氛/标定/切线分别存档
 *  SOURCE_AMENDED         只改动一个直接来源（局部依赖改动）
 *  SEGMENT_PARSED         解析状态（含反向热流/非等间隔诊断）
 *  BASELINE_DERIVED       并列基线候选全部保留
 *  BASELINE_ADOPTED       人工采纳某候选（未采纳候选不删除）
 *  BASELINE_REJECTED      人工否定
 *  TANGENT_ADJUSTED       参数调整（拖区间/改切线）
 *  TRANSITION_DERIVED     onset/peak/endset/焓变及误差来源
 *  MASS_REVISED           质量新版本 -> 归一化派生全部 STALE
 *  EMPTY_WINDOW_CHECKED   空区间检验
 *  CROSS_SEGMENT_REJECTED 跨加热/冷却段混用被拒
 *  CONFLICT_DETECTED      两位使用者同版本编辑
 *  CASE_REPLAYED          反例档案重放
 */
public record EventRecord(
        String eventId,
        String caseId,
        String parentId,
        long seq,
        String type,
        String actor,
        Instant at,
        ObjectNode payload,
        String basisVersion   // 编辑所基于的 head eventId（乐观并发）
) {
    public static final ObjectMapper MAPPER = new ObjectMapper();

    public static ObjectNode payload(Object... kv) {
        ObjectNode n = MAPPER.createObjectNode();
        for (int i = 0; i + 1 < kv.length; i += 2) {
            String key = String.valueOf(kv[i]);
            Object v = kv[i + 1];
            if (v == null) n.putNull(key);
            else if (v instanceof Integer z) n.put(key, z);
            else if (v instanceof Long z) n.put(key, z);
            else if (v instanceof Double z) n.put(key, z);
            else if (v instanceof Boolean z) n.put(key, z);
            else n.put(key, String.valueOf(v));
        }
        return n;
    }

    public JsonNode p(String key) { return payload.get(key); }
    public String pstr(String key) {
        JsonNode v = payload.get(key);
        return v == null || v.isNull() ? null : v.asText();
    }
}
