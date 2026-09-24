#!/usr/bin/env python3
"""Generate deterministic offline DSC case fixtures (repo-only data, no external sources).

Produces the "热转折案：DSC 基线、相变积分与扫描历史审阅器" case pack:
  fixtures/casepack/raw/*.csv  — instrument outputs (raw representation preserved)
The scenarios exercised by the replay tests/UI:
  reverse heat flow, non-uniform temperature, overlapping transitions,
  cycle segment switching, mass version change, parallel baselines, empty interval.
"""
import math
import os
import random

RAW = os.path.join(os.path.dirname(__file__), "..", "fixtures", "casepack", "raw")
os.makedirs(RAW, exist_ok=True)


def gaussian(t, mu, amp, width):
    return amp * math.exp(-0.5 * ((t - mu) / width) ** 2)


def write_csv(path, instrument, direction, sign, points, t_unit="°C", hf_unit="mW",
              segments=None, bad_rows=()):
    """points: list of (temp, hf) in physical °C / instrument-native sign convention."""
    lines = [
        "# instrument: " + instrument,
        "# direction: " + direction,
        "# sign-convention: " + sign,
        "# temperature-unit: " + t_unit,
        "# heatflow-unit: " + hf_unit,
        "Index,Segment,Temperature,HeatFlow",
    ]
    for i, (t, h) in enumerate(points):
        seg = segments[i] if segments else "S1"
        if i in bad_rows:
            t_txt, h_txt = "NA", "--"
        else:
            t_txt = f"{t:.4f}"
            h_txt = f"{h:.5f}"
        lines.append(f"{i},{seg},{t_txt},{h_txt}")
    with open(path, "w", encoding="utf-8") as f:
        f.write("\n".join(lines) + "\n")


def sample(t0, t1, step, jitter=0.0, seed=1, drop=()):
    """Temperature grid; jitter creates non-uniform sampling; drop removes indices."""
    rng = random.Random(seed)
    out = []
    t = t0
    i = 0
    direction = 1.0 if t1 >= t0 else -1.0
    def reached(t, t1, direction):
        return t * direction <= t1 * direction + 1e-9
    while reached(t, t1, direction):
        if i not in drop:
            tt = t + (rng.uniform(-jitter, jitter) if jitter else 0.0)
            out.append((i, round(tt, 4)))
        t += direction * step
        i += 1
    return [(i, tt) for i, tt in out]


def heat_curve(temps, *, peak_mu=248.0, peak_amp=-9.0, width=7.0, base=-0.55, noise=0.06, seed=2):
    """Heating melt: endotherm under exo-up => negative dip."""
    rng = random.Random(seed)
    return [(t, base * (t - 40) / 200.0
             + gaussian(t, peak_mu, peak_amp, width)
             + rng.gauss(0, noise)) for t in temps]


# ---------------------------------------------------------------- main PET case
def main_case():
    # Heat1: single endothermic melt ~248°C
    grid1 = sample(40, 280, 0.4, seed=11)
    temps1 = [t for _, t in grid1]
    pts1 = heat_curve(temps1, seed=21)

    # Cool1: exothermic crystallization ~190°C (exo-up => positive), uniform grid
    temps2 = [t for _, t in sample(280, 60, 0.4, seed=12)]
    rng = random.Random(22)
    pts2 = [(t, 0.18 * (t - 280) / 220.0
             + gaussian(t, 190, 11.0, 8.0)
             + rng.gauss(0, 0.06)) for t in temps2]

    # Heat2: glass step ~82°C + cold-crystallization exo ~125°C + melt endo ~247°C
    temps3 = [t for _, t in sample(30, 280, 0.4, seed=13)]
    rng = random.Random(23)
    pts3 = []
    for t in temps3:
        step = 1.6 / (1 + math.exp(-(t - 82) / 2.5))  # glass-like Cp step
        h = (-0.4 * (t - 30) / 250.0
             + step
             + gaussian(t, 125, 6.0, 5.5)
             + gaussian(t, 247, -10.0, 7.0)
             + rng.gauss(0, 0.06))
        pts3.append(h)

    n1, n2 = len(pts1), len(pts2)
    segs = ["Heat1"] * n1 + ["Cool1"] * n2 + ["Heat2"] * len(pts3)
    temps = temps1 + temps2 + temps3
    hfs = [h for _, h in pts1] + [h for _, h in pts2] + pts3
    points = list(zip(temps, hfs))
    write_csv(os.path.join(RAW, "pet_sample_history.csv"),
              "DSC-Q2000(virtual)", "heating", "exo-up",
              points, segments=segs)


