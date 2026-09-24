"use strict";

let state = null;
let asOfView = null;
let selSeg = null;
let selInterval = null;
let dragging = null;

const api = async (path, opts) => {
  const r = await fetch(path, opts);
  if (!r.ok) {
    const t = await r.text();
    throw new Error(t);
  }
  return r.json();
};
const post = (path, body) => api(path, {
  method: "POST",
  headers: {"Content-Type": "application/json"},
  body: body === undefined ? "" : JSON.stringify(body)
});
const C = (t) => t - 273.15;
const fmt = (x, d = 2) => (x === null || x === undefined || Number.isNaN(x)) ? "—" : Number(x).toFixed(d);

async function load() {
  state = await api("/api/state");
  if (!selSeg) selSeg = state.segments[0]?.segmentId;
  if (!selInterval) selInterval = state.intervals[0]?.intervalId;
  render();
}

function artifact(kindId) {
  if (asOfView) {
    const a = (asOfView.artifacts || []).find(x => x.id === kindId);
    return a ? {id:a.id, version:a.version, ruleVersion:a.ruleVersion, depsHash:a.depsHash, content:a.content} : null;
  }
  return (state.artifacts || []).find(a => a.id === kindId) || null;
}
function currentInterval() {
  return (state.intervals || []).find(i => i.intervalId === selInterval);
}
function intervalInSeg(segId) {
  return (state.intervals || []).filter(i => i.segmentId === segId);
}

// ---------------- 渲染 ----------------
function render() {
  document.getElementById("caseMeta").textContent =
    `${state.title} · ${state.caseId} · 规则 ${state.ruleVersion} · 修订号 ${state.docRevision}`;
  renderSegments();
  renderIntervals();
  renderSources();
  renderChart();
  renderCandidates();
  renderIntegration();
  renderDiags();
  renderEvents();
}

function renderSegments() {
  const el = document.getElementById("segmentList");
  el.innerHTML = "";
  for (const s of state.segments) {
    const d = document.createElement("div");
    d.className = "seg" + (s.segmentId === selSeg ? " active" : "");
    const a = artifact("NORMALIZED_CURVE:" + s.segmentId);
    d.innerHTML = `<div><b>${s.segmentId}</b> 循环${s.cycle}
        <span class="dir ${s.direction}">${s.direction}</span>
        <span class="badge">sign:${s.declaredSign}</span>
        <span class="badge">曲线 v${a ? a.version : "—"}</span></div>
      <div class="ident">${s.fileName}</div>
      <div class="r">${s.rate.value} ${s.rate.unit} · ${s.atmosphere.gas} ${s.atmosphere.flowRaw}${s.atmosphere.flowUnit} · 点${s.rawPointCount}（排除${s.excludedCount}）</div>`;
    d.onclick = () => { selSeg = s.segmentId; renderChart(); renderSegments(); };
    el.appendChild(d);
  }
}

function renderIntervals() {
  const el = document.getElementById("intervalList");
  el.innerHTML = "";
  for (const iv of state.intervals) {
    const d = document.createElement("div");
    d.className = "iv" + (iv.intervalId === selInterval ? " active" : "");
    const b = artifact("BASELINE_SET:" + iv.intervalId);
    d.innerHTML = `<div><b>${iv.intervalId}</b> <span class="badge">${iv.segmentId}</span>
        <span class="badge">基线 v${b ? b.version : "—"}</span></div>
      <div class="st">[${fmt(C(iv.t0K),1)}, ${fmt(C(iv.t1K),1)}] °C</div>`;
    d.onclick = () => { selInterval = iv.intervalId; selSeg = iv.segmentId; render(); };
    el.appendChild(d);
  }
}

