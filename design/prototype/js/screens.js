/* ==========================================================================
   六个页面的结构。三套方案共用同一份结构 —— 差异全部由 CSS 变量承担，
   这样对比的是「同一套信息在三种视觉语言下好不好看」，而不是排版取巧。

   页面清单（对应将来的 Screen.* 与版本规划）：
     home     账本首页：本月概览 + 流水        → 0.6.0
     entry    记一笔：分类 + 金额键盘          → 0.6.0
     stats    统计：环形 / 排行 / 趋势 / 热力  → 0.7.0
     accounts 账户：净资产 + 从卡包一键建户     → 0.7.0
     budget   预算 · 周期 · 借出报销            → 0.8.0
     detail   账单详情：小票附件 + 多币种       → 0.8.0
   ========================================================================== */

const acct = (id) => ACCOUNTS.find((a) => a.id === id) || ACCOUNTS[0];
const catOf = (key) => CAT_BY_KEY[key] || CAT_BY_KEY.other;

function statusbar() {
  return `<div class="statusbar">
      <span class="num" style="font-size:13.5px;font-weight:600">9:41</span>
      <span class="sb-right">${SB_SIGNAL}${SB_WIFI}${SB_BATT}</span>
    </div>`;
}

function catIcon(cat, size = 20, cls = '') {
  return `<div class="cat-icon ${cls}" style="--cat:${cat.color}">${icon(cat.icon, size)}</div>`;
}

/** 底部导航。卡包与账本同级 —— 一次解锁，两边都在。 */
function bottomNav(active) {
  const items = [
    { k: 'cards', label: '卡包', ic: 'cards' },
    { k: 'book', label: '账本', ic: 'book' },
    { k: 'fab', label: '', ic: 'plus' },
    { k: 'pie', label: '统计', ic: 'pie' },
    { k: 'gear', label: '我的', ic: 'gear' },
  ];
  return `<nav class="bottomnav">${items.map((it) => it.k === 'fab'
    ? `<span class="navfab-slot"><button class="navfab" type="button" data-goto="entry" aria-label="记一笔">${icon('plus', 24)}</button></span>`
    : `<button class="navitem" type="button" data-goto="${it.k === 'cards' ? '' : it.k === 'book' ? 'home' : it.k === 'pie' ? 'stats' : 'budget'}"
        aria-current="${active === it.k}">${icon(it.ic, 21)}<span>${it.label}</span></button>`).join('')}</nav>`;
}

