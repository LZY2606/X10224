'use strict';

const state = { history: null, viewSeq: null, segmentId: null, regionId: null,
                drag: null, story: null, tourIndex: 0 };

const $ = (sel) => document.querySelector(sel);
const $$ = (sel) => Array.from(document.querySelectorAll(sel));
const fmt = (v, digits = 3) => (v === null || v === undefined || Number.isNaN(v))
    ? '—' : Number(v).toFixed(digits);
const q = (qq) => qq && qq.value !== null && qq.value !== undefined
    ? `${fmt(qq.value)} ${qq.unit || ''}` : '—';

async function api(path, opts) {
  const res = await fetch(path, opts);
  if (!res.ok) {
    const body = await res.json().catch(() => ({}));
    throw Object.assign(new Error(body.message || res.statusText), { status: res.status, body });
  }
  return res.json();
}

async function loadHistory() {
  const suffix = state.viewSeq ? `?atSeq=${state.viewSeq}` : '';
  state.history = await api(`/api/history${suffix}`);
  render();
}

document.querySelectorAll('.tabs button').forEach((btn) => {
  btn.addEventListener('click', () => {
    $$('.tabs button').forEach(b => b.classList.remove('active'));
    $$('.tab').forEach(t => t.classList.remove('active'));
    btn.classList.add('active');
    $('#tab-' + btn.dataset.tab).classList.add('active');
  });
});

$('#btnExport').addEventListener('click', () => {
  window.location.href = '/api/export';
});

$('#timeSelect').addEventListener('change', (e) => {
  state.viewSeq = e.target.value ? Number(e.target.value) : null;
  loadHistory();
});

$('#segmentSelect').addEventListener('change', (e) => {
  state.segmentId = e.target.value;
  state.regionId = firstRegionOnSegment()?.id || null;
  render();
});
$('#regionSelect').addEventListener('change', (e) => {
  state.regionId = e.target.value || null;
  render();
});
$('#btnRecompute').addEventListener('click', async () => {
  if (!state.regionId) return;
  const r = await api(`/api/recompute/${encodeURIComponent(state.regionId)}`, { method: 'POST' });
  toast(`重算完成：基线 ${r.baselineIds.length} 条；受影响节点 ${r.affectedNodeIds.length}；`
      + `未受影响区间 ${r.unaffectedRegions.length}`);
  await loadHistory();
});
$('#btnAdjustTangent').addEventListener('click', async () => {
  const handles = currentHandles();
  if (!handles || !state.regionId) return;
  const body = {
    regionId: state.regionId,
    tangentId: currentTangentId(),
    leftLo: handles.leftLo, leftHi: handles.leftHi,
    rightLo: handles.rightLo, rightHi: handles.rightHi
  };
  await api('/api/adjust', { method: 'POST', headers: { 'Content-Type': 'application/json' },
                             body: JSON.stringify(body) });
  toast('切线调整已作为新事件提交；旧派生保留为历史。');
  await loadHistory();
});

function render() {
  const h = state.history;
  if (!h) return;
  $('#historyName').textContent = `${h.name} · head seq=${h.headSeq}`
      + (h.viewedAtSeq !== h.headSeq ? ` · 正在查看 seq=${h.viewedAtSeq} 的历史结论` : '');
  renderTimeSelect();
  renderSegmentSelect();
  renderRegionSelect();
  drawChart();
  renderFeatures();
  renderSources();
  renderBaselines();
  renderEvents();
  renderAnomalies();
}

function renderTimeSelect() {
  const sel = $('#timeSelect');
  const keep = sel.value;
  sel.innerHTML = '<option value="">最新（head）</option>';
  state.history.events.forEach((e) => {
    const opt = document.createElement('option');
    opt.value = e.seq;
    opt.textContent = `seq ${e.seq} · ${e.type} · ${e.actor || ''}`;
    sel.appendChild(opt);
  });
  sel.value = keep;
}