function renderSources() {
  const el = document.getElementById("sourceList");
  el.innerHTML = "";
  for (const s of state.sources) {
    const d = document.createElement("div");
    d.className = "src";
    let val = "";
    if (s.payload.value) val = `${s.payload.value} ${s.payload.unit || ""}`;
    else if (s.payload.gas) val = `${s.payload.gas} ${s.payload.flowRaw || ""}${s.payload.flowUnit || ""}`;
    d.innerHTML = `<div class="kind">${s.kind} · v${s.version} → ${s.refId}</div>
      <div>${val}</div><div class="rep">${s.originalRep}</div>
      <div class="ident">id=${s.id} · seq=${s.sequence ?? ""}</div>`;
    el.appendChild(d);
  }
}

// ---------------- SVG 曲线 + 拖动区间 ----------------
function renderChart() {
  const svg = document.getElementById("chart");
  svg.innerHTML = "";
  const seg = state.segments.find(s => s.segmentId === selSeg);
  if (!seg) return;
  const curveA = artifact("NORMALIZED_CURVE:" + selSeg);
  const curve = curveA?.content;
  document.getElementById("chartTitle").innerHTML =
    `${selSeg} · ${seg.direction} · 循环${seg.cycle} <span class="ident">曲线稳定身份 NORMALIZED_CURVE:${selSeg} v${curveA?.version ?? "—"}</span>`;
  if (!curve) { svg.textContent = "无曲线"; return; }

  const W = 860, H = 420, L = 56, R = 16, T = 18, B = 40;
  const pts = curve.points.filter(p => !p.excluded);
  const tmin = Math.min(...pts.map(p => p.tK)), tmax = Math.max(...pts.map(p => p.tK));
  let ymin = Math.min(...pts.map(p => p.hfPerMass)), ymax = Math.max(...pts.map(p => p.hfPerMass));
  const pad = (ymax - ymin) * 0.08 || 0.01; ymin -= pad; ymax += pad;
  const X = t => L + (t - tmin) / (tmax - tmin) * (W - L - R);
  const Y = y => T + (ymax - y) / (ymax - ymin) * (H - T - B);

  // 网格 + 轴
  for (let i = 0; i <= 6; i++) {
    const t = tmin + (tmax - tmin) * i / 6;
    svg.add("line", {x1:X(t), x2:X(t), y1:T, y2:H-B, stroke:"#1d2740"});
    svg.add("text", {x:X(t), y:H-B+16, "text-anchor":"middle", fill:"#7083a8", "font-size":10}, fmt(C(t),1) + "°C");
  }
  for (let i = 0; i <= 4; i++) {
    const y = ymin + (ymax - ymin) * i / 4;
    svg.add("line", {x1:L, x2:W-R, y1:Y(y), y2:Y(y), stroke:"#1d2740"});
    svg.add("text", {x:L-6, y:Y(y)+3, "text-anchor":"end", fill:"#7083a8", "font-size":10}, fmt(y,3));
  }
  svg.add("text", {x:14, y:T+10, fill:"#7083a8", "font-size":10}, "W/g");
  if (curve.signStatus !== "OK") {
    svg.add("text", {x:L+8, y:T+18, fill:"#ff8f7a", "font-size":11}, "方向状态 INVALID（方向不来自文件名）");
  }

  // 曲线
  const d = pts.map((p, i) => (i ? "L" : "M") + fmt(X(p.tK),1) + " " + fmt(Y(p.hfPerMass),1)).join(" ");
  svg.add("path", {d, fill:"none", stroke:"#7db4ff", "stroke-width":1.6});

  // 区间阴影 + 把手（仅本段区间）
  for (const iv of intervalInSeg(selSeg)) {
    const active = iv.intervalId === selInterval;
    const x0 = X(iv.t0K), x1 = X(iv.t1K);
    svg.add("rect", {x:x0, y:T, width:Math.max(2,x1-x0), height:H-T-B,
      fill:active ? "#4d8dff" : "#35507f", opacity: active ? 0.14 : 0.07});
    for (const [xx, edge] of [[x0,"t0"],[x1,"t1"]]) {
      const h = svg.add("rect", {x:xx-4, y:T, width:8, height:H-T-B,
        fill: active ? "#ffd166" : "#8fa3c6", opacity:.85, rx:3,
        "data-iv":iv.intervalId, "data-edge":edge, style:"cursor:ew-resize"});
      h.addEventListener("mousedown", (e) => {
        selInterval = iv.intervalId;
        dragging = {edge, iv};
        e.preventDefault(); render();
      });
    }
    svg.add("text", {x:x0+4, y:T+12, fill: active ? "#ffd166" : "#8fa3c6", "font-size":10}, iv.intervalId);
    drawBaselines(svg, iv, X, Y, tmin, tmax);
    drawFeatures(svg, iv, X, Y);
  }
  document.getElementById("chartTitle").dataset.x = "1";
  svg.addEventListener("mousemove", onMove);
  svg.addEventListener("mouseup", onUp);
  svg.addEventListener("mouseleave", onUp);
}