/* ============================ 首页 ============================ */
function screenHome() {
  const overBudget = EXPENSE_CATS.filter((c) => {
    const b = BUDGETS.find((x) => x.cat === c.key);
    return b && b.used > b.limit;
  });
  const days = DAYS.map((day) => `
    <div>
      <div class="day-head">
        <span class="d">${day.label} · ${day.weekday}</span>
        <span class="sum">支 ${fmt(day.spend)}${day.income ? ' · 收 ' + fmt(day.income) : ''}</span>
      </div>
      <div class="panel flush" style="padding:0 var(--sp15)">
        ${day.items.map((it) => {
          const c = catOf(it.cat);
          const a = acct(it.acct);
          const income = it.amount > 0;
          return `<div class="txn" data-goto="detail">
            ${catIcon(c)}
            <div class="body">
              <div class="t">${it.note}</div>
              <div class="s">
                <span>${c.label}</span><span style="opacity:.45">·</span>
                <span>${a.name}${it.tail ? ' ••' + it.tail : ''}</span>
                ${it.recur ? `<span style="opacity:.45">·</span><span>周期</span>` : ''}
                ${it.tag ? `<span class="pill gray" style="padding:1px 6px">${it.tag}</span>` : ''}
              </div>
            </div>
            <div class="amt">
              <div class="a ${income ? 'pos' : 'spend'}">${income ? '+' : '-'}${CUR_SYM[it.cur || 'CNY']}${fmt(Math.abs(it.amount))}</div>
              <div class="x">${it.time}${it.cur ? ' · 折 ¥' + fmt(it.amount * -1 * it.rate, 0) : ''}</div>
            </div>
          </div>`;
        }).join('')}
      </div>
    </div>`).join('');

  return `<div class="scroller">${statusbar()}
  <div class="page">
    <div class="appbar">
      <div class="title">
        <h1>账本</h1>
        <span class="sub">已记 ${MONTH.days} 天</span>
      </div>
      <div class="acts">
        <button class="iconbtn" type="button">${icon('search', 20)}</button>
        <button class="iconbtn" type="button">${icon('calendar', 20)}</button>
        <button class="iconbtn" type="button">${icon('more', 20)}</button>
      </div>
    </div>

    <section class="hero">
      <div class="hero-top">
        <span class="hero-month">${MONTH.label} ${icon('down', 15)}</span>
        <span class="pill ${overBudget.length ? 'warn' : 'ok'}">${overBudget.length ? overBudget.length + ' 项超预算' : '预算内'}</span>
      </div>
      <div class="hero-kicker">本月支出</div>
      <div class="hero-amount amount-hero num">
        <span class="cur">¥</span>${fmt(MONTH.spend)}
      </div>
      <div class="hero-split">
        <div class="cell"><span class="k">收入</span>${moneyHtml(MONTH.income, 'pos', '¥', 'amount-sm')}</div>
        <div class="cell"><span class="k">结余</span>${moneyHtml(MONTH.balance, '', '¥', 'amount-sm')}</div>
        <div class="cell"><span class="k">日均</span>${moneyHtml(MONTH.dayAvg, 'dim', '¥', 'amount-sm')}</div>
      </div>
      <div class="spark" data-chart="spark"></div>
    </section>

    <div class="sec-label">预算进度<span class="more" data-goto="budget">管理</span></div>
    <div class="panel" style="padding:var(--sp15) var(--sp2)">
      <div style="display:flex;align-items:baseline;justify-content:space-between;margin-bottom:10px">
        <span style="font-size:13px;color:var(--ink-2)">总预算 <b class="num" style="color:var(--ink);font-weight:600">¥${fmt(MONTH.budget, 0)}</b></span>
        <span class="num" style="font-size:12.5px;color:var(--ink-3)">剩 ¥${fmt(MONTH.budget - MONTH.spend)}</span>
      </div>
      <div class="progress"><i style="width:${Math.min(100, (MONTH.spend / MONTH.budget) * 100)}%"></i></div>
      ${overBudget.map((c) => {
        const b = BUDGETS.find((x) => x.cat === c.key);
        return `<div style="display:flex;align-items:center;gap:8px;margin-top:12px">
          ${catIcon(c, 16, 'sm')}
          <span style="font-size:12.5px;color:var(--ink-2);flex:1">「${c.label}」已超 ¥${fmt(b.used - b.limit)}</span>
          <span class="pill warn">${Math.round((b.used / b.limit) * 100)}%</span>
        </div>`;
      }).join('')}
    </div>

    <div class="sec-label">最近流水<span class="more">全部 ${icon('right', 12)}</span></div>
    ${days}
  </div></div>
  ${bottomNav('book')}`;
}

