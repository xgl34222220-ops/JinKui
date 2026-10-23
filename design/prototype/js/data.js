/* ==========================================================================
   原型用的示例数据。金额、分类、账户都按真实个人财务的量级写，
   这样三套方案比的是「信息密度撑起来之后好不好看」，而不是空壳。

   数据结构与将来 Kotlin 侧的 data class 一一对应：
     Account / Category / Transaction / Budget / Recurring / Debt
   ========================================================================== */

/** 分类色。方案 A 会忽略它改用 CardPalette 八色，方案 B/C 直接用。 */
const EXPENSE_CATS = [
  { key: 'food',     label: '餐饮', icon: 'food',     color: '#f2643c', amount: 2148.60, count: 62 },
  { key: 'transit',  label: '交通', icon: 'transit',  color: '#4c8df6', amount: 686.40,  count: 34 },
  { key: 'shopping', label: '购物', icon: 'shopping', color: '#e8507a', amount: 3260.00, count: 18 },
  { key: 'home',     label: '居住', icon: 'home',     color: '#9b5de5', amount: 4500.00, count: 3 },
  { key: 'fun',      label: '娱乐', icon: 'fun',      color: '#00b8d9', amount: 612.30,  count: 9 },
  { key: 'medical',  label: '医疗', icon: 'medical',  color: '#17b890', amount: 268.00,  count: 2 },
  { key: 'study',    label: '教育', icon: 'study',    color: '#f5a623', amount: 399.00,  count: 2 },
  { key: 'phone',    label: '通讯', icon: 'phone',    color: '#7c8798', amount: 129.00,  count: 2 },
  { key: 'gift',     label: '人情', icon: 'gift',     color: '#d9598a', amount: 600.00,  count: 2 },
  { key: 'pet',      label: '宠物', icon: 'pet',      color: '#8a6a4f', amount: 186.50,  count: 4 },
  { key: 'cloth',    label: '服饰', icon: 'cloth',    color: '#b06ae0', amount: 340.00,  count: 2 },
  { key: 'daily',    label: '日用', icon: 'daily',    color: '#5d8f6a', amount: 208.50,  count: 7 },
  { key: 'travel',   label: '旅行', icon: 'travel',   color: '#2f9e8f', amount: 148.00,  count: 1 },
  { key: 'other',    label: '其他', icon: 'spark',    color: '#98a0ac', amount: 0,       count: 0 },
];

const INCOME_CATS = [
  { key: 'salary',  label: '工资', icon: 'salary',  color: '#17b890', amount: 24000.00, count: 1 },
  { key: 'bonus',   label: '奖金', icon: 'bonus',   color: '#f5a623', amount: 0,        count: 0 },
  { key: 'invest',  label: '理财', icon: 'invest',  color: '#4c8df6', amount: 1860.40,  count: 2 },
  { key: 'side',    label: '兼职', icon: 'side',    color: '#9b5de5', amount: 2140.00,  count: 3 },
  { key: 'redpack', label: '红包', icon: 'redpack', color: '#e8507a', amount: 0,        count: 0 },
  { key: 'refund',  label: '退款', icon: 'refund',  color: '#00b8d9', amount: 0,        count: 0 },
];

const CAT_BY_KEY = Object.fromEntries([...EXPENSE_CATS, ...INCOME_CATS].map((c) => [c.key, c]));

/**
 * 账户。[cardId] 非空表示这张账户是从卡包里的卡片一键生成的 ——
 * 这是金匮做记账的独有优势：卡号、有效期、发卡行不用重录一遍。
 */
const ACCOUNTS = [
  { id: 'a1', name: '现金', kind: 'cash', icon: 'cash', balance: 1240.00, cur: 'CNY', group: '现金与储蓄' },
  { id: 'a2', name: '招商银行', kind: 'debit', icon: 'bank', balance: 86420.55, cur: 'CNY',
    group: '现金与储蓄', cardId: 'c-bank-1', tail: '5972', note: '来自卡包 · 储蓄卡' },
  { id: 'a3', name: '工商银行', kind: 'debit', icon: 'bank', balance: 12300.00, cur: 'CNY',
    group: '现金与储蓄', cardId: 'c-bank-2', tail: '8830', note: '来自卡包 · 储蓄卡' },
  { id: 'a4', name: '招行信用卡', kind: 'credit', icon: 'card', balance: -4286.30, cur: 'CNY',
    group: '信用卡', cardId: 'c-credit-1', tail: '3319', limit: 30000, used: 4286.30,
    billDay: 5, dueDay: 23, note: '来自卡包 · 信用卡' },
  { id: 'a5', name: 'U 卡 · Dupay', kind: 'crypto', icon: 'crypto', balance: 1860.20, cur: 'USDT',
    group: '虚拟资产', cardId: 'c-ukey-1', tail: '7741', note: '来自卡包 · U 卡' },
  { id: 'a6', name: '支付宝', kind: 'ewallet', icon: 'ewallet', balance: 3204.86, cur: 'CNY', group: '电子钱包' },
  { id: 'a7', name: '微信支付', kind: 'ewallet', icon: 'ewallet', balance: 968.40, cur: 'CNY', group: '电子钱包' },
  { id: 'a8', name: '韩亚银行', kind: 'foreign', icon: 'globe', balance: 1240000, cur: 'KRW',
    group: '境外账户', rate: 0.0052, note: '汇率 1 KRW ≈ 0.0052 CNY · 手动更新' },
  { id: 'a9', name: '富途证券', kind: 'invest', icon: 'invest', balance: 52400.00, cur: 'CNY', group: '投资' },
];

const CUR_SYM = { CNY: '¥', USD: '$', KRW: '₩', USDT: '₮' };

