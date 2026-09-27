#!/usr/bin/env python3
"""Deterministic in-repo DSC fixture generator.

Produces synthetic instrument-output CSVs for the case pack
"热转折案：DSC 基线、相变积分与扫描历史审阅器".

Convention (instrument declaration, never guessed from filename):
  exo_up   : positive heat flow = exotherm
  endo_up  : positive heat flow = endotherm

Temperature in degC (ITS-90 basis carried in manifest), heat flow in mW.
"""
import math
import os
import random

OUT = os.path.join(os.path.dirname(__file__), "..", "casepack", "curves")


def grid(t0, t1, n, jitter_seed=None):
    if jitter_seed is None:
        return [t0 + (t1 - t0) * i / (n - 1) for i in range(n)]
    rng = random.Random(jitter_seed)
    span = (t1 - t0) / (n - 1)
    ts = [t0]
    for i in range(1, n - 1):
        ts.append(t0 + span * i + rng.uniform(-0.45 * span, 0.45 * span))
    ts.append(t1)
    return ts


def gauss(x, amp, mu, w):
    return amp * math.exp(-0.5 * ((x - mu) / w) ** 2)


def tanh_step(x, amp, mu, w):
    return 0.5 * amp * (1.0 + math.tanh((x - mu) / w))


def rng_noise(seed):
    rng = random.Random(seed)
    return lambda: rng.gauss(0.0, 0.0025)


def write_csv(path, temps, hfs, extra_header=None):
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        f.write("# instrument: DSC-9000 thermal-trace rev4\n")
        if extra_header:
            for line in extra_header:
                f.write("# " + line + "\n")
        f.write("temperature_C,heat_flow_mW\n")
        for t, h in zip(temps, hfs):
            f.write(f"{t:.4f},{h:.5f}\n")


def heat1():
    """First heating 10 degC/min: Tg step ~78C, cold crystallization ~142C,
    overlapping melt with shoulder ~220/228C."""
    ts = grid(30.0, 280.0, 1001)
    noise = rng_noise(11)
    hfs = []
    for t in ts:
        h = 0.35 - 0.0012 * t
        h += tanh_step(t, 0.10, 78.0, 1.6)                      # Tg Cp step (endothermic, +)
        h -= gauss(t, 1.15, 142.0, 5.0)                          # cold crystallization (exo, -)
        h += gauss(t, 2.40, 220.0, 4.2)                          # main melt (endo, +)
        h += gauss(t, 0.95, 228.0, 3.0)                          # overlapping shoulder
        h += noise()
        hfs.append(h)
    write_csv(os.path.join(OUT, "heating_run1.csv"), ts, hfs,
              ["sequence_role: HEAT", "sign_convention: endo_up",
               "filename_note: deliberately uninformative name for name-guess tests"])


def cool1():
    """Cooling 8 degC/min: crystallization exotherm ~168C; exo_up convention
    so exotherm appears positive (opposite sign policy to heating_run1)."""
    ts = grid(280.0, 40.0, 961)  # monotonically decreasing temperature
    noise = rng_noise(22)
    hfs = []
    for t in ts:
        h = -0.20 + 0.0008 * t
        h += gauss(t, 1.85, 168.0, 5.5)                          # crystallization (exo, +)
        h += noise()
        hfs.append(h)
    write_csv(os.path.join(OUT, "curve_log_9921.csv"), ts, hfs,
              ["sequence_role: COOL", "sign_convention: exo_up",
               "filename_note: name says nothing about direction or sign"])


def heat2_nonuniform():
    """Second heating 5 degC/min on a NON-EQUISPACED temperature grid:
    dense sampling around the melt region, sparse elsewhere, one data gap."""
    seg_a = grid(30.0, 190.0, 200)
    seg_b = grid(190.01, 250.0, 600)
    seg_c = grid(256.0, 280.0, 80)                            # gap 250.0 -> 256.0
    ts = seg_a + seg_b + seg_c
    noise = rng_noise(33)
    hfs = []
    for t in ts:
        h = 0.30 - 0.0010 * t
        h += tanh_step(t, 0.09, 79.0, 1.7)
        h += gauss(t, 2.10, 221.5, 4.6)                        # melt
        h += noise()
        hfs.append(h)
    write_csv(os.path.join(OUT, "heating_run2_nonuniform.csv"), ts, hfs,
              ["sequence_role: HEAT", "sign_convention: endo_up",
               "grid: nonuniform; gap 250.0C..256.0C"])


if __name__ == "__main__":
    os.makedirs(OUT, exist_ok=True)
    heat1()
    cool1()
    heat2_nonuniform()
    for name in sorted(os.listdir(OUT)):
        p = os.path.join(OUT, name)
        print(name, sum(1 for _ in open(p, encoding="utf-8")), "lines")