function segment() {
  return state.history.segments.find(s => s.id === state.segmentId) || state.history.segments[0];
}
function region() {
  return state.history.regions.find(r => r.id === state.regionId);
}
function firstRegionOnSegment() {
  return state.history.regions.find(r => r.segmentId === segment()?.id);
}
function renderSegmentSelect() {
  const sel = $('#segmentSelect');
  if (!state.segmentId && state.history.segments[0]) state.segmentId = state.history.segments[0].id;
  sel.innerHTML = '';
  state.history.segments.forEach((s) => {
    const opt = document.createElement('option');
    opt.value = s.id;
    opt.textContent = `${s.sourceFile} · cycle ${s.cycle} · ${s.directionLabel}`
        + (s.supersededBy ? ' (已被替换)' : '');
    sel.appendChild(opt);
  });
  sel.value = state.segmentId;
  const s = segment();
  if (s) {
    $('#segmentMeta').textContent = `方向=${s.directionLabel}（由数据判定） · 约定=${s.convention}`
        + ` · ${s.nonUniform ? '非等间隔温度' : '等间隔'} · 点数=${s.points.length}`
        + ` · 原始来源=${s.rawDescriptor}`;
  }
}
function renderRegionSelect() {
  const sel = $('#regionSelect');
  if (!state.regionId) state.regionId = firstRegionOnSegment()?.id;
  sel.innerHTML = '<option value="">（不选区间）</option>';
  state.history.regions.filter(r => r.segmentId === segment()?.id).forEach((r) => {
    const opt = document.createElement('option');
    opt.value = r.id;
    opt.textContent = `${r.label} [${fmt(r.tLo, 1)}, ${fmt(r.tHi, 1)}] ${r.kind}`;
    sel.appendChild(opt);
  });
  sel.value = state.regionId || '';
}

const SVGNS = 'http://www.w3.org/2000/svg';
function el(name, attrs) {
  const node = document.createElementNS(SVGNS, name);
  Object.entries(attrs || {}).forEach(([k, v]) => node.setAttribute(k, v));
  return node;
}

function drawChart() {
  const svg = $('#chart');
  svg.innerHTML = '';
  const seg = segment();
  if (!seg) return;
  const pts = seg.points.filter(p => p.t !== null);
  const W = 900, H = 460, L = 58, R = 18, T = 18, B = 42;
  const tMin = Math.min(...pts.map(p => p.t)), tMax = Math.max(...pts.map(p => p.t));
  const hMin = Math.min(...pts.map(p => p.hf)), hMax = Math.max(...pts.map(p => p.hf));
  const x = (t) => L + (t - tMin) / (tMax - tMin || 1) * (W - L - R);
  const y = (v) => T + (hMax - v) / (hMax - hMin || 1) * (H - T - B);
  state.scales = { x, y, tMin, tMax, hMin, hMax, L, R, T, B, W, H };

  // grid + axes
  for (let i = 0; i <= 5; i++) {
    const gx = L + i * (W - L - R) / 5;
    svg.appendChild(el('line', { x1: gx, y1: T, x2: gx, y2: H - B, stroke: '#22304a' }));
    const tt = tMin + i * (tMax - tMin) / 5;
    const tx = el('text', { x: gx, y: H - B + 16, 'text-anchor': 'middle', class: 'axis' });
    tx.textContent = fmt(tt, 1) + '°C'; svg.appendChild(tx);
  }
  for (let i = 0; i <= 4; i++) {
    const gy = T + i * (H - T - B) / 4;
    svg.appendChild(el('line', { x1: L, y1: gy, x2: W - R, y2: gy, stroke: '#22304a' }));
    const hv = hMax - i * (hMax - hMin) / 4;
    const ty = el('text', { x: L - 8, y: gy + 4, 'text-anchor': 'end', class: 'axis' });
    ty.textContent = fmt(hv, 2); svg.appendChild(ty);
  }
  svg.appendChild(el('text', { x: W / 2, y: H - 6, 'text-anchor': 'middle', class: 'legend' }))
      .textContent = '温度 (°C，已标定)';
  const yl = el('text', { x: 16, y: H / 2, 'text-anchor': 'middle',
      transform: `rotate(-90 16 ${H / 2})`, class: 'legend' });
  yl.textContent = '热流 (mW，仪器原始方向)'; svg.appendChild(yl);

  // signal polyline
  const d = pts.map((p, i) => `${i ? 'L' : 'M'}${x(p.t).toFixed(2)},${y(p.hf).toFixed(2)}`).join('');
  svg.appendChild(el('path', { d, fill: 'none', stroke: '#7fb6ff', 'stroke-width': 1.6 }));
  pts.filter(p => p.diagnostics).forEach((p) => {
    svg.appendChild(el('circle', { cx: x(p.t), cy: H - B - 8, r: 4, fill: '#ef6b6b' }));
  });

  drawRegions(svg);
  drawBaselines(svg);
  drawFeatures(svg);
}

