package dsc.web;

public final class Page {
    public static final String HTML = """
<!DOCTYPE html>
<html lang="zh">
<head>
<meta charset="utf-8">
<title>热转折案 · DSC 基线与相变积分审阅器</title>
<style>
 body{font:13px/1.5 -apple-system,"Helvetica Neue",Arial,sans-serif;margin:0;background:#101418;color:#dde3ea}
 header{padding:10px 16px;background:#1a2129;display:flex;gap:8px;align-items:center;flex-wrap:wrap}
 button{background:#2d6cdf;border:0;color:#fff;padding:6px 12px;border-radius:4px;cursor:pointer}
 button.ghost{background:#2a333d}
 h1{font-size:15px;margin:0 12px 0 0}
 .path{padding:6px 16px;background:#17301f;color:#9fe6b0;font-size:12px}
 .wrap{display:grid;grid-template-columns:1fr 340px;gap:10px;padding:10px 16px}
 .card{background:#171d24;border:1px solid #262f3a;border-radius:6px;padding:10px;margin-bottom:10px}
 .card h2{font-size:12px;text-transform:uppercase;letter-spacing:.06em;color:#8fa3b8;margin:0 0 8px}
 svg{width:100%;height:380px;background:#0c0f13;border-radius:4px}
 .ev{padding:4px 6px;border-bottom:1px solid #232c36;cursor:pointer}
 .ev:hover{background:#1d2630}
 .tag{display:inline-block;padding:1px 6px;border-radius:3px;font-size:11px;margin-right:4px}
 .WARN{background:#4a3a12;color:#ffd479}.BLOCK{background:#4a1616;color:#ff9a9a}.INFO{background:#14324a;color:#8fd0ff}
 .cand{cursor:pointer;opacity:.45}.cand.sel{opacity:1}
 .handle{cursor:ew-resize;fill:#ffb454}
 .src{font-family:ui-monospace,monospace;font-size:11px;color:#9fb2c8}
 table{border-collapse:collapse;width:100%}td,th{border-bottom:1px solid #232c36;padding:3px 6px;text-align:left;font-size:12px}
</style>
</head>
<body>
<header>
 <h1>热转折案 · DSC 基线 / 相变积分 / 扫描历史审阅器</h1>
 <button onclick="loadPack()">1 加载案例包</button>
 <button onclick="replay()">2 重放场景</button>
 <button class="ghost" onclick="refresh()">刷新</button>
</header>
<div class="path">九十秒样例路径：加载案例包 → 重放场景 → 在曲线上拖动 ev-melt 区间 → 并排切换基线候选 → 打开分支面板解决冲突 → 点击任一旧事件查看当时的依赖版本与结论。</div>
<div class="wrap">
 <div>
  <div class="card"><h2>曲线页 · 可拖区间 / 并排基线候选 / onset-peak-endset</h2><svg id="chart"></svg>
   <div id="legend" class="src"></div></div>
  <div class="card"><h2>事件详情 · 焓变与误差来源</h2><div id="detail">选择一个事件区间。</div></div>
 </div>
 <div>
  <div class="card"><h2>来源（分别存档 · 带版本）</h2><div id="sources" class="src"></div></div>
  <div class="card"><h2>分支 / 冲突</h2><div id="branches"></div></div>
  <div class="card"><h2>诊断（机器可读判断依据）</h2><div id="diags"></div></div>
  <div class="card"><h2>事件时间线（点击回到旧事件）</h2><div id="events"></div></div>
 </div>
</div>
<script>
let S=null, selEvent=null, showCands={};
const svg=document.getElementById('chart'), NS='http://www.w3.org/2000/svg';
function el(n,a){const e=document.createElementNS(NS,n);for(const k in a)e.setAttribute(k,a[k]);return e;}
async function api(p,m,b){const r=await fetch(p,{method:m||'GET',headers:{'Content-Type':'application/json'},body:b?JSON.stringify(b):undefined});return r.json();}
async function loadPack(){await api('/api/casepack/load','POST');await refresh();}
async function replay(){const r=await api('/api/scenario/replay','POST');console.log(r);await refresh();}
async function refresh(){S=await api('/api/state');draw();panels();}

function scales(){
 const pts=S.curve||[];let x0=1e9,x1=-1e9,y0=1e9,y1=-1e9;
 pts.forEach(p=>{x0=Math.min(x0,p[0]);x1=Math.max(x1,p[0]);y0=Math.min(y0,p[1]);y1=Math.max(y1,p[1]);});
 const W=svg.clientWidth||800,H=380,P=30;
 return {x:t=>P+(t-x0)/(x1-x0)*(W-2*P), y:v=>H-P-(v-y0)/(y1-y0)*(H-2*P), x0,x1,y0,y1,W,H,P};
}
function draw(){
 svg.innerHTML=''; if(!S||!S.curve)return;
 const sc=scales();
 // segment bands
 (S.segments||[]).forEach(s=>{
   const col=s.direction==='HEATING'?'rgba(45,108,223,.07)':s.direction==='COOLING'?'rgba(223,108,45,.07)':'rgba(120,120,120,.07)';
   svg.appendChild(el('rect',{x:sc.x(s.tMin),y:sc.P,width:sc.x(s.tMax)-sc.x(s.tMin),height:sc.H-2*sc.P,fill:col}));
   const t=el('text',{x:sc.x(s.tMin)+4,y:sc.P+12,fill:'#7c8ea3','font-size':10});
   t.textContent='seg'+s.index+' '+s.direction; svg.appendChild(t);
 });
 // curve
 let d='';S.curve.forEach((p,i)=>{d+=(i?'L':'M')+sc.x(p[0]).toFixed(1)+' '+sc.y(p[1]).toFixed(1);});
 svg.appendChild(el('path',{d,fill:'none',stroke:'#7fd4ff','stroke-width':1}));
 // events from tangents
 const tan=S.sources&&S.sources.tangents&&S.sources.tangents.payload;
 if(!tan)return;
 const derived=S.derived||[];
 tan.events.forEach(ev=>{
   const g=el('g',{});
   const x0=sc.x(ev.tStart),x1=sc.x(ev.tEnd);
   g.appendChild(el('rect',{x:x0,y:sc.P,width:x1-x0,height:sc.H-2*sc.P,
     fill:selEvent===ev.id?'rgba(255,180,84,.12)':'rgba(255,255,255,.03)',
     stroke:selEvent===ev.id?'#ffb454':'#3a4a5c','stroke-dasharray':'3 3'}));
   // draggable handles
   [['tStart',x0],['tEnd',x1]].forEach(([k,hx])=>{
     const h=el('rect',{class:'handle',x:hx-4,y:sc.P,width:8,height:sc.H-2*sc.P,rx:2});
     h.addEventListener('mousedown',e=>{e.preventDefault();
       const move=me=>{const r=svg.getBoundingClientRect();
         const t=sc.x0+(me.clientX-r.left-sc.P)/(sc.W-2*sc.P)*(sc.x1-sc.x0);
         ev[k]=Math.round(t*10)/10;draw();};
       const up=async me=>{document.removeEventListener('mousemove',move);document.removeEventListener('mouseup',up);
         await api('/api/interval','POST',{event:ev.id,tStart:ev.tStart,tEnd:ev.tEnd});await refresh();};
       document.addEventListener('mousemove',move);document.addEventListener('mouseup',up);});
     g.appendChild(h);
   });
   const lbl=el('text',{x:x0+4,y:sc.H-sc.P-6,fill:'#ffb454','font-size':10});
   lbl.textContent=ev.id+' ['+ev.tStart+','+ev.tEnd+'] °C';
   lbl.style.cursor='pointer';
   lbl.onclick=()=>{selEvent=ev.id;draw();panels();};
   g.appendChild(lbl);
   // baselines + markers for this event
   const bl=derived.find(x=>x.kind==='baselines'&&x.id===ev.id);
   const integ=derived.find(x=>x.kind==='integration'&&x.id===ev.id);
   if(bl&&bl.payload)bl.payload.forEach(c=>{
     const key=ev.id+'/'+c.id;
     if(!(key in showCands))showCands[key]=true;
     if(!showCands[key])return;
     let bd='';c.curve.forEach((p,i)=>{bd+=(i?'L':'M')+sc.x(p[0]).toFixed(1)+' '+sc.y(p[1]).toFixed(1);});
     const chosen=tan['chosenBaseline_'+ev.id]===c.id;
     const path=el('path',{d:bd,fill:'none','stroke-width':chosen?2.5:1.2,class:'cand'+(chosen?' sel':''),
       stroke:c.kind==='LINEAR'?'#8ef0a4':c.kind==='TANGENT'?'#ff9ad5':'#ffd479'});
     path.onclick=async()=>{await api('/api/baseline/choose','POST',{event:ev.id,baselineId:c.id});await refresh();};
     g.appendChild(path);
   });
   if(integ&&integ.payload&&integ.payload.length){
     const chosen=tan['chosenBaseline_'+ev.id];
     const r=integ.payload.find(q=>q.baselineId===chosen)||integ.payload[0];
     [['onsetC','#8ef0a4'],['peakC','#ff9ad5'],['endsetC','#ffd479']].forEach(([f,col])=>{
       if(r[f]==null)return;
       g.appendChild(el('line',{x1:sc.x(r[f]),x2:sc.x(r[f]),y1:sc.P,y2:sc.H-sc.P,stroke:col,'stroke-dasharray':'2 2'}));
       const tt=el('text',{x:sc.x(r[f])+2,y:sc.P+24,fill:col,'font-size':9});tt.textContent=f.replace('C','');g.appendChild(tt);
     });
   }
   svg.appendChild(g);
 });
 document.getElementById('legend').textContent='基线候选：绿=LINEAR 粉=TANGENT 黄=SIGMOIDAL（点击曲线选择，未选候选保留） · 拖动橙色手柄调整区间';
}
function panels(){
 const src=document.getElementById('sources');src.innerHTML='';
 if(S.sources)Object.entries(S.sources).forEach(([k,v])=>{
   src.innerHTML+='<div>'+k+' <b>v'+v.version+'</b> · id='+v.id+'</div>';
 });
 const dg=document.getElementById('diags');dg.innerHTML='';
 (S.diagnostics||[]).slice(-30).forEach(d=>{
   dg.innerHTML+='<div><span class="tag '+d.severity+'">'+d.code+'</span>'+d.message+'</div>';
 });
 const ev=document.getElementById('events');ev.innerHTML='';
 (S.events||[]).forEach(e=>{
   const div=document.createElement('div');div.className='ev';
   div.textContent='#'+e.seq+' '+e.type+' · '+e.ts.slice(11,19);
   div.onclick=async()=>{const v=await api('/api/events/'+e.seq+'/view');
     document.getElementById('detail').innerHTML='<pre class="src">'+JSON.stringify(v,null,1).slice(0,4000)+'</pre>';};
   ev.appendChild(div);
 });
 const br=document.getElementById('branches');br.innerHTML='';
 (S.branches||[]).forEach(b=>{
   const div=document.createElement('div');
   div.innerHTML='<div>'+b.id+' · '+b.status+'</div>';
   if(b.status==='OPEN')(b.options.options||[]).forEach(o=>{
     const btn=document.createElement('button');btn.textContent='采用 '+o.label;
     btn.onclick=async()=>{await api('/api/branch/resolve','POST',{id:b.id,chosen:o.label});await refresh();};
     div.appendChild(btn);
   });
   br.appendChild(div);
 });
 const det=document.getElementById('detail');
 if(selEvent&&S.derived){
   const integ=S.derived.find(x=>x.kind==='integration'&&x.id===selEvent);
   if(integ&&integ.payload){
     let h='<table><tr><th>基线候选</th><th>onset</th><th>peak</th><th>endset</th><th>ΔH J/g</th><th>方向</th></tr>';
     integ.payload.forEach(r=>{
       h+='<tr><td class="src">'+r.baselineId+'</td><td>'+fmt(r.onsetC)+'</td><td>'+fmt(r.peakC)+'</td><td>'
         +fmt(r.endsetC)+'</td><td>'+fmt(r.enthalpyJg)+'</td><td>'+r.direction+'/'+r.exoConvention+'</td></tr>';
       h+='<tr><td></td><td colspan="5" class="src">误差来源: '+(r.errorSources||[]).join(' · ')
         +(r.diagnostics&&r.diagnostics.length?' · 诊断: '+r.diagnostics.join(','):'')+'</td></tr>';
     });
     det.innerHTML=h+'</table><div class="src">状态: '+integ.status+' · 版本 v'+integ.version+'</div>';
   } else det.innerHTML='该事件无积分结果（可能为空区间）。';
 }
}
function fmt(v){return v==null?'—':(+v).toFixed(2);}
refresh();
</script>
</body>
</html>
""";
}
