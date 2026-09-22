package org.dsc.web;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 纯 Java SVG 渲染：曲线、并列基线候选、onset/peak/endset 标记、窗口范围。
 * 前端在其之上叠加可拖动事件；拖动结果回写 TANGENT_ADJUSTED/EMPTY_WINDOW 事件。
 */
public final class SvgChart {
    private SvgChart() {}

    public static String chart(JsonNode caseView, int seqNo) {
        JsonNode segment = null;
        for (JsonNode s : caseView.get("segments")) {
            JsonNode value = s.get("value");
            if (value.get("seqNo").asInt() == seqNo) { segment = s; break; }
        }
        if (segment == null) return emptySvg("segment " + seqNo + " 不存在");
        JsonNode seg = segment.get("value");
        JsonNode points = seg.get("points");

        double tMin = Double.POSITIVE_INFINITY, tMax = Double.NEGATIVE_INFINITY;
        double hMin = Double.POSITIVE_INFINITY, hMax = Double.NEGATIVE_INFINITY;
        List<double[]> pts = new ArrayList<>();
        for (JsonNode p : points) {
            double t = p.get("temperature").asDouble();
            double h = p.get("heatFlow").asDouble();
            pts.add(new double[]{t, h});
            tMin = Math.min(tMin, t); tMax = Math.max(tMax, t);
            hMin = Math.min(hMin, h); hMax = Math.max(hMax, h);
        }
        if (pts.isEmpty()) return emptySvg("segment " + seqNo + " 无可用点（坏值留在来源中）");

        int W = 920, H = 420, ML = 64, MR = 24, MT = 28, MB = 44;
        double tLo = tMin, tHi = tMax;
        double pad = (hMax - hMin) * 0.08 + 1e-6;
        double hLo = hMin - pad, hHi = hMax + pad;
        double sx = (W - ML - MR) / (tHi - tLo);
        double sy = (H - MT - MB) / (hHi - hLo);

        StringBuilder b = new StringBuilder();
        b.append(String.format(Locale.ROOT,
                "<svg xmlns='http://www.w3.org/2000/svg' width='%d' height='%d' "
                + "viewBox='0 0 %d %d' font-family='sans-serif' font-size='11'>", W, H, W, H));
        b.append("<rect width='100%' height='100%' fill='white'/>");

        // 网格 + 坐标
        for (int g = 0; g <= 5; g++) {
            double tk = tLo + (tHi - tLo) * g / 5;
            double x = ML + (tk - tLo) * sx;
            b.append(String.format(Locale.ROOT, "<line x1='%.1f' y1='%d' x2='%.1f' y2='%d' stroke='#eee'/>",
                    x, MT, x, H - MB));
            b.append(String.format(Locale.ROOT, "<text x='%.1f' y='%d' text-anchor='middle'>%.1f</text>",
                    x, H - MB + 16, tk - 273.15));
        }
        b.append(String.format(Locale.ROOT,
                "<text x='%d' y='%d' text-anchor='middle'>温度 (°C)</text>", W / 2, H - 8));
        for (int g = 0; g <= 4; g++) {
            double hk = hLo + (hHi - hLo) * g / 4;
            double y = H - MB - (hk - hLo) * sy;
            b.append(String.format(Locale.ROOT, "<line x1='%d' y1='%.1f' x2='%d' y2='%.1f' stroke='#f4f4f4'/>",
                    ML, y, W - MR, y));
            b.append(String.format(Locale.ROOT, "<text x='%d' y='%.1f' text-anchor='end'>%.3f</text>",
                    ML - 6, y + 3, hk));
        }
        b.append(String.format(Locale.ROOT,
                "<text transform='translate(16,%d) rotate(-90)' text-anchor='middle'>比热流 (W/g)</text>",
                H / 2));

        // 标题：方向/约定来自仪器头
        b.append(String.format(Locale.ROOT,
                "<text x='%d' y='18' font-weight='bold'>段 %d · %s · 约定 %s%s</text>",
                ML, seg.get("seqNo").asInt(),
                seg.get("direction").asText(), seg.get("convention").asText(),
                seg.get("nonUniformSpacing").asBoolean() ? " · ⚠非等间隔" : ""));

        // 曲线
        StringBuilder path = new StringBuilder();
        for (int i = 0; i < pts.size(); i++) {
            double x = ML + (pts.get(i)[0] - tLo) * sx;
            double y = H - MB - (pts.get(i)[1] - hLo) * sy;
            path.append(i == 0 ? "M" : "L").append(String.format(Locale.ROOT, "%.1f %.1f ", x, y));
        }
        b.append("<path d='").append(path).append("' fill='none' stroke='#1f5fa8' stroke-width='1.6'/>");

        // 并列基线候选
        String[] colors = {"#d62728", "#2ca02c", "#9467bd"};
        int ci = 0;
        for (JsonNode blNode : caseView.get("baselines")) {
            JsonNode bl = blNode.get("value");
            if (!seg.get("segmentId").asText().equals(bl.path("segmentId").asText())) continue;
            double slope = bl.get("slope").asDouble();
            double icept = bl.get("intercept").asDouble();
            boolean accepted = bl.path("accepted").asBoolean(false);
            String color = colors[ci % colors.length];
            double y1 = H - MB - (slope * tLo + icept - hLo) * sy;
            double y2 = H - MB - (slope * tHi + icept - hLo) * sy;
            b.append(String.format(Locale.ROOT,
                    "<line x1='%.1f' y1='%.1f' x2='%.1f' y2='%.1f' stroke='%s' stroke-width='%s' "
                    + "stroke-dasharray='%s' opacity='0.85' data-baseline='%s'/>",
                    (double) ML, y1, (double) W - MR, y2, color, accepted ? "2.4" : "1.2",
                    accepted ? "" : "5 4",
                    blNode.get("id").asText()));
            ci++;
        }

        // onset/peak/endset 标记
        for (JsonNode resNode : caseView.get("results")) {
            JsonNode res = resNode.get("value");
            if (!seg.get("segmentId").asText().equals(res.path("segmentId").asText())) continue;
            marker(b, res, "onsetK", tLo, sx, ML, "#ff7f0e", "O");
            marker(b, res, "peakK", tLo, sx, ML, "#d62728", "P");
            marker(b, res, "endsetK", tLo, sx, ML, "#1f77b4", "E");
        }

        b.append("</svg>");
        return b.toString();
    }