function regionBands() {
  const seg = segment();
  return state.history.regions.filter(r => r.segmentId === seg.id);
}

function drawRegions(svg) {
  const { x, y, T, B } = state.scales;
  regionBands().forEach((r) => {
    const active = r.id === state.regionId;
    const x1 = x(r.tLo), x2 = x(r.tHi);
    svg.appendChild(el('rect', { x: x1, y: T, width: Math.max(2, x2 - x1), height: 460 - T - B,
      fill: active ? '#5aa7ff' : '#8a94a8', opacity: active ? 0.10 : 0.05 }));
    svg.appendChild(el('line', { x1: x1, y1: T, x2: x1, y2: 460 - B,
      stroke: active ? '#f0b74e' : '#5b6780', 'stroke-width': active ? 2 : 1, class: 'handle',
      'data-region': r.id, 'data-side': 'lo' }));
    svg.appendChild(el('line', { x1: x2, y1: T, x2: x2, y2: 460 - B,
      stroke: active ? '#f0b74e' : '#5b6780', 'stroke-width': active ? 2 : 1, class: 'handle',
      'data-region': r.id, 'data-side': 'hi' }));
    const label = el('text', { x: (x1 + x2) / 2, y: T + 12, 'text-anchor': 'middle', class: 'legend' });
    label.textContent = r.label;
    svg.appendChild(label);
  });

  svg.querySelectorAll('.handle').forEach((h) => {
    h.addEventListener('mousedown', (e) => {
      if (h.dataset.region !== state.regionId) {
        state.regionId = h.dataset.region;
        $('#regionSelect').value = state.regionId;
        renderFeatures(); renderBaselines();
      }
      state.drag = { regionId: h.dataset.region, side: h.dataset.side };
      e.preventDefault();
    });
  });
}

function currentHandles() {
  const r = region();
  if (!r) return null;
  return { leftLo: r.tLo, leftHi: r.tLo + Math.max(2, (r.tHi - r.tLo) * 0.2),
           rightLo: r.tHi - Math.max(2, (r.tHi - r.tLo) * 0.2), rightHi: r.tHi };
}
function currentTangentId() {
  const tans = state.history.events.filter(e => e.type === 'TANGENT_SELECTED');
  const forRegion = tans.filter(e => e.entityId && relatedRegion(e) === state.regionId);
  return forRegion.length ? forRegion[forRegion.length - 1].entityId : null;
}
function relatedRegion(e) { return null; }

document.addEventListener('mousemove', (e) => {
  if (!state.drag) return;
  const svg = $('#chart');
  const rect = svg.getBoundingClientRect();
  const { L, R, W, tMin, tMax } = state.scales;
  const px = (e.clientX - rect.left) / rect.width * W;
  const t = tMin + ((px - L) / (W - L - R)) * (tMax - tMin);
  const r = state.history.regions.find(x2 => x2.id === state.drag.regionId);
  if (!r) return;
  if (state.drag.side === 'lo') r.tLo = Math.min(Math.max(t, tMin), r.tHi - 0.5);
  else r.tHi = Math.max(Math.min(t, tMax), r.tLo + 0.5);
  drawChart();
});
document.addEventListener('mouseup', () => { state.drag = null; });

function baselinesForActiveRegion() {
  const r = region();
  if (!r) return [];
  return state.history.derivations.baselines.filter(b => b.regionId === r.id && !b.rejected);
}
function acceptedBaselineId(regionId) {
  const a = state.history.accepted.find(x2 => x2.regionId === regionId);
  return a ? a.baselineId : null;
}

