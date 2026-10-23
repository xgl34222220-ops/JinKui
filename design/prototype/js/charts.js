/* ==========================================================================
   图表：全部手绘 SVG / CSS，不引第三方库。
   金匮没有联网权限，将来 Compose 侧也一样用 Canvas 自绘，
   所以这里画的每一张图都对应一段可以直接翻译过去的绘制逻辑。

   颜色不写死：从所属方案根节点的 CSS 变量里读，
   这样同一份数据在三套方案下自动换装，对比才公平。
   ========================================================================== */

function rampOf(el, i) {
  const cs = getComputedStyle(el);
  const v = cs.getPropertyValue(`--ramp-${((i - 1) % 10) + 1}`).trim();
  return v || '#8c867b';
}
function varOf(el, name, fallback = '#888') {
  return getComputedStyle(el).getPropertyValue(name).trim() || fallback;
}
function schemeOf(el) {
  const host = el.closest('.viewport') || el;
  if (host.classList.contains('scheme-vivid')) return 'vivid';
  if (host.classList.contains('scheme-ink')) return 'ink';
  return 'paper';
}

/* ---------------- 迷你走势线 ---------------- */
function sparkline(el, values) {
  const w = 300, h = 54, pad = 3;
  const max = Math.max(...values), min = Math.min(...values);
  const span = max - min || 1;
  const pts = values.map((v, i) => {
    const x = pad + (i * (w - pad * 2)) / (values.length - 1);
    const y = h - pad - ((v - min) / span) * (h - pad * 2);
    return [x, y];
  });
  const line = pts.map((p, i) => `${i ? 'L' : 'M'}${p[0].toFixed(1)} ${p[1].toFixed(1)}`).join(' ');
  const area = `${line} L${pts[pts.length - 1][0].toFixed(1)} ${h} L${pts[0][0].toFixed(1)} ${h} Z`;
  const accent = varOf(el, '--accent');
  const last = pts[pts.length - 1];
  el.innerHTML = `
    <svg viewBox="0 0 ${w} ${h}" width="100%" height="${h}" preserveAspectRatio="none" style="display:block">
      <defs>
        <linearGradient id="sg${Math.random().toString(36).slice(2, 7)}" x1="0" y1="0" x2="0" y2="1">
          <stop offset="0%" stop-color="${accent}" stop-opacity=".22"/>
          <stop offset="100%" stop-color="${accent}" stop-opacity="0"/>
        </linearGradient>
      </defs>
      <path d="${area}" fill="${accent}" opacity=".10"/>
      <path d="${line}" fill="none" stroke="${accent}" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round" opacity=".85"/>
      <circle cx="${last[0].toFixed(1)}" cy="${last[1].toFixed(1)}" r="2.6" fill="${accent}"/>
    </svg>`;
}

/* ---------------- 分类环形图 ---------------- */
function donut(el, segments, centerTop, centerSub) {
  const size = 148, r = 58, cx = size / 2, cy = size / 2;
  const C = 2 * Math.PI * r;
  const total = segments.reduce((s, x) => s + x.value, 0) || 1;
  const scheme = schemeOf(el);
  const thick = scheme === 'vivid' ? 19 : 13;
  const gap = scheme === 'vivid' ? 2.5 : 1.5;
  let acc = 0;
  const arcs = segments.map((s, i) => {
    const frac = s.value / total;
    const dash = Math.max(frac * C - gap, 0.5);
    const off = -acc * C + C / 4;
    acc += frac;
    const color = s.color || rampOf(el, i + 1);
    return `<circle cx="${cx}" cy="${cy}" r="${r}" fill="none" stroke="${color}" stroke-width="${thick}"
      stroke-dasharray="${dash.toFixed(2)} ${(C - dash).toFixed(2)}" stroke-dashoffset="${off.toFixed(2)}"
      stroke-linecap="${scheme === 'vivid' ? 'round' : 'butt'}" opacity="${scheme === 'paper' ? 0.92 : 1}"/>`;
  }).join('');
  const inkC = varOf(el, '--ink');
  const ink3 = varOf(el, '--ink-3');
  const numFont = scheme === 'paper' ? 'var(--serif-num)' : scheme === 'ink' ? 'var(--mono-num)' : 'var(--sans)';
  el.innerHTML = `
    <svg viewBox="0 0 ${size} ${size}" width="${size}" height="${size}" style="flex:none">
      <circle cx="${cx}" cy="${cy}" r="${r}" fill="none" stroke="${varOf(el, '--track')}" stroke-width="${thick}"/>
      ${arcs}
      <text x="${cx}" y="${cy - 4}" text-anchor="middle" fill="${ink3}" font-size="10.5" letter-spacing=".6">本月支出</text>
      <text x="${cx}" y="${cy + 17}" text-anchor="middle" fill="${inkC}" font-size="19" font-weight="600"
        font-family="${numFont}">${centerTop}</text>
      <text x="${cx}" y="${cy + 33}" text-anchor="middle" fill="${ink3}" font-size="10">${centerSub}</text>
    </svg>`;
}