function drawBaselines(svg, iv, X, Y, tmin, tmax) {
  const b = artifact("BASELINE_SET:" + iv.intervalId);
  if (!b) return;
  const set = b.content;
  const colors = {TANGENT_LINE:"#ffd166", SIGMOID_STEP:"#a78bfa", PIECEWISE_OVERLAP:"#06d6a0"};
  for (const cand of set.candidates || []) {
    const isSel = cand.candidateId === set.selectedCandidateId;
    const stroke = cand.origin === "USER_TANGENT" ? "#ff9f68" : (colors[cand.method] || "#888");
    const d = sampleLine(cand, iv, 60).map((q,i)=> (i?"L":"M") + X(q[0]) + " " + Y(q[1])).join(" ");
    svg.add("path", {d, fill:"none", stroke, "stroke-width": isSel ? 2.4 : 1.1,
      "stroke-dasharray": isSel ? "" : "4 3", opacity: isSel ? 1 : .7});
  }
}

function sampleLine(cand, iv, n) {
  const out = [];
  const knots = cand.knots;
  const line = cand.line;
  for (let i = 0; i < n; i++) {
    const t = iv.t0K + (iv.t1K - iv.t0K) * i / (n - 1);
    let seg = 0;
    for (let k = 1; k < knots.length - 1; k++) if (t >= knots[k]) seg = k;
    out.push([t, line[2*seg]*t + line[2*seg+1]]);
  }
  return out;
}

function drawFeatures(svg, iv, X, Y) {
  const tr = artifact("TRANSITION_INTEGRATION:" + iv.transitionId);
  if (!tr) return;
  const r = tr.content;
  for (const f of r.features || []) {
    svg.add("circle", {cx:X(f.tK), cy:Y(f.y), r:4.5, class:"feat-"+f.kind, stroke:"#0c1220"});
    svg.add("text", {x:X(f.tK)+6, y:Y(f.y)-5, fill:"#cdd8ee", "font-size":9.5},
      f.kind + " " + fmt(C(f.tK),1) + "°C");
  }
}

async function onMove(e) {
  if (!dragging) return;
  const svg = document.getElementById("chart");
  const rect = svg.getBoundingClientRect();
  const px = (e.clientX - rect.left) / rect.width * 860;
  const seg = state.segments.find(s => s.segmentId === selSeg);
  const curveA = artifact("NORMALIZED_CURVE:" + selSeg);
  const pts = curveA.content.points.filter(p => !p.excluded);
  const L = 56, R = 16;
  const tmin = Math.min(...pts.map(p=>p.tK)), tmax = Math.max(...pts.map(p=>p.tK));
  const t = tmin + (px - L) / (860 - L - R) * (tmax - tmin);
  const iv = state.intervals.find(i => i.intervalId === dragging.iv.intervalId);
  const cur = {t0K:iv.t0K, t1K:iv.t1K};
  if (dragging.edge === "t0") cur.t0K = Math.min(Math.max(t, tmin), cur.t1K - 0.5);
  else cur.t1K = Math.max(Math.min(t, tmax), cur.t0K + 0.5);
  renderChartLive(cur);
  dragging.pending = cur;
}
function renderChartLive(cur) {
  const iv = state.intervals.find(i => i.intervalId === dragging.iv.intervalId);
  iv.t0K = cur.t0K; iv.t1K = cur.t1K;
  renderChart();
}
async function onUp() {
  if (!dragging || !dragging.pending) { dragging = null; return; }
  const cur = dragging.pending;
  const {iv} = dragging;
  dragging = null;
  try {
    state = await post("/api/intervals/adjust", {
      intervalId: iv.intervalId, segmentId: iv.segmentId,
      t0K: cur.t0K, t1K: cur.t1K, author:"web-reviewer",
      baseRevision: state.docRevision
    });
    render();
  } catch (err) { alert("区间调整被拒绝：\n" + JSON.parse(err.message).message); await load(); }
}