/* ============================ 记一笔 ============================ */
function screenEntry() {
  const cats = EXPENSE_CATS.slice(0, 10);
  const chosen = catOf('food');
  const keys = ['7', '8', '9', 'back', '4', '5', '6', 'plus', '1', '2', '3', 'minus', '0', '00', '.', 'ok'];
  return `<div class="scroller">${statusbar()}
  <div class="page">
    <div class="appbar">
      <div class="title" style="align-items:center">
        <button class="iconbtn" type="button" data-goto="home">${icon('back', 20)}</button>
        <h1 style="font-size:19px">记一笔</h1>
      </div>
      <div class="acts">
        <button class="iconbtn" type="button">${icon('clock', 20)}</button>
        <button class="iconbtn" type="button">${icon('more', 20)}</button>
      </div>
    </div>

    <div class="jseg" style="margin-bottom:var(--sp1)">
      <button type="button" aria-pressed="true">支出</button>
      <button type="button" aria-pressed="false">收入</button>
      <button type="button" aria-pressed="false">转账</button>
    </div>

    <div class="entry-amount">
      <div style="display:flex;align-items:center;gap:8px">
        ${catIcon(chosen, 18, 'sm')}
        <span style="font-size:14px;font-weight:600">${chosen.label}</span>
        <span class="muted" style="font-size:12.5px">· 今天 10月23日</span>
        <span style="flex:1"></span>
        <span class="pill gray">${icon('edit', 11)} 改分类</span>
      </div>
      <div class="row">
        <span class="big amount-hero num"><span class="cur">¥</span>38.50</span>
        <span class="caret"></span>
      </div>
      <div style="font-size:12.5px;color:var(--ink-3)">公司楼下 · 拉面　<span style="opacity:.5">|</span>　支付宝</div>
    </div>

    <div class="sec-label" style="margin-top:var(--sp2)">常用分类<span class="more">全部</span></div>
    <div class="catgrid">
      ${cats.map((c, i) => `<button class="catcell" type="button" aria-pressed="${i === 0}">
          <span class="cat-icon" style="--cat:${c.color}">${icon(c.icon, 21)}</span>
          <span>${c.label}</span>
        </button>`).join('')}
    </div>

    <div class="entry-fields panel flush" style="padding:0 var(--sp2)">
      <div class="frow"><span class="k">账户</span><span class="v">${icon('ewallet', 15)} 支付宝 ${icon('right', 13)}</span></div>
      <div class="frow"><span class="k">日期</span><span class="v">今天 10月23日 周四 ${icon('right', 13)}</span></div>
      <div class="frow"><span class="k">标签</span><span class="v placeholder">通勤、报销… ${icon('plus', 13)}</span></div>
      <div class="frow"><span class="k">小票</span><span class="v placeholder">${icon('camera', 15)} 拍照或选图</span></div>
      <div class="frow"><span class="k">报销</span><span class="v">不计入 ${icon('right', 13)}</span></div>
    </div>

    <div class="keypad">
      ${keys.map((k) => {
        if (k === 'ok') return `<button class="key ok" type="button">保存</button>`;
        if (k === 'back') return `<button class="key fn" type="button">${icon('back', 19)}</button>`;
        if (k === 'plus') return `<button class="key fn" type="button">${icon('plus', 18)}</button>`;
        if (k === 'minus') return `<button class="key fn" type="button" style="font-size:20px">−</button>`;
        return `<button class="key" type="button">${k}</button>`;
      }).join('')}
    </div>
    <p style="font-size:11.5px;color:var(--ink-3);line-height:1.7;margin:var(--sp1) 2px 0">
      键盘自绘，不经过系统输入法 —— 与金匮的 PIN 键盘同一套做法，金额不会被第三方键盘记录。
    </p>
  </div></div>`;
}

/* ============================ 统计 ============================ */
function screenStats() {
  const top = EXPENSE_CATS.filter((c) => c.amount > 0).slice(0, 6);
  const maxAmt = top[0].amount;
  const total = EXPENSE_CATS.reduce((s, c) => s + c.amount, 0);
  return `<div class="scroller">${statusbar()}
  <div class="page">
    <div class="appbar">
      <div class="title"><h1>统计</h1><span class="sub">2026年10月</span></div>
      <div class="acts">
        <button class="iconbtn" type="button">${icon('filter', 20)}</button>
        <button class="iconbtn" type="button">${icon('download', 20)}</button>
      </div>
    </div>

    <div class="jseg" style="margin-bottom:var(--sp15)">
      <button type="button" aria-pressed="true">月</button>
      <button type="button" aria-pressed="false">年</button>
      <button type="button" aria-pressed="false">自定义</button>
    </div>
    <div class="chiprow" style="margin-bottom:var(--sp2)">
      <span class="jchip" aria-pressed="true">支出 ¥${fmt(total)}</span>
      <span class="jchip" aria-pressed="false">收入 ¥${fmt(MONTH.income)}</span>
      <span class="jchip" aria-pressed="false">全部账户</span>
      <span class="jchip" aria-pressed="false">全部分类</span>
    </div>

    <div class="panel">
      <div class="donut-wrap">
        <div data-chart="donut"></div>
        <div class="legend">
          ${top.slice(0, 5).map((c, i) => `<div class="li">
              <i class="dot" style="--bar-cat:${c.color};--bar-ramp:var(--ramp-${i + 1})"></i>
              <span class="nm">${c.label}</span>
              <span class="pc">${((c.amount / total) * 100).toFixed(1)}%</span>
            </div>`).join('')}
        </div>
      </div>
    </div>

    <div class="sec-label">分类排行</div>
    <div class="panel">
      <div class="rank">
        ${top.map((c, i) => `<div class="ri">
            <span class="num" style="font-size:12px;color:var(--ink-3)">${i + 1}</span>
            <span class="nm">${c.label}<span class="muted" style="font-size:11.5px;margin-left:6px">${c.count} 笔</span></span>
            <span class="amt">¥${fmt(c.amount)}</span>
            <div class="bar progress thin"><i class="cat" style="width:${(c.amount / maxAmt) * 100}%;--bar-cat:${c.color};--bar-ramp:var(--ramp-${i + 1})"></i></div>
          </div>`).join('')}
      </div>
    </div>

    <div class="sec-label">近六个月</div>
    <div class="panel"><div data-chart="trend"></div></div>

    <div class="sec-label">这个月</div>
    <div class="stat-grid">
      <div class="stat-cell"><span class="k">日均支出</span><span class="v">¥${fmt(MONTH.dayAvg, 0)}</span></div>
      <div class="stat-cell"><span class="k">最高一天</span><span class="v">¥${fmt(MONTH.maxDay.v, 0)}</span></div>
      <div class="stat-cell"><span class="k">记账笔数</span><span class="v">143</span></div>
    </div>

    <div class="sec-label">每日支出热力<span class="more">10月</span></div>
    <div class="panel"><div class="heat" data-chart="heat"></div></div>
  </div></div>
  ${bottomNav('pie')}`;
}

