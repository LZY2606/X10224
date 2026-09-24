#!/usr/bin/env python3
"""Deterministic generator for the DSC case-pack fixtures.

Regenerates src/main/resources/casepack/*.csv. Seeded; safe to re-run.
The curves are synthetic but domain-shaped: glass transition (Cp step),
crystallization (exotherm), melting (endotherm). Convention "endo_up"
means endothermic deflections point toward +y.
"""
import math, random, os

OUT = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources", "casepack")
os.makedirs(OUT, exist_ok=True)
rng = random.Random(20260919)

RATE = 10.0  # K/min

def gauss(t, mu, w):
    return math.exp(-0.5 * ((t - mu) / w) ** 2)

def sigmoid(x):
    return 1.0 / (1.0 + math.exp(-x))

def base_cp(t):
    return 0.5 + 0.002 * t

def heating_events(t, glass=70.0, crys=115.0, melt=150.0):
    # endo_up: glass step up, crystallization exotherm down, melting endotherm up
    return (0.30 * sigmoid((t - glass) / 2.0)
            - 1.50 * gauss(t, crys, 4.0)
            + 2.00 * gauss(t, melt, 3.0))

def cooling_events(t, crys=130.0, glass=65.0):
    return (0.28 * sigmoid((t - glass) / 2.2)
            - 1.20 * gauss(t, crys, 4.5))

def noise():
    return rng.uniform(-0.004, 0.004)

def write_file(name, header_lines, sections):
    """sections: list of (points) where points = list of (temp_C, heatflow_mW)."""
    path = os.path.join(OUT, name)
    with open(path, "w") as f:
        for h in header_lines:
            f.write(h + "\n")
        f.write("temp_C,time_s,heatflow_mW\n")
        t_abs = 0.0
        for si, pts in enumerate(sections):
            if si > 0:
                f.write("# segment_break\n")
            prev = None
            for (temp, hf) in pts:
                if prev is not None:
                    t_abs += abs(temp - prev) / RATE * 60.0
                prev = temp
                f.write(f"{temp:.3f},{t_abs:.2f},{hf:.4f}\n")
    print("wrote", path)

def sweep(t_lo, t_hi, step, fn):
    pts = []
    t = t_lo
    up = t_hi > t_lo
    while (t <= t_hi if up else t >= t_hi):
        pts.append((t, fn(t) + noise()))
        t += step if up else -step
    return pts

HDR = [
    "# instrument: TA-DSC-2500 sn:1147",
    "# convention: endo_up",
    "# reference_check: indium_melting_expected_endo",
    "# rate_K_per_min: 10.0",
    "# atmosphere: N2 50 mL/min",
    "# sample_pan: hermetic-al",
]

# 1. run1 heating: glass ~70C, crystallization ~115C (exo), melting ~150C (endo)
write_file("run1_heating.csv", HDR + ["# segment: HEATING 1"],
    [sweep(30.0, 180.0, 0.5, lambda t: base_cp(t) + heating_events(t))])

# 1b. run1 cooling: crystallization on cooling ~130C, glass ~65C
write_file("run1_cooling.csv", HDR + ["# segment: COOLING 1"],
    [sweep(180.0, 30.0, 0.5, lambda t: base_cp(t) + cooling_events(t))])

# 2. run2 heating with NON-UNIFORM temperature spacing
pts = []
t = 30.0
while t < 180.0:
    pts.append(t)
    t += rng.uniform(0.18, 1.35)
write_file("run2_heating_nonuniform.csv", HDR + ["# segment: HEATING 1"],
    [[(x, base_cp(x) + heating_events(x) + noise()) for x in pts]])

# 3. run3: header still declares endo_up, but the heat-flow signal is wired
#    in reverse (melting endotherm points DOWN) -> direction conflict that
#    must be caught from the reference_check, never from the file name.
write_file("run3_heating_reversed_signal.csv",
    [h for h in HDR] + ["# segment: HEATING 1"],
    [sweep(30.0, 180.0, 0.5, lambda t: base_cp(t) - heating_events(t))])

# 4. run4: two overlapping endotherms (~142C and ~150C)
def overlap(t):
    return (base_cp(t) + 0.30 * sigmoid((t - 70.0) / 2.0)
            + 1.50 * gauss(t, 142.0, 3.0) + 1.80 * gauss(t, 150.0, 3.5))
write_file("run4_heating_overlapping.csv", HDR + ["# segment: HEATING 1"],
    [sweep(30.0, 180.0, 0.5, lambda t: overlap(t) + noise())])

# 5. run5: cycle switching HEAT1 / COOL1 / HEAT2 in a single file
write_file("run5_cycles.csv", HDR + ["# segment: HEATING 1"], [
    sweep(30.0, 180.0, 1.0, lambda t: base_cp(t) + heating_events(t) + noise()),
    sweep(180.0, 30.0, 1.0, lambda t: base_cp(t) + cooling_events(t) + noise()),
    sweep(30.0, 180.0, 1.0, lambda t: base_cp(t) + heating_events(t, glass=71.0) + noise()),
])
