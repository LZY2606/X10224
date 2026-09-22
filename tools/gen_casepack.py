#!/usr/bin/env python3
"""
热转折案离线案例包曲线生成器（仅用于生成仓库内 fixture；测试只用仓库内 fixture，不联网）。

合成 DSC 段：线性基线 + 高斯峰（时间温度域）+ 玻璃化台阶。
内部约定吸热向上：熔融为正高斯，结晶为负高斯。
同时输出真值 JSON（峰温、焓 J/g、质量、速率），测试用非等间隔时间梯形积分核对。
"""
import json
import math
import os

ROOT = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources", "casepack")
os.makedirs(ROOT, exist_ok=True)

R = 8.314462618

def gaussian_minutes(T, Tp, area_Jg, width_K, rate_K_min, sign):
    # area = ∫ f dt [J/g]；f(T) = sign*H * (rate/(w*sqrt(2pi))) exp(-(T-Tp)^2/(2w^2))
    amp = area_Jg * rate_K_min / (width_K * math.sqrt(2 * math.pi)) * 60.0
    return sign * amp * math.exp(-((T - Tp) ** 2) / (2 * width_K ** 2)) / 60.0 * 60.0 / 60.0 * 60.0

def heatflow_Wg(T, Tp, area_Jg, width_K, rate_K_min, sign):
    # f in W/g so that ∫ f dT/rate*60 = area
    amp = area_Jg * rate_K_min / (width_K * math.sqrt(2 * math.pi)) / 60.0
    return sign * amp * math.exp(-((T - Tp) ** 2) / (2 * width_K ** 2))

def glass_step(T, Tg, height, width_K):
    z = (T - Tg) / width_K
    return height / (1 + math.exp(-z))

def baseline(T, slope_WgK, intercept):
    return slope_WgK * (T - 300.0) + intercept

def write_segment(name, temps, values, unit_t, unit_y, direction, rate_raw, extra_header=""):
    path = os.path.join(ROOT, name)
    with open(path, "w", encoding="utf-8") as f:
        f.write("# Thermal Transition Case Pack - offline DSC fixture\n")
        f.write(f"# instrument_model: DSC-SYNTH-9000\n")
        f.write(f"# heat_flow_direction: {direction}\n")
        f.write(f"# temperature_unit: {unit_t}\n")
        f.write(f"# heat_flow_unit: {unit_y}\n")
        f.write(f"# scan_rate: {rate_raw}\n")
        f.write(extra_header)
        f.write("temperature,heat_flow\n")
        for t, y in zip(temps, values):
            f.write(f"{t:.4f},{y:.6f}\n")
    return path

def uniform(T0, T1, step):
    n = int(round((T1 - T0) / step)) + 1
    return [T0 + i * step for i in range(n)]

def nonuniform(T0, T1):
    # 非等间隔：0.5K 网格中随机但确定性地抽稀/加密（PRNG 固定种子）
    import random
    rng = random.Random(20260923)
    ts = [T0]
    t = T0
    while t < T1:
        t += rng.choice([0.2, 0.25, 0.5, 0.8, 1.2, 2.0])
        ts.append(min(t, T1))
    if abs(ts[-1] - T1) < 1e-9:
        ts[-1] = T1
    else:
        ts.append(T1)
    out = []
    for t in ts:
        if not out or abs(t - out[-1]) > 1e-9:
            out.append(round(t, 4))
    return out