/** 流水，按天分组。 */
const DAYS = [
  {
    label: '今天', date: '10月23日', weekday: '周四', spend: 132.50, income: 0,
    items: [
      { cat: 'food', note: '公司楼下 · 拉面', acct: 'a7', amount: -38.50, time: '12:24' },
      { cat: 'transit', note: '地铁 2 号线', acct: 'a6', amount: -6.00, time: '08:52' },
      { cat: 'food', note: 'Manner 咖啡', acct: 'a4', amount: -22.00, time: '09:10', tag: '通勤' },
      { cat: 'daily', note: '便利店 · 纸巾和电池', acct: 'a6', amount: -46.00, time: '19:40' },
      { cat: 'fun', note: 'Steam 秋促', acct: 'a5', amount: -20.00, time: '21:15', cur: 'USDT', rate: 7.2 },
    ],
  },
  {
    label: '昨天', date: '10月22日', weekday: '周三', spend: 268.00, income: 1200.00,
    items: [
      { cat: 'side', note: '外包设计尾款', acct: 'a6', amount: 1200.00, time: '16:02', income: true },
      { cat: 'food', note: '和同事聚餐 · AA', acct: 'a4', amount: -186.00, time: '20:30', tag: '报销中' },
      { cat: 'medical', note: '感冒药', acct: 'a2', amount: -52.00, time: '11:20' },
      { cat: 'transit', note: '打车回家', acct: 'a6', amount: -30.00, time: '22:41' },
    ],
  },
  {
    label: '10月21日', date: '10月21日', weekday: '周二', spend: 89.90, income: 0,
    items: [
      { cat: 'food', note: '外卖 · 麻辣香锅', acct: 'a7', amount: -45.90, time: '18:36' },
      { cat: 'phone', note: '话费自动充值', acct: 'a2', amount: -29.00, time: '00:10', recur: true },
      { cat: 'pet', note: '猫粮', acct: 'a6', amount: -15.00, time: '13:05' },
    ],
  },
];

/** 近六个月收支，用于趋势图。单位元。 */
const TREND = [
  { m: '5月', spend: 9860, income: 25200 },
  { m: '6月', spend: 11240, income: 25200 },
  { m: '7月', spend: 14680, income: 27800 },
  { m: '8月', spend: 10920, income: 25200 },
  { m: '9月', spend: 13450, income: 26900 },
  { m: '10月', spend: 12486, income: 28000 },
];

/** 本月每日支出，用于热力图（10 月 1 日起 23 天）。 */
const DAILY_SPEND = [420, 86, 210, 1320, 64, 380, 96, 540, 128, 2100, 74, 320, 156, 88, 640, 210, 96, 1480, 240, 132, 89, 268, 132];

/** 预算。 */
const BUDGETS = [
  { cat: 'food', limit: 3000, used: 2148.60 },
  { cat: 'transit', limit: 800, used: 686.40 },
  { cat: 'shopping', limit: 2000, used: 3260.00 },
  { cat: 'fun', limit: 1000, used: 612.30 },
  { cat: 'pet', limit: 300, used: 186.50 },
];

/** 周期账单与订阅。 */
const RECURRING = [
  { name: '房租', cat: 'home', amount: 4500.00, cycle: '每月 1 日', next: '9 天后', acct: 'a2', state: '待扣款' },
  { name: 'Netflix', cat: 'fun', amount: 68.00, cycle: '每月 27 日', next: '4 天后', acct: 'a4', state: '待扣款' },
  { name: 'iCloud 200G', cat: 'phone', amount: 21.00, cycle: '每月 2 日', next: '10 天后', acct: 'a4', state: '待扣款' },
  { name: '健身房年卡', cat: 'medical', amount: 299.00, cycle: '每年 3 月 12 日', next: '140 天后', acct: 'a6', state: '待扣款' },
  { name: '宽带', cat: 'home', amount: 129.00, cycle: '每季度', next: '32 天后', acct: 'a2', state: '待扣款' },
];

/** 借出 / 借入 / 报销。 */
const DEBTS = [
  { name: '借给小李', kind: 'out', amount: 2000.00, date: '9月14日', state: '未还', note: '房租周转' },
  { name: '公司差旅报销', kind: 'in', amount: 860.00, date: '10月22日', state: '审核中', note: '含 3 张小票' },
  { name: '欠房东水电', kind: 'borrow', amount: 320.00, date: '10月1日', state: '待还', note: '随房租一起' },
];

/** 汇总。 */
const MONTH = {
  label: '2026年10月',
  spend: 12486.30,
  income: 28000.00,
  balance: 15513.70,
  budget: 16000,
  days: 23,
  dayAvg: 542.88,
  maxDay: { d: '10月10日', v: 2100 },
  netWorth: 152504.01,
  netWorthCny: 158952.01,
};

const fmt = (n, digits = 2) =>
  n.toLocaleString('zh-CN', { minimumFractionDigits: digits, maximumFractionDigits: digits });

/** 金额整数部分与小数部分分开排版：大数字更稳。 */
function money(n, sym = '¥') {
  const sign = n < 0 ? '-' : '';
  const abs = Math.abs(n);
  const i = Math.floor(abs);
  const d = Math.round((abs - i) * 100).toString().padStart(2, '0');
  return { sign, sym, int: i.toLocaleString('zh-CN'), dec: d };
}

function moneyHtml(n, cls = '', sym = '¥', size = 'amount-md') {
  const m = money(n, sym);
  return `<span class="num ${size} ${cls}">${m.sign}<span class="cur">${m.sym}</span>${m.int}<span style="opacity:.62;font-size:.72em">.${m.dec}</span></span>`;
}