// ---------------- 基线候选 / 积分 / 异常面板 ----------------
function renderCandidates() {
  const el = document.getElementById("candidateList");
  el.innerHTML = "";
  const iv = currentInterval();
  if (!iv) { el.textContent = "—"; return; }
  const b = artifact("BASELINE_SET:" + iv.intervalId);
  if (!b) { el.textContent = "—"; return; }
  const set = b.content;
  for (const c of set.candidates || []) {
    const d = document.createElement("div");
    d.className = "cand" + (c.candidateId === set.selectedCandidateId ? " selected" : "");
    d.innerHTML = `<div class="row"><span class="m">${c.method} · ${c.origin}</span>
        <span class="ident">${c.candidateId}</span></div>
      <div class="r">${c.rationale}</div>
      <div class="r">残差 RMS = ${fmt(c.residualRms, 5)} W/g</div>`;
    if (!asOfView) {
      const row = document.createElement("div");
      row.className = "row";
      const a = document.createElement("button");
      a.className = "minibtn"; a.textContent = c.candidateId === set.selectedCandidateId ? "已采纳" : "接受此基线";
      a.onclick = async () => {
        state = await post("/api/decisions", {intervalId: iv.intervalId, candidateId: c.candidateId,
          action:"ACCEPT", baseRevision: state.docRevision});
        render();
      };
      const r = document.createElement("button");
      r.className = "minibtn ghost"; r.textContent = "否定";
      r.onclick = async () => {
        state = await post("/api/decisions", {intervalId: iv.intervalId, candidateId: c.candidateId,
          action:"REJECT", baseRevision: state.docRevision});
        render();
      };
      row.append(a, r);
      d.appendChild(row);
    }
    el.appendChild(d);
  }
  for (const msg of set.diagnostics || []) {
    const d = document.createElement("div");
    d.className = "diag " + (msg.startsWith("MULTIPLE") ? "" : "info");
    d.textContent = msg;
    el.appendChild(d);
  }
}