    private static void marker(StringBuilder b, JsonNode res, String key,
                               double tLo, double sx, int ML, String color, String label) {
        JsonNode v = res.get(key);
        if (v == null || v.isNull() || v.asDouble() == 0 && Double.isNaN(v.asDouble())) return;
        double tk = v.asDouble();
        if (Double.isNaN(tk)) return;
        double x = ML + (tk - tLo) * sx;
        b.append(String.format(java.util.Locale.ROOT,
                "<g class='marker' data-key='%s' data-result='%s'>", key, res.path("resultId").asText()));
        b.append(String.format(java.util.Locale.ROOT,
                "<line x1='%.1f' y1='28' x2='%.1f' y2='376' stroke='%s' stroke-dasharray='2 3' opacity='0.6'/>",
                x, x, color));
        b.append(String.format(java.util.Locale.ROOT,
                "<text x='%.1f' y='40' fill='%s' font-weight='bold'>%s</text>", x + 2, color, label));
        b.append("</g>");
    }

    private static String emptySvg(String msg) {
        return "<svg xmlns='http://www.w3.org/2000/svg' width='920' height='420'>"
                + "<rect width='100%' height='100%' fill='white'/>"
                + "<text x='460' y='210' text-anchor='middle' fill='#888'>" + msg + "</text></svg>";
    }
}