/* ============================ 账户 ============================ */
function screenAccounts() {
  const groups = [...new Set(ACCOUNTS.map((a) => a.group))];
  const liability = ACCOUNTS.filter((a) => a.balance < 0).reduce((s, a) => s + Math.abs(a.balance), 0);
  return `<div class="scroller">${statusbar()}
  <div class="page">
    <div class="appbar">
      <div class="title"><h1>账户</h1><span class="sub">${ACCOUNTS.length} 个</span></div>
      <div class="acts">
        <button class="iconbtn" type="button">${icon('swap', 20)}</button>
        <button class="iconbtn" type="button">${icon('plus', 20)}</button>
      </div>
    </div>

    <section class="networth">
      <div class="hero-kicker">净资产（折合人民币）</div>
      <div class="amount-hero num" style="font-size:38px"><span class="cur">¥</span>${fmt(MONTH.netWorthCny)}</div>
      <div class="hero-split" style="margin-top:var(--sp2)">
        <div class="cell"><span class="k">资产</span>${moneyHtml(MONTH.netWorthCny + liability, '', '¥', 'amount-sm')}</div>
        <div class="cell"><span class="k">负债</span>${moneyHtml(-liability, 'spend', '¥', 'amount-sm')}</div>
        <div class="cell"><span class="k">本月变动</span>${moneyHtml(4820.5, 'pos', '+¥', 'amount-sm')}</div>
      </div>
      <div style="display:flex;gap:6px;flex-wrap:wrap;margin-top:var(--sp15)">
        <span class="pill gray">4 种币种</span>
        <span class="pill gray">汇率手动更新 · 不联网</span>
      </div>
    </section>

    <div class="chiprow" style="margin:var(--sp2) 0 var(--sp05)">
      <span class="jchip" aria-pressed="true">全部</span>
      <span class="jchip" aria-pressed="false">储蓄</span>
      <span class="jchip" aria-pressed="false">信用</span>
      <span class="jchip" aria-pressed="false">虚拟资产</span>
      <span class="jchip" aria-pressed="false">境外</span>
    </div>

    ${groups.map((g) => `
      <div class="sec-label">${g}</div>
      <div class="panel flush" style="padding:0 var(--sp2)">
        ${ACCOUNTS.filter((a) => a.group === g).map((a) => `
          <div class="acct">
            <div class="cat-icon" style="--cat:${a.kind === 'credit' ? '#e8507a' : a.kind === 'crypto' ? '#00b8d9' : a.kind === 'foreign' ? '#4c8df6' : a.kind === 'invest' ? '#17b890' : '#8a6a4f'}">${icon(a.icon, 20)}</div>
            <div class="body">
              <div class="n">${a.name}${a.tail ? `<span class="muted num" style="font-size:12px">•••• ${a.tail}</span>` : ''}</div>
              <div class="m">
                ${a.note ? `<span class="pill" style="padding:1px 6px">${icon('cards', 10)} ${a.note}</span>` : `<span>${a.kind === 'credit' ? '账单日 ' + a.billDay + ' 日 · 还款日 ' + a.dueDay + ' 日' : '手动账户'}</span>`}
              </div>
              ${a.kind === 'credit' ? `<div style="margin-top:8px"><div class="progress thin"><i style="width:${(a.used / a.limit) * 100}%"></i></div>
                <div style="font-size:11px;color:var(--ink-3);margin-top:5px" class="num">已用 ¥${fmt(a.used, 0)} / 额度 ¥${fmt(a.limit, 0)}</div></div>` : ''}
            </div>
            <div class="bal">
              <div class="a ${a.balance < 0 ? 'spend' : ''}">${a.balance < 0 ? '-' : ''}${CUR_SYM[a.cur]}${fmt(Math.abs(a.balance), a.cur === 'KRW' ? 0 : 2)}</div>
              <div class="b">${a.cur}${a.rate ? ' · ≈¥' + fmt(a.balance * a.rate, 0) : ''}</div>
            </div>
          </div>`).join('')}
      </div>`).join('')}

    <div class="panel" style="margin-top:var(--sp2);display:flex;align-items:center;gap:var(--sp15)">
      <div class="cat-icon" style="--cat:#946616">${icon('cards', 20)}</div>
      <div style="flex:1;min-width:0">
        <div style="font-size:14px;font-weight:600">从卡包生成账户</div>
        <div style="font-size:11.5px;color:var(--ink-3);margin-top:4px;line-height:1.6">
          已识别 3 张卡可作为账户：招行 ••5972、招行信用卡 ••3319、Dupay U 卡 ••7741
        </div>
      </div>
      <span class="pill">${icon('right', 11)}</span>
    </div>
  </div></div>
  ${bottomNav('')}`;
}

