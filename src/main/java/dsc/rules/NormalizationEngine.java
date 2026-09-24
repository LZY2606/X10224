package dsc.rules;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import dsc.domain.Diag;
import dsc.domain.RawPoint;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 归一化：原始温度/热流 -> 规范坐标（K、W/g，吸热向上为正）。
 * 关键语义：
 *  - 吸放热方向只由仪器元数据 signBasis 决定，绝不从文件名猜测；
 *  - 无法换算的点排除并附诊断，但不从来源删除；
 *  - 加热/冷却方向由扫描程序 programDirection 决定，积分时禁止跨段混用。
 */
public final class NormalizationEngine {
    private static final ObjectMapper M = new ObjectMapper();

    public record Input(String segmentId,
                        String programDirection,
                        String signBasis,
                        double calOffsetK,
                        double calSlope,
                        double massG,
                        String massVersion,
                        List<RawPoint> rawPoints,
                        String fileName) {
    }

    public NormalizedCurve normalize(Input in) {
        List<String> diags = new ArrayList<>();
        String signStatus = "OK";
        double signMultiplier = 1.0;
        String basis = in.signBasis == null ? "UNKNOWN" : in.signBasis.trim().toUpperCase();

        switch (basis) {
            case "ENDO_POSITIVE", "ENDO_UP" -> signMultiplier = 1.0;
            case "EXO_POSITIVE", "EXO_UP" -> signMultiplier = -1.0;
            default -> {
                signStatus = "INVALID";
                diags.add(Diag.error("SIGN_AMBIGUOUS",
                        "仪器元数据未声明吸放热方向（EXO_POSITIVE/ENDO_POSITIVE）；"
                                + "不能从文件名 '" + in.fileName() + "' 猜测方向",
                        in.segmentId()).message());
            }
        }

        String direction = in.programDirection == null ? "UNKNOWN" : in.programDirection.trim().toUpperCase();
        if (!"HEATING".equals(direction) && !"COOLING".equals(direction)) {
            signStatus = "INVALID";
            diags.add(Diag.error("DIRECTION_UNKNOWN",
                    "扫描程序未声明 HEATING/COOLING，禁止与其它段混用", in.segmentId()).message());
        }

        List<RawPoint> sorted = in.rawPoints().stream()
                .sorted(Comparator.comparingLong(RawPoint::seq)).toList();

        List<NormPoint> out = new ArrayList<>();
        boolean sawNonUniform = false;
        double prevT = Double.NaN;
        for (RawPoint rp : sorted) {
            if (rp.excluded() || rp.tK() == null || rp.hfW() == null) {
                String msg = rp.diag() != null ? rp.diag() : "缺失点";
                diags.add(Diag.warn("UNCONVERTIBLE_POINT",
                        "seq=" + rp.seq() + " 保留在原位置但不参与计算: " + msg,
                        in.segmentId()).message());
                continue;
            }
            double tK = in.calSlope() * rp.tK() + in.calOffsetK();
            double y = signMultiplier * rp.hfW() / in.massG();
            out.add(new NormPoint(tK, y, false));
            if (!Double.isNaN(prevT)) {
                double d = tK - prevT;
                if (d <= 0 && "HEATING".equals(direction)) {
                    diags.add(Diag.warn("NON_MONOTONIC_TEMPERATURE",
                            "seq=" + rp.seq() + " 温度非单调（实际 ΔT=" + d + "K），积分按单调子段处理",
                            in.segmentId()).message());
                }
                if (Math.abs(d - 5.0) > 1.0 && !sawNonUniform) {
                    sawNonUniform = true;
                    diags.add(Diag.info("NON_UNIFORM_GRID",
                            "非等间隔温度采样（出现 ΔT=" + String.format("%.2f", Math.abs(d))
                                    + "K），积分使用逐点 ΔT 而非名义步长",
                            in.segmentId()).message());
                }
            }
            prevT = tK;
        }

        if (in.massG() <= 0) {
            diags.add(Diag.error("INVALID_MASS",
                    "质量版本 " + in.massVersion() + " 无效，归一化无法进行", in.segmentId()).message());
        }

        out.sort(Comparator.comparingDouble(NormPoint::tK));
        return new NormalizedCurve(in.segmentId(), direction, basis, signStatus,
                in.massG(), in.massVersion(), out, diags);
    }

    /** 从仪器输出来源 payload 解析采样点（由导入层填充）。 */
    public static JsonNode pointsNode(List<RawPoint> points) throws Exception {
        ArrayNode arr = M.createArrayNode();
        for (RawPoint p : points) {
            var o = arr.addObject();
            o.put("seq", p.seq());
            o.put("tRaw", p.tRaw());
            o.put("hfRaw", p.hfRaw());
            if (p.tK() != null) {
                o.put("tK", p.tK());
            }
            if (p.hfW() != null) {
                o.put("hfW", p.hfW());
            }
            o.put("excluded", p.excluded());
            if (p.diag() != null) {
                o.put("diag", p.diag());
            }
        }
        return arr;
    }
}