function drawBaselines(svg) {
  const { x, y, tMin, tMax } = state.scales;
  const colors = { 'two-sided': '#4ec9a0', parallel: '#f0b74e', global: '#c08cff',
                   indeterminate: '#ef6b6b' };
  baselinesForActiveRegion().forEach((b) => {
    if (b.slope === null) return;
    const accepted = b.id === acceptedBaselineId(b.regionId);
    const d = `M${x(tMin)},${y(b.slope * tMin + b.intercept)} L${x(tMax)},${y(b.slope * tMax + b.intercept)}`;
    svg.appendChild(el('path', { d, fill: 'none',
      stroke: colors[b.mode] || '#999', 'stroke-width': accepted ? 2.6 : 1.2,
      'stroke-dasharray': accepted ? '' : '5 4', opacity: accepted ? 0.95 : 0.65 }));
  });
}

function drawFeatures(svg) {
  const r = region();
  if (!r) return;
  const { x, y } = state.scales;
  state.history.derivations.features
      .filter(f => f.regionId === r.id && !f.rejected)
      .forEach((f) => {
        [['onset', f.onset], ['peak', f.peak], ['endset', f.endset]].forEach(([name, qq]) => {
          if (!qq || qq.value === null) return;
          // mark on the signal at that temperature using nearest point
          const seg = segment();
          const p = nearestPoint(seg, qq.value);
          if (!p) return;
          svg.appendChild(el('circle', { cx: x(qq.value), cy: y(p.hf), r: name === 'peak' ? 4 : 3,
            fill: name === 'peak' ? '#ef6b6b' : '#f0d26b', stroke: '#0b101b', 'stroke-width': 1 }));
          const tx = el('text', { x: x(qq.value), y: y(p.hf) - 8, 'text-anchor': 'middle',
            class: 'legend' });
          tx.textContent = name;
          svg.appendChild(tx);
        });
      });
}
function nearestPoint(seg, t) {
  const pts = seg.points.filter(p => p.t !== null);
  let best = null, bd = Infinity;
  pts.forEach(p => { const dd = Math.abs(p.t - t); if (dd < bd) { bd = dd; best = p; } });
  return best;
}

function renderFeatures() {
  const r = region();
  const host = $('#featureCards');
  host.innerHTML = '';
  if (!r) { host.innerHTML = '<p class="meta">选择一个区间查看派生结果。</p>'; renderErrors(null); return; }
  const feats = state.history.derivations.features.filter(f => f.regionId === r.id && !f.rejected);
  if (!feats.length) {
    host.innerHTML = '<p class="meta">本区间尚无可用派生（可能是空区间或缺少基线）。</p>';
    renderErrors(null);
    return;
  }
  feats.forEach((f) => {
    const card = document.createElement('div');
    card.className = 'feature';
    const accepted = f.baselineId === acceptedBaselineId(r.id);
    card.innerHTML = `
      <div><span class="badge ${f.status}">${f.status}</span>
        ${accepted ? '<span class="badge VALID">已采纳基线</span>' : '<span class="badge MULTIPLE">候选</span>'}
        <span class="meta">规则 ${f.ruleVersion}</span></div>
      <div class="vals">
        <b>onset</b><span>${q(f.onset)}</span>
        <b>peak</b><span>${q(f.peak)}</span>
        <b>endset</b><span>${q(f.endset)}</span>
        <b>ΔH 总焓变</b><span>${q(f.deltaH)} · ${f.sign}</span>
        <b>ΔH 比焓</b><span>${q(f.deltaHSpecific)}</span>
        <b>质量版本</b><span class="id-cell" data-chain="${f.massIdUsed || ''}">${f.massIdUsed || '未归档'}</span>
        <b>标定版本</b><span class="id-cell" data-chain="${f.calibrationIdUsed || ''}">${f.calibrationIdUsed || '无'}</span>
      </div>
      <div class="meta">派生身份 <span class="id-cell" data-chain="${f.id}">${f.id}</span></div>`;
    host.appendChild(card);
  });
  renderErrors(feats[0]);
  bindChainClicks(host);
}

function renderErrors(f) {
  const host = $('#errorBudget');
  host.innerHTML = '';
  if (!f) { host.innerHTML = '<p class="meta">—</p>'; return; }
  const table = document.createElement('table');
  table.innerHTML = '<tr><th>来源</th><th>量级</th><th>依据</th></tr>';
  f.errorBudget.forEach((e) => {
    const tr = document.createElement('tr');
    tr.innerHTML = `<td>${e.source}</td><td>${fmt(e.magnitude)} ${e.unit}</td><td class="meta">${e.basis}</td>`;
    table.appendChild(tr);
  });
  host.appendChild(table);
}

