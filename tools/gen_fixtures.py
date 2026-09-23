#!/usr/bin/env python3
"""生成「热转折案」离线案例包 fixture（仓库内自包含，测试与演示共用）。"""
import math, os

OUT = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources", "casepack")
os.makedirs(OUT, exist_ok=True)

MASS_G = 0.00520          # 5.20 mg
RATE_KS = 10.0 / 60.0     # 10 K/min
MW_PER_WG = MASS_G * 1000 # mW per (W/g)

def gauss_area(dH, center, sigma):
    """给定焓变(J/g)的高斯峰 -> Φ(T) 贡献 (W/g)，对 T 积分再除以速率得 dH。"""
    return lambda T: dH * RATE_KS * math.exp(-0.5 * ((T - center) / sigma) ** 2) / (sigma * math.sqrt(2 * math.pi))

def glass_step(T, lo=60.0, hi=80.0, height=0.05):
    mid, w = (lo + hi) / 2, (hi - lo) / 6
    return height / (1.0 + math.exp(-(T - mid) / w))

def noise(t):
    return 0.0015 * math.sin(t * 7.3) + 0.0010 * math.sin(t * 13.1)

def nonuniform_temps(t0, t1, direction=1):
    """非等间隔温度序列。"""
    temps, t = [], t0
    steps = [0.4, 0.7, 0.5, 0.9, 0.3, 0.6]
    i = 0
    while (direction > 0 and t < t1) or (direction < 0 and t > t1):
        temps.append(t)
        t += direction * steps[i % len(steps)]
        i += 1
    temps.append(t1)
    return temps

def write_csv(name, convention, temps, phi, t_offset=0.0):
    """phi: T -> W/g（endo 为正）。按 convention 决定输出符号。"""
    sign = 1.0 if convention == "endo_up" else -1.0
    lines = [
        "#Instrument: DSC-2500",
        f"#Convention: {convention}",
        "#HeatFlowUnit: mW",
        "#TempUnit: °C",
        "#TimeUnit: s",
        "#ScanRate: 10 K/min",
        "time_s,temp_C,heatflow_mW",
    ]
    for i, T in enumerate(temps):
        t = t_offset + i * 0.6 + (0.13 if i % 3 == 0 else 0.0)  # 非等间隔时间
        hf = phi(T) * MW_PER_WG * sign + noise(t)
        lines.append(f"{t:.3f},{T:.3f},{hf:.6f}")
    with open(os.path.join(OUT, name), "w") as f:
        f.write("\n".join(lines) + "\n")
    print(name, len(temps), "points")

# run1 升温：玻璃化 + 结晶放热 + 熔融双峰（重叠转折）
crys = gauss_area(-35.0, 120.0, 4.0)
melt1 = gauss_area(40.0, 180.0, 3.0)
melt2 = gauss_area(8.0, 172.0, 1.5)
write_csv("run1_heating.csv", "exo_up", nonuniform_temps(20.0, 250.0),
          lambda T: glass_step(T) + crys(T) + melt1(T) + melt2(T))

# run1 降温：结晶放热（反向热流：文件名像放热方向，但头部声明 endo_up）
crys_cool = gauss_area(-30.0, 140.0, 5.0)
write_csv("run1_cooling.csv", "endo_up", nonuniform_temps(250.0, 20.0, direction=-1),
          lambda T: glass_step(T) + crys_cool(T))

# run2 升温（第二循环）：玻璃化 + 单熔融峰
melt_r2 = gauss_area(38.0, 180.0, 3.0)
write_csv("run2_heating.csv", "exo_up", nonuniform_temps(20.0, 250.0),
          lambda T: glass_step(T) + melt_r2(T))

# run3：无法换算的单位（arb），原始值保留 + 诊断
write_csv("run3_unknown_units.csv", "exo_up", nonuniform_temps(20.0, 60.0),
          lambda T: glass_step(T))
# 改写头部单位
p = os.path.join(OUT, "run3_unknown_units.csv")
s = open(p).read().replace("#HeatFlowUnit: mW", "#HeatFlowUnit: arb")
open(p, "w").write(s)

# 文件名暗示 exo，但头部声明 endo_up：方向只能认头部
peak = gauss_area(-12.0, 100.0, 4.0)
write_csv("misleading_name.csv", "endo_up", nonuniform_temps(20.0, 200.0),
          lambda T: 0.01 + peak(T))

# 完整循环：同一文件内升温+降温（循环段切换）
full_temps = nonuniform_temps(20.0, 250.0) + nonuniform_temps(250.0, 20.0, direction=-1)[1:]
write_csv("cycle_full.csv", "exo_up", full_temps,
          lambda T: glass_step(T) + crys(T) + melt1(T) + melt2(T) + crys_cool(T))

# 无 Convention 头部：方向不可猜测 -> UNKNOWN + 诊断
lines = ["#Instrument: DSC-2500", "#HeatFlowUnit: mW", "#TempUnit: °C", "time_s,temp_C,heatflow_mW"]
for i, T in enumerate(nonuniform_temps(20.0, 100.0)):
    lines.append(f"{i*0.6:.3f},{T:.3f},{0.5 * MW_PER_WG:.6f}")
open(os.path.join(OUT, "no_convention.csv"), "w").write("\n".join(lines) + "\n")
print("no_convention.csv")

# 合成台阶：T<100 为 0，T>=100 为 0.5 W/g，非等间隔
write_csv("synthetic_step.csv", "exo_up", nonuniform_temps(50.0, 160.0),
          lambda T: 0.5 if T >= 100.0 else 0.0)