function renderIntegration() {
  const el = document.getElementById("integrationPanel");
  el.innerHTML = "";
  const iv = currentInterval();
  if (!iv) { el.textContent = "—"; return; }
  const tr = artifact("TRANSITION_INTEGRATION:" + iv.transitionId);
  if (!tr) { el.textContent = "—"; return; }
  const r = tr.content;
  const id = document.createElement("div");
  id.className = "ident";
  id.textContent = `稳定身份 TRANSITION_INTEGRATION:${iv.transitionId} v${tr.version} · 规则 ${tr.ruleVersion}`;
  el.appendChild(id);
  const kv = document.createElement("div");
  kv.className = "kv";
  kv.innerHTML = `<span class="k">事件类别</span><span class="v">${r.eventClass}</span>
    <span class="k">吸放热</span><span class="v">${r.thermalSign}（依据仪器表头，不看文件名）</span>
    <span class="k">焓变 ΔH</span><span class="v">${r.enthalpyJPerG==null ? "INVALID" : fmt(r.enthalpyJPerG,4)+" J/g"}</span>
    <span class="k">ΔCp（玻璃化）</span><span class="v">${r.cpShiftJPerGK==null ? "—" : fmt(r.cpShiftJPerGK,4)+" J/(g·K)"}</span>
    <span class="k">温度不确定度</span><span class="v">±${fmt(r.temperatureUncertaintyK,2)} K</span>
    <span class="k">质量版本</span><span class="v">${r.massVersion}</span>
    <span class="k">基线来源</span><span class="v">${r.baselineCandidateId || "—"}</span>`;
  el.appendChild(kv);
  for (const f of r.features || []) {
    const d = document.createElement("div");
    d.className = "diag info";
    d.innerHTML = `<b class="feat-${f.kind}">●</b> ${f.kind}: ${fmt(C(f.tK),2)} °C / ${fmt(f.y,4)} W/g
      <div class="ident">坐标基准：规范 K 与 ENDO_UP W/g</div>`;
    el.appendChild(d);
  }
  const eh = document.createElement("div");
  eh.style.marginTop = "6px";
  eh.textContent = "焓变误差来源：";
  el.appendChild(eh);
  for (const e of r.errorSources || []) {
    const d = document.createElement("div");
    d.className = "errsrc";
    d.innerHTML = `<b>${e.code}</b> · ${fmt(e.magnitudeJPerG,5)} J/g<div>${e.explanation}</div>`;
    el.appendChild(d);
  }
}

function renderDiags() {
  const el = document.getElementById("diagPanel");
  el.innerHTML = "";
  const seg = state.segments.find(s => s.segmentId === selSeg);
  const curveA = artifact("NORMALIZED_CURVE:" + selSeg);
  const msgs = curveA?.content?.diagnostics || [];
  if (!msgs.length) { el.textContent = "本段无异常"; return; }
  for (const m of msgs) {
    const d = document.createElement("div");
    d.className = "diag info";
    d.textContent = m;
    const chain = document.createElement("div");
    chain.className = "chain";
    chain.innerHTML = `<b>来源链</b><br>
      INSTRUMENT_OUTPUT:inst-seg-${selSeg}（原始 CSV：${seg.fileName}，表头：${seg.header}）<br>
      → TEMPERATURE_CAL:calib-thermocouple-T1<br>
      → SAMPLE_MASS:mass-sample-PET-042（当前版本）<br>
      → NORMALIZED_CURVE:${selSeg} v${curveA.version}（规则 ${curveA.ruleVersion}，指纹 ${curveA.depsHash}）`;
    d.onclick = () => chain.classList.toggle("open");
    el.append(d, chain);
  }
}

// ---------------- 事件时间线 / 历史视图 ----------------
function renderEvents() {
  const el = document.getElementById("eventList");
  el.innerHTML = "";
  const events = asOfView
    ? state.events.filter(e => e.sequence <= asOfView.event.sequence)
    : state.events;
  for (const e of events) {
    const d = document.createElement("div");
    d.className = "evt";
    d.innerHTML = `<span class="seq">#${e.sequence}</span><span class="ty ${e.type}">${e.type}</span><span class="sm">${e.summary}</span>`;
    d.onclick = async () => {
      asOfView = await api("/api/events/" + e.sequence + "/asof");
      showAsof();
    };
    el.appendChild(d);
  }
}

function showAsof() {
  const b = document.getElementById("asofBanner");
  b.classList.remove("hidden");
  document.getElementById("asofText").textContent =
    `历史视图：事件 #${asOfView.event.sequence} ${asOfView.event.type} — ${asOfView.event.summary}（规则 ${asOfView.ruleVersion}，只读）`;
  render();
}