/* ---------------- 六月收支柱状图 ---------------- */
function trendBars(el, data) {
  const w = 300, h = 118, padB = 20, padT = 8;
  const max = Math.max(...data.map((d) => Math.max(d.spend, d.income)));
  const group = (w - 8) / data.length;
  const bw = group * 0.26;
  const scheme = schemeOf(el);
  const spend = rampOf(el, 1);
  const income = scheme === 'paper' ? rampOf(el, 3) : scheme === 'ink' ? rampOf(el, 3) : '#17b890';
  const ink3 = varOf(el, '--ink-3');
  const track = varOf(el, '--track');
  let body = '';
  data.forEach((d, i) => {
    const x = 4 + i * group + group / 2;
    const hs = ((h - padB - padT) * d.spend) / max;
    const hi = ((h - padB - padT) * d.income) / max;
    const rr = scheme === 'vivid' ? bw / 2 : 2;
    body += `<rect x="${x - bw - 2}" y="${h - padB - hi}" width="${bw}" height="${hi}" rx="${rr}" fill="${income}" opacity=".38"/>`;
    body += `<rect x="${x + 2}" y="${h - padB - hs}" width="${bw}" height="${hs}" rx="${rr}" fill="${spend}"/>`;
    body += `<text x="${x}" y="${h - 5}" text-anchor="middle" font-size="10" fill="${ink3}">${d.m}</text>`;
  });
  el.innerHTML = `
    <svg viewBox="0 0 ${w} ${h}" width="100%" height="${h}" style="display:block">
      <line x1="4" y1="${h - padB}" x2="${w - 4}" y2="${h - padB}" stroke="${track}" stroke-width="1"/>
      ${body}
    </svg>
    <div style="display:flex;gap:14px;margin-top:8px;font-size:11px;color:${ink3}">
      <span style="display:inline-flex;align-items:center;gap:5px"><i style="width:8px;height:8px;border-radius:2px;background:${spend};display:inline-block"></i>支出</span>
      <span style="display:inline-flex;align-items:center;gap:5px"><i style="width:8px;height:8px;border-radius:2px;background:${income};opacity:.4;display:inline-block"></i>收入</span>
    </div>`;
}

/* ---------------- 预算环 ---------------- */
function budgetRing(el, pct, left) {
  const size = 116, r = 48, C = 2 * Math.PI * r, cx = size / 2;
  const scheme = schemeOf(el);
  const over = pct > 1;
  const color = over ? varOf(el, '--danger') : varOf(el, '--accent');
  const thick = scheme === 'vivid' ? 13 : 9;
  const dash = Math.min(pct, 1) * C;
  const numFont = scheme === 'paper' ? 'var(--serif-num)' : scheme === 'ink' ? 'var(--mono-num)' : 'var(--sans)';
  el.innerHTML = `
    <svg viewBox="0 0 ${size} ${size}" width="${size}" height="${size}" style="flex:none">
      <circle cx="${cx}" cy="${cx}" r="${r}" fill="none" stroke="${varOf(el, '--track')}" stroke-width="${thick}"/>
      <circle cx="${cx}" cy="${cx}" r="${r}" fill="none" stroke="${color}" stroke-width="${thick}"
        stroke-dasharray="${dash.toFixed(1)} ${(C - dash).toFixed(1)}" stroke-dashoffset="${C / 4}"
        stroke-linecap="${scheme === 'vivid' ? 'round' : 'butt'}" transform="rotate(-90 ${cx} ${cx})"/>
      <text x="${cx}" y="${cx - 2}" text-anchor="middle" font-size="22" font-weight="600"
        font-family="${numFont}" fill="${varOf(el, '--ink')}">${Math.round(pct * 100)}%</text>
      <text x="${cx}" y="${cx + 15}" text-anchor="middle" font-size="10" fill="${varOf(el, '--ink-3')}">已用</text>
    </svg>`;
}

/* ---------------- 日历热力 ---------------- */
function heatCell(el, v, max) {
  const p = v <= 0 ? 0 : Math.max(8, Math.round((v / max) * 100));
  if (p === 0) return `<div class="hc"></div>`;
  return `<div class="hc" style="background:color-mix(in srgb, var(--accent) ${p}%, var(--track))" title="¥${fmt(v, 0)}"></div>`;
}

/* ---------------- 挂载 ---------------- */
function mountCharts(root) {
  root.querySelectorAll('[data-chart]').forEach((el) => {
    const kind = el.dataset.chart;
    if (kind === 'spark') sparkline(el, DAILY_SPEND);
    else if (kind === 'trend') trendBars(el, TREND);
    else if (kind === 'ring') budgetRing(el, MONTH.spend / MONTH.budget);
    else if (kind === 'donut') {
      const segs = EXPENSE_CATS.filter((c) => c.amount > 0).slice(0, 8)
        .map((c, i) => ({ value: c.amount, color: schemeOf(el) === 'paper' ? rampOf(el, i + 1) : c.color }));
      donut(el, segs, '12,486', '较上月 ↓ 7.2%');
    } else if (kind === 'heat') {
      const max = Math.max(...DAILY_SPEND);
      const wd = ['一', '二', '三', '四', '五', '六', '日'].map((d) => `<div class="wd">${d}</div>`).join('');
      // 2026-10-01 是周四，前面补三个空格
      const blanks = Array.from({ length: 3 }, () => '<div class="hc" style="background:transparent"></div>').join('');
      el.innerHTML = wd + blanks + DAILY_SPEND.map((v) => heatCell(el, v, max)).join('');
    }
  });
}