def main():
    truth = {"segments": {}}

    # 1) 加热段 cycle1：玻璃化台阶(60°C) + 熔融峰(150°C, 吸热 45 J/g)，endo-up，等间隔 0.5K
    rate = 10.0
    Tk = uniform(303.15, 483.15, 0.5)
    Tc = [t - 273.15 for t in Tk]
    y = []
    for tc in Tc:
        v = baseline(tc + 273.15, -0.0004, 0.02)
        v += glass_step(tc, 60.0, 0.08, 4.0)          # W/g 台阶
        v += heatflow_Wg(tc + 273.15, 150.0 + 273.15, 45.0, 3.0, rate, +1)
        y.append(v)
    write_segment("cycle1_heating_endo.csv", Tc, y, "C", "W/g", "endo-up", "10 K/min")
    truth["segments"]["cycle1_heating"] = {
        "mode": "HEATING", "rateKPerMin": rate, "massMg": 12.40,
        "glass": {"TgC": 60.0, "stepWg": 0.08},
        "melt": {"TpC": 150.0, "enthalpyJg": 45.0, "widthK": 3.0, "direction": "ENDOTHERMIC"}}

    # 2) 加热段 cycle2（质量修订后重扫）：同样形状，微差
    y2 = []
    for tc in Tc:
        v = baseline(tc + 273.15, -0.0004, 0.021)
        v += glass_step(tc, 61.0, 0.082, 4.0)
        v += heatflow_Wg(tc + 273.15, 150.4 + 273.15, 46.2, 3.0, rate, +1)
        y2.append(v)
    write_segment("cycle2_heating_endo.csv", Tc, y2, "C", "W/g", "endo-up", "10 K/min")
    truth["segments"]["cycle2_heating"] = {
        "mode": "HEATING", "rateKPerMin": rate, "massMg": 12.10,
        "melt": {"TpC": 150.4, "enthalpyJg": 46.2, "widthK": 3.0, "direction": "ENDOTHERMIC"}}

    # 3) 冷却段：结晶峰(120°C, 放热 38 J/g)，exo-up 仪器（反向热流），等间隔
    Tkc = uniform(473.15, 303.15, -0.5)
    Tcc = [t - 273.15 for t in Tkc]
    y3 = []
    for tc in Tcc:
        v = baseline(tc + 273.15, -0.0002, -0.01)
        # 仪器 exo-up：结晶放热在文件里为正；导入后翻转为内部负
        v += heatflow_Wg(tc + 273.15, 120.0 + 273.15, 38.0, 3.5, rate, +1)
        y3.append(v)
    write_segment("cycle1_cooling_exo.csv", Tcc, y3, "C", "W/g", "exo-up", "10 K/min")
    truth["segments"]["cycle1_cooling"] = {
        "mode": "COOLING", "rateKPerMin": rate, "massMg": 12.40,
        "crystal": {"TpC": 120.0, "enthalpyJg": 38.0, "widthK": 3.5,
                    "fileDirection": "positive(exo-up)", "internalDirection": "EXOTHERMIC"}}

    # 4) 非等间隔加热段：熔融 25 J/g，网格非均匀
    Tn = nonuniform(303.15, 463.15)
    Tnc = [round(t - 273.15, 4) for t in Tn]
    y4 = [baseline(t, 0.0003, 0.0) + heatflow_Wg(t, 130.0 + 273.15, 25.0, 2.5, 10.0, +1)
          for t in Tn]
    write_segment("nonuniform_heating.csv", Tnc, y4, "C", "W/g", "endo-up", "10 K/min")
    truth["segments"]["nonuniform_heating"] = {
        "mode": "HEATING", "rateKPerMin": 10.0, "massMg": 12.40,
        "melt": {"TpC": 130.0, "enthalpyJg": 25.0, "widthK": 2.5},
        "grid": "nonuniform"}

    # 5) 重叠转折：熔融主峰 155°C/40 J/g + 肩峰 163°C/12 J/g
    y5 = []
    for tc in Tc:
        v = baseline(tc + 273.15, -0.0003, 0.015)
        v += heatflow_Wg(tc + 273.15, 155.0 + 273.15, 40.0, 3.0, rate, +1)
        v += heatflow_Wg(tc + 273.15, 163.0 + 273.15, 12.0, 2.2, rate, +1)
        y5.append(v)
    write_segment("overlap_heating.csv", Tc, y5, "C", "W/g", "endo-up", "10 K/min")
    truth["segments"]["overlap_heating"] = {
        "mode": "HEATING", "rateKPerMin": rate, "massMg": 12.40,
        "peaks": [{"TpC": 155.0, "enthalpyJg": 40.0, "widthK": 3.0},
                  {"TpC": 163.0, "enthalpyJg": 12.0, "widthK": 2.2}],
        "combinedEnthalpyJg": 52.0}

    # 6) 反向热流但文件头缺失方向（方向未决；禁止文件名猜测）
    y6 = []
    for tc in Tc:
        v = baseline(tc + 273.15, 0.0, 0.0)
        v += heatflow_Wg(tc + 273.15, 140.0 + 273.15, 30.0, 3.0, rate, +1)
        y6.append(v)
    write_segment("mystery_heating.csv", Tc, y6, "C", "W/g", "", "10 K/min")
    truth["segments"]["mystery_heating"] = {
        "mode": "HEATING", "rateKPerMin": rate, "massMg": 12.40,
        "peak": {"TpC": 140.0, "magnitudeJg": 30.0},
        "direction": "UNRESOLVED"}

    # 7) 原始热流 mW（质量归一化在派生层）+ 一行坏值（无法转换留在原位）
    mass_g = 0.01240
    y7_mW = []
    for tc in Tc:
        wg = baseline(tc + 273.15, -0.0004, 0.02)
        wg += heatflow_Wg(tc + 273.15, 150.0 + 273.15, 45.0, 3.0, rate, +1)
        y7_mW.append(wg * mass_g * 1000.0)
    lines = []
    for i, (tc, mw) in enumerate(zip(Tc, y7_mW)):
        if i == 200:
            lines.append(f"{tc:.4f},NA_SENSOR_DROP")
        else:
            lines.append(f"{tc:.4f},{mw:.6f}")
    with open(os.path.join(ROOT, "cycle1_heating_mW.csv"), "w", encoding="utf-8") as f:
        f.write("# instrument_model: DSC-SYNTH-9000\n")
        f.write("# heat_flow_direction: endo-up\n")
        f.write("# temperature_unit: C\n# heat_flow_unit: mW\n# scan_rate: 10 K/min\n")
        f.write("temperature,heat_flow\n")
        f.write("\n".join(lines) + "\n")
    truth["segments"]["cycle1_heating_mW"] = {
        "mode": "HEATING", "rateKPerMin": rate, "massMg": 12.40,
        "melt": {"TpC": 150.0, "enthalpyJg": 45.0}, "badRowIndex": 200}

    with open(os.path.join(ROOT, "truth.json"), "w", encoding="utf-8") as f:
        json.dump(truth, f, ensure_ascii=False, indent=2)
    print("casepack generated at", ROOT)

if __name__ == "__main__":
    main()