/* ============================ 预算 · 周期 · 借贷 ============================ */
function screenBudget() {
  return `<div class="scroller">${statusbar()}
  <div class="page">
    <div class="appbar">
      <div class="title"><h1 style="font-size:22px">预算与周期</h1></div>
      <div class="acts">
        <button class="iconbtn" type="button">${icon('bell', 20)}</button>
        <button class="iconbtn" type="button">${icon('plus', 20)}</button>
      </div>
    </div>

    <div class="panel">
      <div class="budget-ring">
        <div data-chart="ring"></div>
        <div style="flex:1;min-width:0">
          <div class="hero-kicker">10 月总预算</div>
          <div class="amount-lg num" style="margin-bottom:6px"><span class="cur">¥</span>${fmt(MONTH.budget, 0)}</div>
          <div style="font-size:12.5px;color:var(--ink-2)" class="num">
            还剩 ¥${fmt(MONTH.budget - MONTH.spend)} · 距月末 8 天
          </div>
          <div style="font-size:11.5px;color:var(--ink-3);margin-top:8px;line-height:1.6">
            按当前节奏，本月预计支出 ¥16,290，将超预算 ¥290。
          </div>
        </div>
      </div>
    </div>

    <div class="sec-label">分类预算<span class="more">${BUDGETS.length} 项</span></div>
    <div class="panel flush" style="padding:0 var(--sp2)">
      ${BUDGETS.map((b) => {
        const c = catOf(b.cat);
        const pct = b.used / b.limit;
        const over = pct > 1;
        return `<div class="budget-row">
          <div class="top">
            <span class="nm">${catIcon(c, 16, 'sm')} ${c.label}</span>
            <span class="fig ${over ? '' : 'muted'}" style="${over ? 'color:var(--danger)' : ''}">¥${fmt(b.used)} / ${fmt(b.limit, 0)}</span>
          </div>
          <div class="progress thin"><i class="${over ? 'warn' : 'cat'}" style="width:${Math.min(100, pct * 100)}%;--bar-cat:${c.color};--bar-ramp:var(--ramp-${BUDGETS.indexOf(b) + 1})"></i></div>
          <div class="foot">${over
            ? `已超支 ¥${fmt(b.used - b.limit)} · ${Math.round(pct * 100)}%`
            : `还剩 ¥${fmt(b.limit - b.used)} · 每天可用 ¥${fmt((b.limit - b.used) / 8)}`}</div>
        </div>`;
      }).join('')}
    </div>

    <div class="sec-label">周期账单<span class="more">订阅管理</span></div>
    <div class="panel flush" style="padding:0 var(--sp2)">
      ${RECURRING.map((r) => {
        const c = catOf(r.cat);
        return `<div class="recur">
          ${catIcon(c, 18, 'sm')}
          <div class="body">
            <div class="n">${r.name}</div>
            <div class="m">${r.cycle} · ${acct(r.acct).name} · ${r.next}</div>
          </div>
          <div class="a">¥${fmt(r.amount, 0)}<small>${r.state}</small></div>
        </div>`;
      }).join('')}
    </div>

    <div class="sec-label">借出 · 借入 · 报销<span class="more">全部</span></div>
    <div class="panel flush" style="padding:0 var(--sp2)">
      ${DEBTS.map((d) => `<div class="recur">
          <div class="cat-icon sm" style="--cat:${d.kind === 'out' ? '#f5a623' : d.kind === 'in' ? '#17b890' : '#e8507a'}">${icon('debt', 17)}</div>
          <div class="body">
            <div class="n">${d.name}</div>
            <div class="m">${d.date} · ${d.note}</div>
          </div>
          <div class="a">${d.kind === 'borrow' ? '-' : ''}¥${fmt(d.amount, 0)}<small>${d.state}</small></div>
        </div>`).join('')}
    </div>
  </div></div>
  ${bottomNav('gear')}`;
}