function renderSources() {
  const h = state.history;
  const host = $('#sourcesView');
  let html = '<div class="two-col">';
  html += '<div class="card"><h3>样品质量（独立版本）</h3><table><tr><th>ID</th><th>版本</th><th>原始表示</th><th>克</th><th>原因</th><th>当前</th></tr>';
  h.mass.versions.forEach(m => {
    html += `<tr><td class="id-cell" data-chain="${m.id}">${m.id}</td><td>v${m.version}</td>
      <td class="kbd">${m.raw} ${m.unit}</td><td>${fmt(m.grams, 5)} g</td>
      <td class="meta">${m.reason}</td><td>${m.current ? '✔' : ''}</td></tr>`;
  });
  html += '</table></div>';
  html += '<div class="card"><h3>温度标定（独立版本）</h3><table><tr><th>ID</th><th>标样</th><th>偏移</th><th>当前</th></tr>';
  h.calibration.versions.forEach(c => {
    html += `<tr><td class="id-cell" data-chain="${c.id}">${c.id}</td><td>${c.standard}</td>
      <td>${q(c.offsetCelsius)}</td><td>${c.current ? '✔' : ''}</td></tr>`;
  });
  html += '</table></div></div>';
  html += '<div class="card" style="margin-top:12px"><h3>气氛（单独存档）</h3><table><tr><th>ID</th><th>气体</th><th>原始流量</th><th>备注</th></tr>';
  h.atmospheres.forEach(a => {
    html += `<tr><td class="id-cell" data-chain="${a.id}">${a.id}</td><td>${a.gas}</td>
      <td class="kbd">${a.flowRaw} ${a.flowUnit}</td><td class="meta">${a.note}</td></tr>`;
  });
  html += '</table></div>';
  html += '<div class="card" style="margin-top:12px"><h3>仪器输出片段（逐点保留原始表示）</h3>';
  h.segments.forEach(s => {
    html += `<div class="judgment INFO"><b>${s.sourceFile}</b> · cycle ${s.cycle} ·
      方向=<b>${s.directionLabel}</b>（数据判定，不靠文件名） · 约定=${s.convention}
      ${s.supersededBy ? '· <span class="badge SUPERSEDED">已被 ' + s.supersededBy + ' 替换</span>' : ''}
      <div class="meta">${s.rawDescriptor}</div>
      ${s.parseDiagnostics.length ? s.parseDiagnostics.map(d =>
        `<div class="badge REJECTED">${d}</div>`).join('') : ''}</div>`;
  });
  html += '</div>';
  host.innerHTML = html;
  bindChainClicks(host);
}

function renderBaselines() {
  const h = state.history;
  const list = $('#baselineList');
  const r = region();
  list.innerHTML = '';
  const related = r ? h.derivations.baselines.filter(b => b.regionId === r.id) : [];
  const cmpA = $('#cmpA'), cmpB = $('#cmpB');
  [cmpA, cmpB].forEach(sel => { sel.innerHTML = ''; });
  related.forEach((b, i) => {
    [cmpA, cmpB].forEach(sel => {
      const opt = document.createElement('option');
      opt.value = b.id; opt.textContent = `${b.mode} · ${b.status} · ${b.id.slice(0, 14)}`;
      sel.appendChild(opt);
    });
  });
  if (related.length > 1) cmpB.value = related[1].id;

  const table = document.createElement('table');
  table.innerHTML = '<tr><th>候选</th><th>模式</th><th>斜率/截距</th><th>RMSE</th><th>状态</th><th>规则</th><th>操作</th></tr>';
  related.forEach((b) => {
    const accepted = b.id === acceptedBaselineId(b.regionId);
    const tr = document.createElement('tr');
    tr.innerHTML = `<td class="id-cell" data-chain="${b.id}">${b.id}</td>
      <td>${b.mode}</td>
      <td>${fmt(b.slope, 5)} / ${fmt(b.intercept, 3)}</td>
      <td>${fmt(b.rmse, 4)} mW</td>
      <td><span class="badge ${b.status}">${b.status}</span>${accepted ? ' <span class="badge VALID">已采纳</span>' : ''}</td>
      <td class="meta">${b.ruleVersion}</td>
      <td class="actions"></td>`;
    const act = tr.querySelector('.actions');
    if (!accepted && b.status !== 'INDETERMINATE') {
      const ok = document.createElement('button');
      ok.textContent = '采纳';
      ok.onclick = async () => {
        await api('/api/accept', { method: 'POST', headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ regionId: b.regionId, baselineId: b.id }) });
        await loadHistory();
      };
      act.appendChild(ok);
    }
    const no = document.createElement('button');
    no.textContent = '否定（保留）';
    no.style.marginLeft = '6px';
    no.onclick = async () => {
      const reason = prompt('否定该候选的理由（候选仍保留在历史中）', '审阅否定');
      if (reason === null) return;
      await api('/api/reject', { method: 'POST', headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ candidateId: b.id, reason }) });
      await loadHistory();
    };
    act.appendChild(no);
    table.appendChild(tr);
  });
  list.appendChild(table);

  const a = related.find(b => b.id === cmpA.value) || related[0];
  const b2 = related.find(b => b.id === cmpB.value) || related[1];
  $('#baselineCompare').innerHTML = (a ? baselineCard(a) : '<p class="meta">无候选</p>')
      + (b2 && b2.id !== (a && a.id) ? baselineCard(b2) : '');
}

