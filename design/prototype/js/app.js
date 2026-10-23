/* ==========================================================================
   原型外壳：方案切换、页面切换、并排对比、右侧设计说明。
   ========================================================================== */

const SCHEMES = {
  paper: {
    key: 'paper',
    cls: 'scheme-paper',
    name: '纸墨',
    en: 'PAPER & INK',
    tagline: '就是金匮现在的样子，长出一本账',
    oneLine: '暖纸白 + 赤金点缀，宋体数字，1dp 描边，零投影。像一本装订好的账簿。',
    why: [
      '色值与圆角全部取自 <code>Theme.kt</code> 的 <code>LightColors</code>，账本与卡包一眼同源，不用维护第二套设计语言。',
      '分类色直接复用卡包已有的 <code>CardPalette</code> 八色（墨/赤金/松绿/靛蓝/朱砂/黛紫/石青/岩灰），环形图天生协调。',
      '大数字用宋体、正文用黑体，与金匮品牌字标一致；金额像账簿手写的，不像后台报表。',
      '零投影、只靠描边分层，浅色下最耐看，也最省电（OLED 白底面积小）。',
    ],
    risk: '气质偏安静。分类只有 8 色，超过 8 个分类时环形图区分度下降；「好看」更靠排版而非色彩，对信息密度要求高。',
    reuse: 'JkColors.LightColors + CardPalette + JkType.brand 宋体，复用度最高，Compose 侧几乎不用新增令牌。',
    swatches: ['#f7f6f3', '#ffffff', '#e6e3dc', '#1a1815', '#946616', '#3a3632', '#2f6b57', '#a8483a'],
  },
  vivid: {
    key: 'vivid',
    cls: 'scheme-vivid',
    name: '鲜活',
    en: 'VIVID DATA',
    tagline: '记账专属的一套，色彩承担信息',
    oneLine: '亮中性底 + 白瓷面板 + 柔和投影，每个分类都有自己的颜色，图表饱满有生气。',
    why: [
      '分类色板扩到 12 色，环形图、排行条、热力图靠颜色就能读懂，不用看文字。',
      '层次用柔和投影而不是描边，面板像浮在纸上，更接近主流记账 App 的手感，新用户零学习成本。',
      '首页概览用极淡的暖橙渐变，支出用朱红、收入用翠绿 —— 收支方向一眼分明。',
      '胶囊形 chip 和更大圆角让「记一笔」显得轻快，符合每天要打开好几次的工具属性。',
    ],
    risk: '与卡包现有的克制风格并存时可能像两个 App；投影多了在低端机上有过度绘制成本；12 色需要仔细调饱和度，否则容易俗艳。',
    reuse: '需要新增一套记账专用色板与 elevation 令牌，Theme.kt 要扩 JkColors（或另立 LedgerColors）。',
    swatches: ['#f4f5f7', '#ffffff', '#f2643c', '#17b890', '#4c8df6', '#e8507a', '#9b5de5', '#f5a623'],
  },
  ink: {
    key: 'ink',
    cls: 'scheme-ink',
    name: '墨金',
    en: 'DARK FINANCE',
    tagline: '深色不是反相，是另一套设计',
    oneLine: '暖墨黑 + 描金，等宽体数字，图表像私人银行终端。金匮深色语言的延伸。',
    why: [
      '取自 <code>Theme.kt</code> 的 <code>DarkColors</code>：#141210 底、#D4A857 金，与卡包深色模式完全一致。',
      '金额用等宽体（tabular），列表里的小数点严格对齐 —— 记账场景下这是实打实的可读性提升。',
      '概览卡加了一层极淡的金色径向光，深色下不刺眼但有质感；图表另配宝石色阶，避免深色下彩色发脏。',
      '夜间记账、睡前看报表的典型场景，深色是主场；OLED 上省电优势明显。',
    ],
    risk: '只适合当深色方案，不能单独作为产品的唯一面孔；金色用多了会显得浮夸，需要严格控制点缀面积。',
    reuse: 'JkColors.DarkColors + 新增图表宝石色阶，复用度高。可与方案 A 组成同一产品的浅色/深色两态。',
    swatches: ['#141210', '#1c1a17', '#2f2b26', '#f2eee7', '#d4a857', '#e0857a', '#7fc9a4', '#8fb4e8'],
  },
};

const ROADMAP = [
  ['0.6.0', '账本落地：账户 / 分类 / 流水 / 记一笔（含自绘键盘）/ 月度概览；DB 迁移到 v2，备份 schema 升到 2 并向后兼容。'],
  ['0.7.0', '统计：环形、分类排行、六月趋势、日历热力；预算与超支提醒；从卡包一键生成账户。'],
  ['0.8.0', '周期账单与订阅、借出借入与报销、多币种与手动汇率、小票照片附件（沿用卡片附件加密）。'],
  ['0.9.0', 'CSV 导入导出（账本单独导出，可选是否含金额）、按账户/分类/标签的搜索与筛选、账本独立体检。'],
  ['1.0.0', '性能与打磨：万条流水下的分页与聚合、卡包与账本的统一首页、动效收尾、真机适配。'],
];

const state = { scheme: 'paper', screen: 'home', compare: false };

function phoneHtml(schemeKey, screenKey) {
  const s = SCHEMES[schemeKey];
  const body = SCREENS[screenKey].build();
  return `<div class="viewport ${s.cls}"><div class="notch"></div>${body}</div>`;
}