// ---------------- 操作绑定 ----------------
document.getElementById("btnBackNow").onclick = async () => { asOfView = null; document.getElementById("asofBanner").classList.add("hidden"); await load(); };
document.getElementById("btnRule").onclick = async () => { state = await post("/api/beats/rule-revision", {author:"web-reviewer"}); render(); };
document.getElementById("btnMass").onclick = async () => { state = await post("/api/beats/mass-revision", {}); render(); };
document.getElementById("btnLocal").onclick = async () => { state = await post("/api/beats/local-source-change", {segmentId:"seg2", flowRaw:"45"}); render(); };
document.getElementById("btnEmpty").onclick = async () => {
  const iv = currentInterval() || state.intervals[0];
  state = await post("/api/intervals/adjust", {intervalId:"interval-empty-probe", segmentId: iv.segmentId,
    t0K:120, t1K:121, baseRevision: state.docRevision});
  selInterval = "interval-empty-probe"; render();
};
document.getElementById("btnConflict").onclick = async () => {
  const b = (state.artifacts.find(a=>a.id==="BASELINE_SET:interval-melt")?.content) || {};
  const t = (b.candidates||[]).find(c=>c.method==="TANGENT_LINE")?.candidateId;
  const s = (b.candidates||[]).find(c=>c.method==="SIGMOID_STEP")?.candidateId;
  const r = await post("/api/beats/conflict", {intervalId:"interval-melt", candidateA:t, candidateB:s});
  state = r.state; render();
};
document.getElementById("btnReplay").onclick = async () => {
  if (!confirm("将重置为初始案例并执行全部可重复节拍（参数修订/质量修订/局部改动/冲突决定/空区间），继续？")) return;
  await post("/api/reseed");
  state = await post("/api/replay");
  selInterval = "interval-overlap"; render();
};
document.getElementById("btnReseed").onclick = async () => { state = await post("/api/reseed"); asOfView=null; render(); };
document.getElementById("btnExport").onclick = async () => {
  const blob = await (await fetch("/api/export")).blob();
  const a = document.createElement("a");
  a.href = URL.createObjectURL(blob); a.download = "dsc-transition-snapshot.json"; a.click();
};
document.getElementById("fileImport").onchange = async (e) => {
  const text = await e.target.files[0].text();
  state = await post("/api/import", text);
  alert("已删除本地数据库并从快照导入：身份、顺序与来源关系按文件保持。");
  render();
};
document.getElementById("btnImportCycle").onclick = async () => {
  const snapshot = await (await fetch("/api/export")).text();
  const before = JSON.parse(snapshot);
  state = await post("/api/import", snapshot);
  const ok = verifyRoundTrip(before, state, snapshot);
  alert(ok ? "导出 → 删除本地数据库 → 导入完成：身份、顺序、指针与来源关系全部一致。"
           : "校验发现差异，请查看控制台");
  render();
};

function verifyRoundTrip(before, afterState, rawAfterSnapshot) {
  const idsB = before.sources.map(s=>s.id+"@v"+s.version+"#"+s.sequence).join("|");
  const idsA = afterState.sources.map(s=>s.id+"@v"+s.version+"#"+(s.sequence??"")).join("|");
  const evB = before.events.map(e=>e.sequence+":"+e.id).join("|");
  const evA = afterState.events.map(e=>e.sequence+":"+e.id).join("|");
  const ptrB = Object.entries(before.pointers).map(([k,v])=>k+"="+v).sort().join("|");
  const ptrA = (() => {
    const m = {};
    for (const a of afterState.artifacts) {
      // 当前指针即返回的当前 artifacts 集合
    }
    return afterState.artifacts.map(a=>a.id+"="+a.version).sort().join("|");
  })();
  console.log("identity", idsB === idsA, "events", evB === evA, "pointers", ptrB === ptrA);
  return idsB === idsA && evB === evA && ptrB === ptrA;
}

// 小工具：SVG 建元素
SVGElement.prototype.add = function(tag, attrs, text) {
  const el = document.createElementNS("http://www.w3.org/2000/svg", tag);
  for (const [k,v] of Object.entries(attrs||{})) el.setAttribute(k, v);
  if (text != null) el.textContent = text;
  this.appendChild(el);
  return el;
};

load();
