package org.dsc.engine;

import org.dsc.domain.DscParser;
import org.dsc.domain.Models.*;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class EngineSemanticsTest {

    private Segment syntheticHeating(HeatFlowConvention conv, boolean nonUniform) {
        java.util.List<Point> pts = new java.util.ArrayList<>();
        int i = 0;
        for (double T = 300; T <= 520; T += (nonUniform && T > 400 ? 3.0 : 1.0)) {
            double time = i * 6.0;
            // 430K 一个向下(屏幕)峰，500K 一个向上(屏幕)峰；语义由 convention 决定
            double hf = 0.10 - gauss(T, 430, 4, 0.4) + gauss(T, 500, 3, 0.5);
            pts.add(new Point(i, time, T, hf));
            i++;
        }
        return new Segment("seg", 1, ScanDirection.HEATING, conv,
                new ScanRate(10, "10 K/min", "K/min"), pts, nonUniform, List.of());
    }

    private static double gauss(double x, double mu, double sig, double a) {
        return a * Math.exp(-0.5 * ((x - mu) / sig) * ((x - mu) / sig));
    }

    @Test
    void multiplePlausibleBaselinesAreAllRetained() {
        Segment seg = syntheticHeating(HeatFlowConvention.EXO_UP, false);
        TangentSelection tan = new TangentSelection("seg",
                new TempWindow(300, 340), new TempWindow(510, 519), "t", 1);
        List<Baselines.Fit> fits = Baselines.candidates(seg, tan);
        assertTrue(fits.size() >= 2, "tangent/OLS/constant candidates retained");
        double firstRmse = fits.get(0).rmse;
        // 不同候选有真实差异
        boolean differs = fits.stream().anyMatch(f -> Math.abs(f.intercept - fits.get(0).intercept) > 1e-9
                || Math.abs(f.slope - fits.get(0).slope) > 1e-9);
        assertTrue(differs);
        assertTrue(firstRmse >= 0);
    }

    @Test
    void endoExoDirectionFollowsConventionNotFileName() {
        Segment exoUp = syntheticHeating(HeatFlowConvention.EXO_UP, false);
        Segment endoUp = syntheticHeating(HeatFlowConvention.ENDO_UP, false);
        Baselines.Fit flat = new Baselines.Fit(0, 0.10, 1e-6, 10);
        // 500K 附近是“向上的峰”：EXO_UP 仪器下向上=放热(结晶)；ENDO_UP 下向上=吸热(熔融)
        var a1 = Transitions.analyze(exoUp, new TempWindow(488, 512), flat);
        var a2 = Transitions.analyze(endoUp, new TempWindow(488, 512), flat);
        assertEquals(TransitionKind.CRYSTALLIZATION, a1.kind);
        assertEquals(TransitionKind.MELTING, a2.kind);
    }

    @Test
    void enthalpySignIsEndothermPositiveRegardlessOfConvention() {
        Segment exoUp = syntheticHeating(HeatFlowConvention.EXO_UP, false);
        Segment endoUp = syntheticHeating(HeatFlowConvention.ENDO_UP, false);
        Baselines.Fit flat = new Baselines.Fit(0, 0.10, 1e-6, 10);
        var exoPeak = Transitions.analyze(exoUp, new TempWindow(418, 442), flat);
        var endoPeak = Transitions.analyze(endoUp, new TempWindow(488, 512), flat);
        assertTrue(exoPeak.enthalpyJg < 0, "exotherm integrates negative");
        assertTrue(endoPeak.enthalpyJg > 0, "endotherm integrates positive");
    }

    @Test
    void integrationUsesRealTimeForNonUniformTemperatures() {
        Segment uniform = syntheticHeating(HeatFlowConvention.ENDO_UP, false);
        Segment nonUniform = syntheticHeating(HeatFlowConvention.ENDO_UP, true);
        Baselines.Fit flat = new Baselines.Fit(0, 0.10, 1e-6, 10);
        var au = Transitions.analyze(uniform, new TempWindow(488, 512), flat);
        var an = Transitions.analyze(nonUniform, new TempWindow(488, 512), flat);
        assertTrue(an.errorSources.stream().anyMatch(e -> e.contains("NON_UNIFORM_SPACING")));
        // 非等间隔仍给出有限焓变（按真实时间梯形积分），而不是崩溃或假设等 dt
        assertTrue(Double.isFinite(an.enthalpyJg));
        assertNotEquals(au.enthalpyJg, an.enthalpyJg, 1e-12);
    }

    @Test
    void emptyWindowIsDiagnosedNotFabricated() {
        Segment seg = syntheticHeating(HeatFlowConvention.EXO_UP, false);
        Baselines.Fit flat = new Baselines.Fit(0, 0.10, 1e-6, 10);
        var a = Transitions.analyze(seg, new TempWindow(600, 610), flat);
        assertTrue(a.diagnostics.get(0).contains("empty window"));
        assertTrue(a.errorSources.contains("EMPTY_WINDOW"));
        assertTrue(Double.isNaN(a.enthalpyJg));
    }

    @Test
    void heatingAndCoolingWindowsCannotMix() {
        Segment h = syntheticHeating(HeatFlowConvention.EXO_UP, false);
        String coolBlock = """
            SEGMENT seq=2 direction=cooling convention=exo-up rate="-10 K/min" atmosphere="N2"
            """;
        // 构造一个紧邻温度区间不同的冷却段
        java.util.List<Point> pts = new java.util.ArrayList<>();
        int idx = 0;
        for (double T = 520; T >= 300; T -= 2)
            pts.add(new Point(idx++, idx * 7.0, T, 0.12));
        Segment c = new Segment("c", 2, ScanDirection.COOLING, HeatFlowConvention.EXO_UP,
                new ScanRate(10, "-10 K/min", "K/min"), pts, false, List.of());
        // 300-510 同时落在两段覆盖范围 -> crossesSegmentBoundary 为 true
        TempWindow wide = new TempWindow(300, 510);
        assertTrue(Transitions.crossesSegmentBoundary(List.of(h, c), wide));
        assertFalse(Transitions.crossesSegmentBoundary(List.of(h), new TempWindow(400, 410)));
    }

    @Test
    void coolingEndothermStaysUnclassifiedRatherThanGuessed() {
        java.util.List<Point> pts = new java.util.ArrayList<>();
        int idx = 0;
        for (double T = 520; T >= 300; T -= 1) {
            // ENDO_UP 仪器，冷却段出现一个向上（吸热）峰
            double hf = 0.1 + gauss(T, 450, 4, 0.4);
            pts.add(new Point(idx++, idx * 6.0, T, hf));
        }
        Segment c = new Segment("c", 2, ScanDirection.COOLING, HeatFlowConvention.ENDO_UP,
                new ScanRate(10, "-10 K/min", "K/min"), pts, false, List.of());
        Baselines.Fit flat = new Baselines.Fit(0, 0.10, 1e-6, 10);
        var a = Transitions.analyze(c, new TempWindow(438, 462), flat);
        assertEquals(TransitionKind.UNCLASSIFIED, a.kind);
        assertTrue(a.errorSources.stream().anyMatch(e -> e.contains("UNEXPECTED_ENDOTHERM_ON_COOLING")));
    }

    @Test
    void fixtureParsesFromRepositoryOnly() {
        Segment seg = DscParser.parse("seg-h1", 1, Replay.resource("segment_heating_1.txt")).segment;
        assertTrue(seg.nonUniformSpacing());
        assertTrue(seg.points().size() > 100);
        assertEquals(ScanDirection.HEATING, seg.direction());
    }
}
