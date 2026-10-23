/* ==========================================================================
   线性图标。全部 24×24、1.6 描边、圆头，与金匮现有的 Material Outlined 观感一致。
   落地到 Compose 时可直接换成 material-icons-extended 里的同义图标，
   或照这里的 path 画成 ImageVector（账本不该引入新的图片资源）。
   ========================================================================== */

const ICON_PATHS = {
  /* ---- 支出分类 ---- */
  food: '<path d="M3.6 11h16.8c0 4.6-3.8 8.4-8.4 8.4S3.6 15.6 3.6 11Z"/><path d="M8.6 7.6c0-1.1 1-1.5 1-2.6M12 7.6c0-1.1 1-1.5 1-2.6"/>',
  transit: '<rect x="4.5" y="3.6" width="15" height="12.6" rx="2.4"/><path d="M4.5 9.6h15M8.4 16.2v1.8M15.6 16.2v1.8"/><circle cx="8.6" cy="12.6" r=".9" fill="currentColor" stroke="none"/><circle cx="15.4" cy="12.6" r=".9" fill="currentColor" stroke="none"/>',
  shopping: '<path d="M5.4 8h13.2l1 11.4H4.4L5.4 8Z"/><path d="M9 8V6.2a3 3 0 0 1 6 0V8"/>',
  home: '<path d="M4 10.8 12 4.6l8 6.2V19a1.4 1.4 0 0 1-1.4 1.4H5.4A1.4 1.4 0 0 1 4 19v-8.2Z"/><path d="M9.8 20.4v-5.2h4.4v5.2"/>',
  fun: '<rect x="3.6" y="5.6" width="16.8" height="12.8" rx="2.6"/><path d="m10.4 9.4 4.6 2.6-4.6 2.6V9.4Z"/>',
  medical: '<path d="M3.4 12.4h3.9l1.9-4 3.1 8.2 2-4.2h6.3"/>',
  study: '<path d="M4 5.6A1.6 1.6 0 0 1 5.6 4H11v16H5.6A1.6 1.6 0 0 1 4 18.4v-12.8Z"/><path d="M20 5.6A1.6 1.6 0 0 0 18.4 4H13v16h5.4a1.6 1.6 0 0 0 1.6-1.6v-12.8Z"/>',
  phone: '<rect x="6.6" y="2.8" width="10.8" height="18.4" rx="2.4"/><path d="M10.8 18.4h2.4"/>',
  gift: '<rect x="3.8" y="8.4" width="16.4" height="4" rx="1"/><path d="M5.4 12.4V19a1.4 1.4 0 0 0 1.4 1.4h10.4a1.4 1.4 0 0 0 1.4-1.4v-6.6M12 8.4v12"/><path d="M12 8.4S10.9 4 8.7 4a2.2 2.2 0 0 0 0 4.4H12Zm0 0s1.1-4.4 3.3-4.4a2.2 2.2 0 0 1 0 4.4H12Z"/>',
  pet: '<circle cx="6.6" cy="10.4" r="1.9"/><circle cx="10.2" cy="6.6" r="1.9"/><circle cx="14.4" cy="6.6" r="1.9"/><circle cx="17.8" cy="10.4" r="1.9"/><path d="M12.2 11.6c2.9 0 5.4 2.4 5.4 4.6 0 1.9-1.6 3-3.6 2.6-1.2-.2-2.4-.2-3.6 0-2 .4-3.6-.7-3.6-2.6 0-2.2 2.5-4.6 5.4-4.6Z"/>',
  cloth: '<path d="M8.4 3.6 12 5.8l3.6-2.2 4.4 3-2 3.2-1.6-1v11.6H7.6V8.8L6 9.8l-2-3.2 4.4-3Z"/>',
  daily: '<path d="m12 3.2 8 4.4v8.8l-8 4.4-8-4.4V7.6l8-4.4Z"/><path d="m4 7.6 8 4.4 8-4.4M12 12v8.8"/>',
  travel: '<path d="M12 3.4c2.4 2.6 3.6 5 3.6 7.2a3.6 3.6 0 1 1-7.2 0c0-2.2 1.2-4.6 3.6-7.2Z"/><path d="M5 20.6c1.6-2.2 4-3.4 7-3.4s5.4 1.2 7 3.4"/>',

  /* ---- 收入分类 ---- */
  salary: '<rect x="3.2" y="6" width="17.6" height="12.6" rx="2.4"/><path d="M3.2 10.4h17.6"/><circle cx="16.6" cy="14.6" r="1.4"/>',
  bonus: '<path d="M7.4 4h9.2v4.6a4.6 4.6 0 1 1-9.2 0V4Z"/><path d="M7.4 5.6H5.2a2.4 2.4 0 0 0 2.4 3.6M16.6 5.6h2.2a2.4 2.4 0 0 1-2.4 3.6M9.6 20.4h4.8M12 13.2v7.2"/>',
  invest: '<path d="M3.6 19.6h16.8"/><rect x="5.6" y="12.4" width="3.2" height="5" rx="1"/><rect x="10.4" y="9" width="3.2" height="8.4" rx="1"/><rect x="15.2" y="5.4" width="3.2" height="12" rx="1"/>',
  side: '<rect x="3.2" y="7.4" width="17.6" height="12.4" rx="2.2"/><path d="M8.8 7.4V6a2 2 0 0 1 2-2h2.4a2 2 0 0 1 2 2v1.4M3.2 12.6h17.6"/>',
  redpack: '<rect x="4.4" y="3.4" width="15.2" height="17.2" rx="2.2"/><path d="m4.4 6.6 7.6 4.6 7.6-4.6"/><circle cx="12" cy="14.4" r="2"/>',
  refund: '<path d="M4.4 11.4a7.8 7.8 0 1 1 2.6 6.4"/><path d="M3.6 5.6v5.8h5.8"/>',

  /* ---- 账户 ---- */
  cash: '<rect x="2.8" y="6.4" width="18.4" height="11.2" rx="2.2"/><circle cx="12" cy="12" r="2.6"/><path d="M6.4 12h.01M17.6 12h.01"/>',
  bank: '<path d="m3.6 9.6 8.4-5.4 8.4 5.4"/><path d="M5.8 9.6v9M10 9.6v9M14 9.6v9M18.2 9.6v9M3.2 20.4h17.6"/>',
  card: '<rect x="2.8" y="5.4" width="18.4" height="13.2" rx="2.4"/><path d="M2.8 10h18.4"/><path d="M6.4 14.6h3.2"/>',
  ewallet: '<rect x="4.4" y="3.4" width="15.2" height="17.2" rx="3"/><path d="M8.6 8.6h6.8M8.6 12h4.2"/><circle cx="14.8" cy="15.8" r="1.6"/>',
  crypto: '<rect x="6.6" y="6.6" width="10.8" height="10.8" rx="2"/><path d="M12 2.8v3.8M12 17.4v3.8M2.8 12h3.8M17.4 12h3.8"/><circle cx="12" cy="12" r="2.2"/>',
  globe: '<circle cx="12" cy="12" r="8.6"/><path d="M3.4 12h17.2M12 3.4c2.4 2.6 3.6 5.4 3.6 8.6s-1.2 6-3.6 8.6c-2.4-2.6-3.6-5.4-3.6-8.6S9.6 6 12 3.4Z"/>',
  debt: '<path d="M7.4 8.4h9.2M7.4 12h9.2M7.4 15.6h5.6"/><rect x="3.6" y="4" width="16.8" height="16" rx="2.4"/>',

  /* ---- 界面 ---- */
  plus: '<path d="M12 5v14M5 12h14"/>',
  search: '<circle cx="11" cy="11" r="6.6"/><path d="m16 16 4.4 4.4"/>',
  more: '<circle cx="5.6" cy="12" r="1.5" fill="currentColor" stroke="none"/><circle cx="12" cy="12" r="1.5" fill="currentColor" stroke="none"/><circle cx="18.4" cy="12" r="1.5" fill="currentColor" stroke="none"/>',
  back: '<path d="M15 4.8 7.8 12l7.2 7.2"/>',
  left: '<path d="M14.4 5.6 8 12l6.4 6.4"/>',
  right: '<path d="m9.6 5.6 6.4 6.4-6.4 6.4"/>',
  down: '<path d="m5.6 9.2 6.4 6.4 6.4-6.4"/>',
  calendar: '<rect x="3.6" y="5.2" width="16.8" height="15.2" rx="2.4"/><path d="M3.6 10h16.8M8.4 3.4v3.6M15.6 3.4v3.6"/>',
  pie: '<circle cx="12" cy="12" r="8.6"/><path d="M12 3.4V12l7.4 4.3"/>',
  chart: '<path d="M3.6 19.6h16.8"/><path d="m5.6 15.4 4-4.6 3.2 2.8 5.6-6.6"/><path d="M18.4 7h-3.2M18.4 7v3.2"/>',
  camera: '<path d="M3.6 8.6h3.2l1.6-2.4h7.2l1.6 2.4h3.2v10.2H3.6V8.6Z"/><circle cx="12" cy="13.4" r="3.2"/>',
  tag: '<path d="M4 11.2V4.8h6.4l9.2 9.2-6.4 6.4L4 11.2Z"/><circle cx="8" cy="8" r="1.4"/>',
  edit: '<path d="m4.8 19.2 4-1 10-10-3-3-10 10-1 4Z"/><path d="m14.4 6.6 3 3"/>',
  trash: '<path d="M4.8 7h14.4M9.2 7V4.8h5.6V7M6.6 7l1 12.4h8.8L17.4 7"/>',
  check: '<path d="m5 12.6 4.6 4.6L19 6.8"/>',
  filter: '<path d="M4 7h16M7 12h10M10 17h4"/>',
  download: '<path d="M12 4v10.4M8 11l4 4 4-4"/><path d="M4.6 18.4h14.8"/>',
  upload: '<path d="M12 15.4V5M8 8.6l4-4 4 4"/><path d="M4.6 18.4h14.8"/>',
  refresh: '<path d="M19.4 11.4a7.6 7.6 0 1 0-.8 4.6"/><path d="M19.8 5.6v5.8H14"/>',
  lock: '<rect x="4.8" y="10" width="14.4" height="10.4" rx="2.4"/><path d="M8.4 10V7.6a3.6 3.6 0 0 1 7.2 0V10"/>',
  bell: '<path d="M6.4 10.4a5.6 5.6 0 1 1 11.2 0c0 4 1.4 5.6 1.4 5.6H5s1.4-1.6 1.4-5.6Z"/><path d="M10.2 19.2a2 2 0 0 0 3.6 0"/>',
  eye: '<path d="M2.8 12S6.4 5.8 12 5.8 21.2 12 21.2 12 17.6 18.2 12 18.2 2.8 12 2.8 12Z"/><circle cx="12" cy="12" r="2.8"/>',
  copy: '<rect x="8.4" y="8.4" width="11.2" height="11.2" rx="2.2"/><path d="M15.6 8.4V6.6a2.2 2.2 0 0 0-2.2-2.2H6.6a2.2 2.2 0 0 0-2.2 2.2v6.8a2.2 2.2 0 0 0 2.2 2.2h1.8"/>',
  swap: '<path d="M4 8.6h13.2l-3-3M20 15.4H6.8l3 3"/>',
  book: '<path d="M4.4 4.6h6.2A2.4 2.4 0 0 1 13 7v12.4a2 2 0 0 0-2-2H4.4V4.6Z"/><path d="M19.6 4.6h-6.2A2.4 2.4 0 0 0 11 7v12.4a2 2 0 0 1 2-2h6.6V4.6Z"/>',
  cards: '<rect x="3.2" y="6.4" width="12.4" height="11.2" rx="2.2"/><path d="M7.6 4.4h10.8a2.4 2.4 0 0 1 2.4 2.4v8.4"/>',
  gear: '<circle cx="12" cy="12" r="3.2"/><path d="M12 3.2v2.4M12 18.4v2.4M4.8 12H2.4M21.6 12h-2.4M6.9 6.9 5.2 5.2M18.8 18.8l-1.7-1.7M17.1 6.9l1.7-1.7M5.2 18.8l1.7-1.7"/>',
  spark: '<path d="M12 3.6 13.9 9l5.5 1.9-5.5 1.9L12 18.4 10.1 12.8 4.6 10.9 10.1 9 12 3.6Z"/>',
  wallet: '<rect x="3.2" y="6" width="17.6" height="12.6" rx="2.6"/><path d="M3.2 10.2h17.6M16.4 14.8h1.6"/>',
  clock: '<circle cx="12" cy="12" r="8.6"/><path d="M12 7.2V12l3.4 2"/>',
  receipt: '<path d="M6 3.4h12v17.2l-2.4-1.6-2.4 1.6-2.4-1.6-2.4 1.6L6 20.6V3.4Z"/><path d="M9.2 8h5.6M9.2 12h5.6"/>',
};