function baselineCard(b) {
  return `<div class="card"><h3>${b.mode} 候选 <span class="badge ${b.status}">${b.status}</span></h3>
    <table>
      <tr><th>身份</th><td class="id-cell" data-chain="${b.id}">${b.id}</td></tr>
      <tr><th>斜率</th><td>${fmt(b.slope, 6)} mW/°C</td></tr>
      <tr><th>截距</th><td>${fmt(b.intercept, 4)} mW</td></tr>
      <tr><th>RMSE</th><td>${fmt(b.rmse, 5)} mW</td></tr>
      <tr><th>依赖</th><td>${(b.dependencies || []).map(d => `<span class="id-cell" data-chain="${d}">${d}</span>`).join('<br>')}</td></tr>
    </table>
    ${(b.judgments || []).map(judgmentHtml).join('')}</div>`;
}

function judgmentHtml(j) {
  return `<div class="judgment ${j.severity}">
    <span class="badge ${j.severity}">${j.severity}</span>
    <code>${j.code}</code> · 规则 ${j.ruleVersion}
    <div>${j.message}</div>
    ${j.subjectId ? `<div class="meta">对象 <span class="id-cell" data-chain="${j.subjectId}">${j.subjectId}</span></div>` : ''}
  </div>`;
}

function renderEvents() {
  const host = $('#eventsView');
  let html = '<div class="card"><h3>事件日志（只追加；点击 seq 回到当时依赖版本与可见结论）</h3><table>'
      + '<tr><th>seq</th><th>类型</th><th>对象身份</th><th>操作者</th><th>说明</th><th>替换</th><th>依赖</th></tr>';
  state.history.events.forEach(e => {
    html += `<tr><td><button class="seqgo" data-seq="${e.seq}">${e.seq}</button></td>
      <td>${e.type}</td>
      <td class="id-cell" data-chain="${e.entityId || ''}">${e.entityId || ''}</td>
      <td>${e.actor || ''}</td>
      <td class="meta">${e.comment || ''}</td>
      <td>${e.supersedes ? `<span class="id-cell" data-chain="${e.supersedes}">${e.supersedes}</span>` : ''}</td>
      <td class="meta">${(e.dependencies || []).join(', ')}</td></tr>`;
  });
  html += '</table></div>';
  host.innerHTML = html;
  host.querySelectorAll('.seqgo').forEach(btn => btn.addEventListener('click', () => {
    state.viewSeq = Number(btn.dataset.seq);
    $('#timeSelect').value = state.viewSeq;
    loadHistory();
    window.scrollTo(0, 0);
  }));
  bindChainClicks(host);
}

function renderAnomalies() {
  const host = $('#anomalyView');
  const groups = { REJECTED: [], SUSPECT: [], INFO: [] };
  state.history.judgments.forEach(j => groups[j.severity].push(j));
  let html = '<p class="meta">点击任意对象身份可进入来源链；判定均为机器可读事件（code/severity/ruleVersion/evidence）。</p>';
  ['REJECTED', 'SUSPECT', 'INFO'].forEach(sev => {
    if (!groups[sev].length) return;
    html += `<div class="card"><h3>${sev}（${groups[sev].length}）</h3>`;
    html += groups[sev].map(judgmentHtml).join('');
    html += '</div>';
  });
  host.innerHTML = html;
  bindChainClicks(host);
}

