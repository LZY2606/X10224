// 热转折案复核界面：每个显示值带稳定身份；操作都追加新事件；时间旅行不覆盖历史。
const CASE = 'thermal-turning-case';
const state = { view: null, seq: 1, atEvent: '', dragging: null };

const $ = s => document.querySelector(s);
const api = (p, opt) => fetch(p, opt).then(r => {
  if (!r.ok) return r.text().then(t => { throw new Error(t); });
  return r.json();
});

async function loadView() {
  const qs = state.atEvent ? '?at=' + encodeURIComponent(state.atEvent) : '';
  state.view = await api('/api/case/' + CASE + qs);
  render();
}

function celsius(k) { return (k - 273.15).toFixed(1); }
function num(v, d = 2) { return (v == null || Number.isNaN(v)) ? '—' : Number(v).toFixed(d); }
function esc(s) { return String(s).replace(/[&<>]/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;'}[c])); }

function render() {
  renderHead(); renderTabs(); renderSvg(); renderBaselines(); renderResults(); renderEvents(); renderTimeSelect();
}

function renderHead() {
  const last = state.view.events[state.view.events.length - 1];
  $('#head-id').textContent = last ? last.eventId : '—';
}

function renderTimeSelect() {
  const sel = $('#time-select');
  const cur = state.atEvent;
  sel.innerHTML = '<option value="">（最新状态）</option>' +
    state.view.events.map(e => `<option value="${e.eventId}" ${e.eventId===cur?'selected':''}>${e.seq} ${e.type}</option>`).join('');
}

function renderTabs() {
  const segs = state.view.segments;
  state.seq = segs.some(s => s.value.seqNo === state.seq) ? state.seq :
    (segs[0] ? segs[0].value.seqNo : 1);
  $('#seg-tabs').innerHTML = segs.map(s =>
    `<button data-seq="${s.value.seqNo}" class="${s.value.seqNo===state.seq?'active':''}">段${s.value.seqNo} ${s.value.direction}</button>`
  ).join('');
  $('#seg-tabs').querySelectorAll('button').forEach(btn =>
    btn.onclick = () => { state.seq = +btn.dataset.seq; renderTabs(); renderSvg(); renderBaselines(); renderResults(); });
}

function renderSvg() {
  const qs = state.atEvent ? '&at=' + encodeURIComponent(state.atEvent) : '';
  const url = '/api/case/' + CASE + '/svg/' + state.seq + '?_=' + Date.now() + qs;
  // 用 data URL 之外的方式：直接取 svg 文本注入以便交互
  fetch(url).then(r => r.text()).then(svg => {
    $('#svg-object').outerHTML =
      `<div id="svg-object" class="svg" style="padding:0">${svg}</div>`;
    attachDrag();
  });
}

// ---------------- 拖动区间 ----------------
function attachDrag() {
  const wrap = $('#curve-wrap');
  const band = $('#win-band'), hl = $('#handle-l'), hr = $('#handle-r');
  const pct = x => Math.min(96, Math.max(4, (x - wrap.getBoundingClientRect().left) / wrap.clientWidth * 100));
  let mode = null;
  const down = m => e => {
    mode = m; e.preventDefault();
    window.addEventListener('mousemove', move);
    window.addEventListener('mouseup', up);
  };
  const move = e => {
    const x = pct(e.clientX);
    let l = parseFloat(hl.style.left || getComputedStyle(hl).left) / wrap.clientWidth * 100;
    let r = parseFloat(hr.style.left || getComputedStyle(hr).left) / wrap.clientWidth * 100;
    if (mode === 'l') l = Math.min(x, r - 2);
    if (mode === 'r') r = Math.max(x, l + 2);
    hl.style.left = l + '%'; hr.style.left = r + '%';
    band.style.left = l + '%'; band.style.width = (r - l) + '%';
    syncWindowInputs(l, r);
  };
  const up = () => { mode = null;
    window.removeEventListener('mousemove', move);
    window.removeEventListener('mouseup', up);
  };
  hl.addEventListener('mousedown', down('l'));
  hr.addEventListener('mousedown', down('r'));
  band.addEventListener('mousedown', down('band'));
  const seg = currentSeg();
  if (seg) {
    const pts = seg.value.points;
    const tLo = Math.min(...pts.map(p => p.temperature));
    const tHi = Math.max(...pts.map(p => p.temperature));
    window.__xToK = pctX => tLo + (tHi - tLo) * pctX / 100;
  }
  syncWindowInputs(parseFloat(hl.style.left || '30'), parseFloat(hr.style.left || '60'));
}

function syncWindowInputs(lPct, rPct) {
  if (!window.__xToK) return;
  $('#win-start').value = celsius(window.__xToK(lPct));
  $('#win-end').value = celsius(window.__xToK(rPct));
}

function currentSeg() {
  return state.view.segments.find(s => s.value.seqNo === state.seq);
}
function winFromInputs() {
  const c2k = c => parseFloat(c) + 273.15;
  return { startK: c2k($('#win-start').value), endK: c2k($('#win-end').value) };
}

// ---------------- 基线 / 结果表 ----------------
function statusTag(status) {
  if (!status) return '';
  if (status.startsWith('STALE')) return `<span class="tag warn">${esc(status)}</span>`;
  if (status === 'FAILED' || status.startsWith('CURRENT_REJECTED'))
    return `<span class="tag err">${esc(status)}</span>`;
  if (status.startsWith('CURRENT_ADOPTED')) return `<span class="tag ok">${esc(status)}</span>`;
  return `<span class="tag">${esc(status)}</span>`;
}

function renderBaselines() {
  const segId = currentSeg()?.value.segmentId;
  const rows = state.view.baselines.filter(b => b.value.segmentId === segId);
  $('#baseline-table tbody').innerHTML = rows.map(b => `
    <tr class="${b.value.accepted ? 'adopted' : ''} ${b.status.startsWith('STALE')?'stale':''}">
      <td><code>${b.id}</code><br><span class="hint">v${b.version} · ${b.createdEvent}</span></td>
      <td>${esc(b.value.method)}</td>
      <td>${num(b.value.slope, 6)}</td>
      <td>${num(b.value.intercept, 4)}</td>
      <td>${num(b.value.rmse, 5)}</td>
      <td>${b.value.anchorPoints}</td>
      <td>${statusTag(b.status)}</td>
      <td>
        <button onclick="decide('${b.id}','adopt')">采纳</button>
        <button class="secondary" onclick="decide('${b.id}','reject')">否定</button>
      </td>
    </tr>`).join('') || '<tr><td colspan="8" class="hint">该段暂无基线候选</td></tr>';
}

function renderResults() {
  const segId = currentSeg()?.value.segmentId;
  const rows = state.view.results.filter(r => r.value.segmentId === segId);
  $('#result-table tbody').innerHTML = rows.map(r => {
    const v = r.value;
    const errs = (v.errorSources || []).map(e => `<span class="tag warn">${esc(e)}</span>`).join('');
    const diag = (v.diagnostics || []).map(e => `<span class="tag err">${esc(e)}</span>`).join('');
    return `<tr class="${r.status.startsWith('STALE')?'stale':''} ${r.status==='FAILED'?'failed':''}">
      <td><code>${r.id}</code><br><span class="hint">v${r.version} · ${r.createdEvent}</span></td>
      <td>${esc(v.segmentId)}</td>
      <td>${esc(v.kind)}</td>
      <td>${v.onsetK && !Number.isNaN(v.onsetK) ? celsius(v.onsetK)+' °C' : '—'}</td>
      <td>${v.peakK && !Number.isNaN(v.peakK) ? celsius(v.peakK)+' °C' : '—'}</td>
      <td>${v.endsetK && !Number.isNaN(v.endsetK) ? celsius(v.endsetK)+' °C' : '—'}</td>
      <td>${v.enthalpyJg && !Number.isNaN(v.enthalpyJg) ? num(v.enthalpyJg,3) : '—'}</td>
      <td>±${num(v.uncertaintyJg,3)}</td>
      <td>${statusTag(r.status)}</td>
      <td>${errs}${diag}</td>
      <td><button class="secondary" onclick="provenance('${r.id}')">来源链</button></td>
    </tr>`;
  }).join('') || '<tr><td colspan="11" class="hint">该段暂无积分结果</td></tr>';
}

async function decide(id, action) {
  await api(`/api/case/${CASE}/baseline/${id}/${action}`, {
    method: 'POST', headers: {'Content-Type':'application/json'},
    body: JSON.stringify({ actor: 'web-reviewer', basis: currentBasis() })
  });
  await loadView();
}

function currentBasis() {
  const evs = state.view.events;
  return evs.length ? evs[evs.length - 1].eventId : null;
}

async function provenance(id) {
  const qs = state.atEvent ? '?at=' + encodeURIComponent(state.atEvent) : '';
  const data = await api('/api/result/' + id + '/provenance' + qs);
  $('#prov-title').textContent = id;
  $('#prov-body').textContent = JSON.stringify(data, null, 2);
  $('#prov-dialog').showModal();
}

// ---------------- 事件链 / 时间旅行 ----------------
function renderEvents() {
  $('#event-list').innerHTML = state.view.events.map(e => `
    <div class="event ${e.eventId===state.atEvent?'sel':''}" data-id="${e.eventId}">
      <div><span class="t">#${e.seq} ${esc(e.type)}</span>
        <span class="id">${e.eventId} · ${esc(e.actor)}</span></div>
      <pre>${esc(JSON.stringify(e.payload, null, 0))}</pre>
    </div>`).join('');
  $('#event-list').querySelectorAll('.event').forEach(el =>
    el.onclick = () => {
      state.atEvent = el.dataset.id === state.atEvent ? '' : el.dataset.id;
      loadView();
    });
}

// ---------------- 操作按钮 ----------------
$('#btn-replay').onclick = async () => {
  const out = await api('/api/replay', { method: 'POST' });
  state.atEvent = '';
  if (out.steps) renderReplaySteps(out.steps);
  await loadView();
};

$('#btn-refresh').onclick = () => loadView();
$('#time-select').onchange = e => { state.atEvent = e.target.value; loadView(); };

$('#btn-apply-tangent').onclick = async () => {
  const seg = currentSeg();
  const w = winFromInputs();
  const span = w.endK - w.startK;
  await api(`/api/case/${CASE}/tangent`, {
    method: 'POST', headers: {'Content-Type':'application/json'},
    body: JSON.stringify({
      seq: state.seq, segmentId: seg.value.segmentId,
      left: { startK: w.startK - span * 0.9, endK: w.startK - span * 0.2 },
      right: { startK: w.endK + span * 0.2, endK: w.endK + span * 0.9 },
      rationale: 'dragged in curve page', actor: 'web-reviewer', basis: currentBasis()
    })
  });
  await loadView();
};

$('#btn-empty').onclick = async () => {
  await api(`/api/case/${CASE}/empty`, {
    method: 'POST', headers: {'Content-Type':'application/json'},
    body: JSON.stringify({ seq: state.seq, window: winFromInputs(),
      actor: 'web-reviewer', basis: currentBasis() })
  });
  await loadView();
};

$('#btn-export').onclick = async () => {
  const text = await fetch('/api/export').then(r => r.text());
  const blob = new Blob([text], { type: 'application/json' });
  const a = document.createElement('a');
  a.href = URL.createObjectURL(blob);
  a.download = 'dsc-archive.json';
  a.click();
};

$('#file-import').onchange = async e => {
  const f = e.target.files[0];
  if (!f) return;
  await api('/api/import', { method: 'POST', body: await f.text() });
  state.atEvent = '';
  await loadView();
  alert('导入完成：身份、顺序与来源关系按归档重建');
};

function renderReplaySteps(steps) {
  const box = $('#replay-steps');
  box.innerHTML = '';
  steps.forEach((s, i) => setTimeout(() => {
    const d = document.createElement('div');
    d.className = 'step play';
    d.innerHTML = `<b>${i + 1}.</b> ${esc(s.title)} <code>${s.eventId}</code> — ${esc(s.judgement)}`;
    box.appendChild(d);
  }, i * 350));
}

// 90 秒短样例路径：自动重放 -> 浏览各段 -> 时间旅行 -> 打开来源链 -> 导出提示
$('#btn-autoplay').onclick = async () => {
  const say = m => {
    const d = document.createElement('div');
    d.className = 'step play';
    d.textContent = '▸ ' + m;
    $('#replay-steps').appendChild(d);
  };
  say('开始短样例路径：重放反例档案…');
  const out = await api('/api/replay', { method: 'POST' });
  if (out.steps) renderReplaySteps(out.steps);
  await new Promise(r => setTimeout(r, out.steps ? out.steps.length * 360 : 400));
  await loadView();
  const seqs = state.view.segments.map(s => s.value.seqNo);
  for (const sq of seqs.slice(0, 3)) {
    state.seq = sq; renderTabs(); renderSvg(); renderBaselines(); renderResults();
    say('查看段 ' + sq + '：并排基线、onset/peak/endset、误差来源');
    await new Promise(r => setTimeout(r, 2200));
  }
  const res = state.view.results.find(r => r.value.errorSources?.length);
  if (res) { provenance(res.id); say('从异常结果进入来源链：结果→基线→段→直接来源（含版本）'); }
  await new Promise(r => setTimeout(r, 2600));
  $('#prov-dialog').close();
  const old = state.view.events.find(e => e.type === 'BASELINE_DERIVED');
  if (old) { state.atEvent = old.eventId; await loadView();
    say('时间旅行回到 BASELINE_DERIVED：恢复当时结论，不被最新结果覆盖'); }
  await new Promise(r => setTimeout(r, 3000));
  state.atEvent = ''; await loadView();
  say('短样例路径完成（< 90 秒）。可点“导出归档”，删除本地数据库后再导入验证身份/顺序不变。');
};

loadView().catch(() => {
  $('#replay-steps').innerHTML =
    '<div class="step">数据库中还没有案例。点击「载入并重放离线案例包」开始。</div>';
});