function slotHtml(schemeKey) {
  const s = SCHEMES[schemeKey];
  const label = state.compare
    ? `<div class="slot-label">
         <span class="tagline">${s.en}</span>
         <h2>方案 ${schemeKey === 'paper' ? 'A' : schemeKey === 'vivid' ? 'B' : 'C'} · ${s.name}</h2>
         <p>${s.tagline}</p>
       </div>`
    : '';
  return `<div class="slot">${label}<div class="phone">${phoneHtml(schemeKey, state.screen)}</div>${
    state.compare ? `<div class="slot-label" style="max-width:340px"><p>${s.oneLine}</p></div>` : ''
  }</div>`;
}

function notesHtml() {
  const s = SCHEMES[state.scheme];
  const letter = state.scheme === 'paper' ? 'A' : state.scheme === 'vivid' ? 'B' : 'C';
  return `
    <h3>方案 ${letter} · ${s.en}</h3>
    <p style="color:var(--studio-ink);font-size:14px;line-height:1.7;margin-bottom:2px"><b>${s.name}</b> — ${s.tagline}</p>
    <p>${s.oneLine}</p>

    <div class="rule"></div>
    <h4>为什么这么设计</h4>
    <ul>${s.why.map((w) => `<li>${w}</li>`).join('')}</ul>

    <h4>要留意的</h4>
    <p>${s.risk}</p>

    <h4>与现有代码的关系</h4>
    <p>${s.reuse}</p>

    <div class="rule"></div>
    <h4>色板</h4>
    <div class="swatches">${s.swatches.map((c) => `<div class="sw" style="background:${c}"><span>${c}</span></div>`).join('')}</div>

    <div class="rule"></div>
    <h4>当前页面</h4>
    <p style="color:var(--studio-ink)">
      ${SCREENS[state.screen].title}
      <span style="color:var(--studio-ink-3)">· ${Object.keys(SCREENS).length} 个页面都用同一套结构渲染，只换方案变量。</span>
    </p>

    <div class="rule"></div>
    <h4>全量功能的版本规划</h4>
    ${ROADMAP.map(([v, t]) => `<div class="ver"><b>${v}</b><span>${t}</span></div>`).join('')}

    <div class="rule"></div>
    <h4>安全前提（不因记账而松动）</h4>
    <ul>
      <li>账本与卡包<b>共用一把锁</b>：同一份主密码、同一套生物识别/PIN，锁定时两边明文一起销毁。</li>
      <li>金额与分类存在 SQLCipher 整库加密内，<b>不再套字段级加密</b>，这样统计能在 SQL 层聚合，几万条流水也不卡。</li>
      <li>备注、账户名、小票附件继续走字段级 AES-GCM，与卡片备注同一套。</li>
      <li>依旧<b>不申请任何联网权限</b>：汇率手动录入，泄露库不比对，云同步不做。</li>
      <li>备份 schema 升到 2，旧备份（只有卡片）仍可导入；导出时可选择不含金额。</li>
    </ul>
  `;
}

function render() {
  const stage = document.getElementById('stage');
  const schemes = state.compare ? ['paper', 'vivid', 'ink'] : [state.scheme];
  stage.innerHTML = schemes.map(slotHtml).join('');

  document.getElementById('notes').innerHTML = notesHtml();
  document.body.classList.toggle('compare', state.compare);

  document.querySelectorAll('.seg#screenSeg button').forEach((b) =>
    b.setAttribute('aria-pressed', String(b.dataset.screen === state.screen)));
  document.querySelectorAll('.seg#schemeSeg button').forEach((b) =>
    b.setAttribute('aria-pressed', String(!state.compare && b.dataset.scheme === state.scheme)));
  document.getElementById('compareBtn').setAttribute('aria-pressed', String(state.compare));

  // 图表要在 DOM 就位后再画：颜色是从方案根节点的 CSS 变量里读的。
  stage.querySelectorAll('.viewport').forEach(mountCharts);
}

function buildControls() {
  const screenSeg = document.getElementById('screenSeg');
  screenSeg.innerHTML = Object.entries(SCREENS)
    .map(([k, v]) => `<button type="button" data-screen="${k}">${v.title}</button>`)
    .join('');
  screenSeg.addEventListener('click', (e) => {
    const b = e.target.closest('button[data-screen]');
    if (!b) return;
    state.screen = b.dataset.screen;
    render();
  });

  const schemeSeg = document.getElementById('schemeSeg');
  schemeSeg.innerHTML = Object.values(SCHEMES)
    .map((s, i) => `<button type="button" data-scheme="${s.key}">${'ABC'[i]} ${s.name}</button>`)
    .join('');
  schemeSeg.addEventListener('click', (e) => {
    const b = e.target.closest('button[data-scheme]');
    if (!b) return;
    state.scheme = b.dataset.scheme;
    state.compare = false;
    render();
  });

  document.getElementById('compareBtn').addEventListener('click', () => {
    state.compare = !state.compare;
    render();
  });

  // 手机内部的跳转：点底部导航、返回、流水行都能在原型里走一遍
  document.getElementById('stage').addEventListener('click', (e) => {
    const t = e.target.closest('[data-goto]');
    if (!t || !t.dataset.goto) return;
    state.screen = t.dataset.goto;
    render();
  });
}

buildControls();
render();