# ---------------------------------------------------------------- edge cases
def reverse_heatflow():
    """Same physical endotherm, but instrument declares exo-down: peak flips sign.
    Direction/sign must come from metadata, never the file name."""
    temps = [t for _, t in sample(40, 280, 0.4, seed=31)]
    rng = random.Random(32)
    pts = [(t, -0.5 * (t - 40) / 200.0
            + gaussian(t, 245, 9.0, 7.0)  # positive under exo-down == endothermic
            + rng.gauss(0, 0.06)) for t in temps]
    write_csv(os.path.join(RAW, "edge_reverse_heatflow.csv"),
              "DSC-Q2000(virtual)", "heating", "exo-down", pts)


def non_uniform():
    """Jittered temperature grid: non-uniform steps must be detected and integrated
    with the non-uniform trapezoid rule."""
    grid = sample(40, 280, 0.5, jitter=0.22, seed=41)
    temps = [t for _, t in grid]
    pts = heat_curve(temps, peak_mu=250, peak_amp=-8.5, seed=42)
    write_csv(os.path.join(RAW, "edge_non_uniform_temperature.csv"),
              "DSC-Q2000(virtual)", "heating", "exo-up", pts)


def overlapping():
    """Two partially overlapping endotherms: both transitions kept, no single answer."""
    temps = [t for _, t in sample(40, 280, 0.4, seed=51)]
    rng = random.Random(52)
    pts = [(t, -0.3 * (t - 40) / 200.0
            + gaussian(t, 238, -7.0, 6.0)
            + gaussian(t, 251, -8.5, 7.5)
            + rng.gauss(0, 0.06)) for t in temps]
    write_csv(os.path.join(RAW, "edge_overlapping_transitions.csv"),
              "DSC-Q2000(virtual)", "heating", "exo-up", pts)


def cycle_switch():
    """Heat1 -> Cool1 -> Heat2 in one file, plus an isothermal-style program label."""
    tempsA = [t for _, t in sample(50, 200, 0.6, seed=61)]
    rng = random.Random(62)
    a = [(t, -0.1 * (t - 50) / 150.0 + gaussian(t, 150, -6.0, 6.0)
          + rng.gauss(0, 0.07)) for t in tempsA]
    tempsB = [t for _, t in sample(200, 50, 0.6, seed=63)]
    rng = random.Random(64)
    b = [(t, 0.1 * (t - 200) / 150.0 + gaussian(t, 120, 7.0, 6.0)
          + rng.gauss(0, 0.07)) for t in tempsB]
    tempsC = [t for _, t in sample(50, 200, 0.6, seed=65)]
    rng = random.Random(66)
    c = [(t, -0.1 * (t - 50) / 150.0 + gaussian(t, 152, -6.5, 6.0)
          + rng.gauss(0, 0.07)) for t in tempsC]
    segs = ["Heat1"] * len(a) + ["Cool1"] * len(b) + ["Heat2"] * len(c)
    temps = tempsA + tempsB + tempsC
    hfs = [h for _, h in a] + [h for _, h in b] + [h for _, h in c]
    write_csv(os.path.join(RAW, "edge_cycle_switch.csv"),
              "DSC-Q2000(virtual)", "heating", "exo-up",
              list(zip(temps, hfs)), segments=segs)


def empty_interval():
    """A heating trace with no transition inside the reviewer-selected window:
    fitting must refuse cleanly rather than invent a baseline."""
    temps = [t for _, t in sample(100, 140, 0.4, seed=71)]
    rng = random.Random(72)
    pts = [(t, 0.0008 * (t - 100) + rng.gauss(0, 0.04)) for t in temps]
    write_csv(os.path.join(RAW, "edge_empty_interval.csv"),
              "DSC-Q2000(virtual)", "heating", "exo-up", pts)


def bad_rows():
    """Non-convertible points stay in place with diagnostics, never dropped for plotting."""
    grid = sample(40, 280, 0.5, seed=81)
    temps = [t for _, t in grid]
    pts = heat_curve(temps, peak_mu=246, peak_amp=-8.0, seed=82)
    by_index = {idx: (t, h) for (idx, t), (_, h) in zip(grid, pts)}
    lines = ["# instrument: DSC-Q2000(virtual)", "# direction: heating",
             "# sign-convention: exo-up", "# temperature-unit: °C",
             "# heatflow-unit: mW", "Index,Segment,Temperature,HeatFlow"]
    for i in range(0, 481):
        if i in (30, 31) or i not in by_index:
            lines.append(f"{i},S1,NA,--")
        else:
            t, h = by_index[i]
            lines.append(f"{i},S1,{t:.4f},{h:.5f}")
    with open(os.path.join(RAW, "edge_unconvertible_rows.csv"), "w", encoding="utf-8") as f:
        f.write("\n".join(lines) + "\n")


if __name__ == "__main__":
    main_case()
    reverse_heatflow()
    non_uniform()
    overlapping()
    cycle_switch()
    empty_interval()
    bad_rows()
    for name in sorted(os.listdir(RAW)):
        print(name)