async function showChain(id) {
  if (!id) return;
  $('#chainContent').textContent = '加载中…';
  $('#sourceChain').classList.remove('hidden');
  try {
    const data = await api(`/api/validity/${encodeURIComponent(id)}`);
    $('#chainContent').textContent = JSON.stringify(data, null, 2);
  } catch (e) {
    $('#chainContent').textContent = `身份 ${id} 不是派生节点（它是来源/决定身份，身份在导出→删除→导入后保持不变）。`;
  }
}
function bindChainClicks(root) {
  root.querySelectorAll('.id-cell').forEach(cell => {
    cell.onclick = () => showChain(cell.dataset.chain || cell.textContent);
  });
}
$('#chainClose').onclick = () => $('#sourceChain').classList.add('hidden');
$('#sourceChain').addEventListener('click', (e) => {
  if (e.target.id === 'sourceChain') $('#sourceChain').classList.add('hidden');
});
['cmpA', 'cmpB'].forEach(id => $('#' + id).addEventListener('change', renderBaselines));

function toast(msg) {
  const bar = document.createElement('div');
  bar.textContent = msg;
  bar.style.cssText = 'position:fixed;bottom:20px;left:50%;transform:translateX(-50%);'
      + 'background:#22314d;border:1px solid #5aa7ff;color:#e7ecf5;padding:10px 16px;'
      + 'border-radius:8px;z-index:30;font-size:13px';
  document.body.appendChild(bar);
  setTimeout(() => bar.remove(), 4200);
}

// ---------- 90-second guided tour ----------
async function loadStory() {
  state.story = await api('/api/story');
}
const TOUR_TABS = { open: 'sources', sources: 'sources', 'first-derivation': 'curve',
  empty: 'curve', reversed: 'curve', 'nonuniform-cycle2': 'curve', overlap: 'baselines',
  'mass-revision': 'events', 'local-amend': 'anomalies', 'conflict-decision': 'events' };
const TOUR_REGION = { 'first-derivation': 0, empty: -1, reversed: -1,
  'nonuniform-cycle2': -1, overlap: -1 };

function showTourStep() {
  const steps = state.story.steps;
  const step = steps[state.tourIndex];
  $('#tourStep').textContent = `[${state.tourIndex + 1}/${steps.length}] `;
  $('#tourText').textContent = step.narration + (step.checkpointSeq > 0
      ? `（历史检查点 seq=${step.checkpointSeq}）` : '');
  const tab = TOUR_TABS[step.key] || 'curve';
  $$('.tabs button').find(b => b.dataset.tab === tab)?.click();
  if (step.checkpointSeq > 0) {
    state.viewSeq = step.checkpointSeq;
    $('#timeSelect').value = step.checkpointSeq;
    loadHistory();
  } else if (state.viewSeq) {
    state.viewSeq = null;
    $('#timeSelect').value = '';
    loadHistory();
  }
  if (step.subjects && step.subjects.length && TOUR_REGION[step.key] !== undefined) {
    const idx = TOUR_REGION[step.key];
    const rid = idx === -1 ? step.subjects[step.subjects.length - 1] : step.subjects[idx];
    const rr = state.history.regions.find(x => x.id === rid);
    if (rr) {
      state.segmentId = rr.segmentId;
      state.regionId = rid;
      render();
    }
  }
}
$('#btnTour').onclick = async () => {
  await loadStory();
  state.tourIndex = 0;
  $('#tourBar').classList.remove('hidden');
  showTourStep();
};
$('#tourNext').onclick = () => {
  state.tourIndex = Math.min((state.story.steps.length - 1), state.tourIndex + 1);
  showTourStep();
};
$('#tourPrev').onclick = () => {
  state.tourIndex = Math.max(0, state.tourIndex - 1);
  showTourStep();
};
$('#tourStop').onclick = () => {
  $('#tourBar').classList.add('hidden');
  state.viewSeq = null; $('#timeSelect').value = ''; loadHistory();
};

(async function init() {
  await loadStory();
  await loadHistory();
})();