/* ============================ 账单详情 ============================ */
function screenDetail() {
  const c = catOf('food');
  const a = acct('a4');
  return `<div class="scroller">${statusbar()}
  <div class="page">
    <div class="appbar">
      <div class="title" style="align-items:center">
        <button class="iconbtn" type="button" data-goto="home">${icon('back', 20)}</button>
        <h1 style="font-size:19px">账单详情</h1>
      </div>
      <div class="acts">
        <button class="iconbtn" type="button">${icon('copy', 19)}</button>
        <button class="iconbtn" type="button">${icon('more', 20)}</button>
      </div>
    </div>

    <div class="detail-head">
      <div style="display:flex;justify-content:center">${catIcon(c, 26)}</div>
      <div style="font-size:13px;color:var(--ink-2);margin-top:10px">${c.label} · 和同事聚餐</div>
      <div class="amt amount-hero num spend" style="font-size:38px">-<span class="cur">¥</span>186.00</div>
      <div style="display:flex;gap:6px;justify-content:center;margin-top:10px">
        <span class="pill warn">${icon('receipt', 10)} 报销中</span>
        <span class="pill gray">AA · 4 人</span>
      </div>
    </div>

    <div class="panel flush" style="padding:0 var(--sp2)">
      <div class="kv"><span class="k">账户</span><span class="v">${icon('card', 15)} ${a.name} •••• ${a.tail}</span></div>
      <div class="kv"><span class="k">时间</span><span class="v num">2026-10-22 20:30</span></div>
      <div class="kv"><span class="k">币种</span><span class="v">CNY ¥</span></div>
      <div class="kv"><span class="k">标签</span><span class="v"><span class="pill gray">聚餐</span><span class="pill gray">同事</span></span></div>
      <div class="kv"><span class="k">报销</span><span class="v" style="color:var(--accent-ink)">已提交 · 预计 10月28日到账</span></div>
      <div class="kv"><span class="k">备注</span><span class="v dim" style="max-width:190px;text-align:right">季度团建，先垫付，财务走 OA</span></div>
    </div>

    <div class="sec-label">小票附件<span class="more">${icon('camera', 12)} 添加</span></div>
    <div class="panel">
      <div class="receipt">${icon('receipt', 26)}<span>发票_1022.pdf · 218 KB · 已加密</span></div>
      <div style="display:flex;align-items:center;gap:8px;margin-top:var(--sp15);font-size:11.5px;color:var(--ink-3)">
        ${icon('lock', 13)} 附件与卡包证件照同一套加密：字段级 AES-GCM，落盘即密文。
      </div>
    </div>

    <div class="sec-label">同类最近</div>
    <div class="panel flush" style="padding:0 var(--sp2)">
      ${[
        { n: '日料 · 单人', d: '10月18日', v: 128.00 },
        { n: '火锅 · 部门聚餐', d: '10月9日', v: 246.00 },
        { n: '面馆', d: '10月4日', v: 32.00 },
      ].map((x) => `<div class="recur">
          ${catIcon(c, 16, 'sm')}
          <div class="body"><div class="n" style="font-size:13.5px">${x.n}</div><div class="m">${x.d}</div></div>
          <div class="a" style="font-size:13.5px">-¥${fmt(x.v)}</div>
        </div>`).join('')}
    </div>

    <div class="btnrow">
      <button class="btn ghost" type="button">${icon('edit', 17)} 编辑</button>
      <button class="btn danger" type="button">${icon('trash', 17)} 删除</button>
    </div>
  </div></div>`;
}

const SCREENS = {
  home: { title: '首页', build: screenHome },
  entry: { title: '记一笔', build: screenEntry },
  stats: { title: '统计', build: screenStats },
  accounts: { title: '账户', build: screenAccounts },
  budget: { title: '预算', build: screenBudget },
  detail: { title: '详情', build: screenDetail },
};
