#!/usr/bin/env python3
"""Generate the offline DSC case-pack fixtures (deterministic, seeded)."""
import math, os, random

OUT = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources", "fixtures")
os.makedirs(OUT, exist_ok=True)
rng = random.Random(20260919)

def noise():
    return rng.uniform(-0.005, 0.005)

def base_hf(t):
    # heat-capacity slope baseline in mW (endo_up convention)
    return 0.02 * (t - 25.0) / 100.0

def gauss(t, t0, amp, w):
    return amp * math.exp(-((t - t0) ** 2) / (2 * w * w))

def step(t, t0, h, w):
    return h / (1.0 + math.exp(-(t - t0) / w))

def write(path, header_lines, rows):
    with open(os.path.join(OUT, path), "w") as f:
        for h in header_lines:
            f.write(h + "\n")
        f.write("Temp_C,HeatFlow_mW,Time_s\n")
        for r in rows:
            f.write(r + "\n")

HDR = [
    "# instrument: SIM-DSC-250 (offline case pack)",
    "# sample: ALLOY-X batch 7",
    "# mass_mg: 8.42",
    "# scan_rate_K_per_min: 10",
    "# atmosphere: N2 50 mL/min",
    "# calibration_id: CAL-2026-03",
]

def grid(t0, t1, dt):
    ts, t = [], t0
    while t <= t1 + 1e-9:
        ts.append(round(t, 3)); t += dt
    return ts

def time_s(t, t0, rate=10.0):
    return (t - t0) / rate * 60.0

# runA: heating, glass transition + cold crystallisation (exo) + melting (endo), uniform grid
rows = []
for t in grid(30.0, 250.0, 0.5):
    y = base_hf(t) + step(t, 75.0, 0.15, 2.0) - gauss(t, 135.0, 1.2, 6.0) + gauss(t, 225.0, 2.0, 5.0) + noise()
    rows.append(f"{t:.3f},{y:.5f},{time_s(t,30.0):.2f}")
write("runA_heat.csv", HDR + ["# sign_convention: endo_up", "# segment: HEAT 1 of 1"], rows)

# runB: cooling, crystallisation exotherm near 180 C
rows = []
for t in grid(30.0, 250.0, 0.5):
    y = base_hf(t) - gauss(t, 180.0, 1.5, 5.0) + noise()
    rows.append(f"{t:.3f},{y:.5f},{time_s(t,30.0):.2f}")
rows.reverse()
write("runB_cool.csv", HDR + ["# sign_convention: endo_up", "# segment: COOL 1 of 1"], rows)

# runC: filename claims cooling but data heats; reversed heat flow (exo_up) -> endo melting dips negative
rows = []
for t in grid(30.0, 250.0, 0.5):
    y = -base_hf(t) - gauss(t, 160.0, 1.8, 5.0) + noise()   # exo_up: endotherm plotted negative
    rows.append(f"{t:.3f},{y:.5f},{time_s(t,30.0):.2f}")
write("runC_misnamed_cooling.csv",
      [h for h in HDR] + ["# sign_convention: exo_up", "# segment: HEAT 1 of 1"], rows)

# runD: non-uniform temperature grid + one unconvertible OVERLOAD cell
rows = []
t = 40.0
i = 0
while t <= 200.0 + 1e-9:
    y = base_hf(t) + gauss(t, 150.0, 1.6, 5.0) + noise()
    cell = "OVERLOAD" if abs(t - 150.0) < 0.01 else f"{y:.5f}"
    rows.append(f"{t:.3f},{cell},{time_s(t,40.0):.2f}")
    t += 0.5 if i % 2 == 0 else 1.5
    i += 1
write("runD_nonuniform.csv", HDR + ["# sign_convention: endo_up", "# segment: HEAT 1 of 1"], rows)

# runE: overlapping transitions (two endo peaks that never return to baseline)
rows = []
for t in grid(100.0, 190.0, 0.5):
    y = base_hf(t) + gauss(t, 140.0, 1.5, 5.0) + gauss(t, 152.0, 1.2, 5.0) + noise()
    rows.append(f"{t:.3f},{y:.5f},{time_s(t,100.0):.2f}")
write("runE_overlap.csv", HDR + ["# sign_convention: endo_up", "# segment: HEAT 1 of 1"], rows)

# runF: heat/cool/heat cycles in one file (segment switching)
def cycle_rows(t0, t1, dt, peak_t, amp):
    out = []
    ts = grid(min(t0, t1), max(t0, t1), dt)
    if t1 < t0:
        ts.reverse()
    for t in ts:
        y = base_hf(t) + gauss(t, peak_t, amp, 5.0) + noise()
        out.append(f"{t:.3f},{y:.5f},{time_s(abs(t - t0), 0.0):.2f}")
    return out

with open(os.path.join(OUT, "runF_cycles.csv"), "w") as f:
    for h in HDR + ["# sign_convention: endo_up"]:
        f.write(h + "\n")
    f.write("# segment: HEAT 1 of 3\nTemp_C,HeatFlow_mW,Time_s\n")
    f.write("\n".join(cycle_rows(30.0, 200.0, 0.5, 170.0, 1.4)) + "\n")
    f.write("# segment: COOL 2 of 3\nTemp_C,HeatFlow_mW,Time_s\n")
    f.write("\n".join(cycle_rows(200.0, 60.0, 0.5, 150.0, -1.1)) + "\n")
    f.write("# segment: HEAT 3 of 3\nTemp_C,HeatFlow_mW,Time_s\n")
    f.write("\n".join(cycle_rows(60.0, 200.0, 0.5, 170.0, 1.3)) + "\n")

print("fixtures written to", OUT)
