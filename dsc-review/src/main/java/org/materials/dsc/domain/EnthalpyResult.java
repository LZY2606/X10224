package org.materials.dsc.domain;

import java.util.List;

/**
 * 一次积分结果（t 域梯形积分，因此非等间隔温度天然正确）。
 * ΔH(J/g) = ∫(h - baseline) dt（mW·s = mJ）/ 质量(g) / 1000。
 */
public record EnthalpyResult(
        double deltaHJoulePerGram,
        String kind,
        double uncertaintyJoulePerGram,
        List<ErrorSource> errorSources,
        String diagnostic) {

    public boolean isEmpty() {
        return diagnostic != null;
    }
}