/** 返回一个内联 SVG 字符串。size 单位 px。 */
function icon(name, size = 20, extra = '') {
  const d = ICON_PATHS[name] || ICON_PATHS.daily;
  return `<svg width="${size}" height="${size}" viewBox="0 0 24 24" fill="none" stroke="currentColor"
    stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round" ${extra} aria-hidden="true">${d}</svg>`;
}

/* 状态栏用的实心小图标 */
const SB_SIGNAL = '<svg width="17" height="11" viewBox="0 0 17 11" fill="currentColor"><rect x="0" y="7" width="3" height="4" rx="1"/><rect x="4.6" y="5" width="3" height="6" rx="1"/><rect x="9.2" y="2.6" width="3" height="8.4" rx="1"/><rect x="13.8" y="0" width="3" height="11" rx="1" opacity=".35"/></svg>';
const SB_WIFI = '<svg width="15" height="11" viewBox="0 0 15 11" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"><path d="M1.2 3.6a9 9 0 0 1 12.6 0M3.8 6.4a5.4 5.4 0 0 1 7.4 0"/><circle cx="7.5" cy="9.3" r=".9" fill="currentColor" stroke="none"/></svg>';
const SB_BATT = '<svg width="25" height="12" viewBox="0 0 25 12" fill="none"><rect x=".6" y=".6" width="20" height="10.8" rx="3" stroke="currentColor" stroke-opacity=".4"/><rect x="2.4" y="2.4" width="14" height="7.2" rx="1.8" fill="currentColor"/><path d="M22.6 4.2v3.6a2 2 0 0 0 0-3.6Z" fill="currentColor" fill-opacity=".4"/></svg>';
