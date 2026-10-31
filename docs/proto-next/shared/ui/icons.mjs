/**
 * 图标集合：内联 SVG 字符串，不引外部资源。
 *
 * 用途：应用外壳 l4 的侧栏菜单、搜索框、用户区与页头按钮。
 * 约定：统一 16×16 视框、currentColor 描边，尺寸由外层 CSS 控制。
 */

const svg = (paths, opts = {}) => {
  const { fill = 'none', stroke = 'currentColor', width = 1.5 } = opts;
  return `<svg viewBox="0 0 16 16" width="16" height="16" fill="${fill}" stroke="${stroke}" stroke-width="${width}" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">${paths}</svg>`;
};

export const ICONS = {
  /* 通用 */
  chevronDown: svg('<path d="M4 6.5l4 4 4-4"/>'),
  close: svg('<path d="M4 4l8 8M12 4l-8 8"/>'),
  search: svg('<circle cx="7" cy="7" r="4.2"/><path d="M10.2 10.2L13.5 13.5"/>'),
  help: svg('<circle cx="8" cy="8" r="5.8"/><path d="M6.4 6.3a1.7 1.7 0 113.2.6c0 1-1.6 1.2-1.6 2.2"/><path d="M8 11.6h.01"/>'),
  package: svg('<path d="M8 2l5.5 3v6L8 14l-5.5-3V5z"/><path d="M2.5 5L8 8l5.5-3M8 8v6"/>'),

  /* 一级菜单 */
  warehouse: svg('<path d="M2 7l6-4 6 4v6.5H2z"/><path d="M6 13.5V9.5h4v4"/>'),
  goods: svg('<rect x="2.5" y="3" width="11" height="10" rx="1.2"/><path d="M2.5 6.2h11M6 3v3.2"/>'),
  order: svg('<path d="M3.5 2.5h6l3 3v8h-9z"/><path d="M9.5 2.5v3h3"/><path d="M5.8 8.5h4.4M5.8 11h3"/>'),
  owner: svg('<circle cx="8" cy="5.6" r="2.6"/><path d="M3 13.5c0-2.5 2.2-4 5-4s5 1.5 5 4"/>'),

  /* 备用 */
  settings: svg('<circle cx="8" cy="8" r="2.2"/><path d="M8 1.8v1.6M8 12.6v1.6M1.8 8h1.6M12.6 8h1.6M3.6 3.6l1.1 1.1M11.3 11.3l1.1 1.1M12.4 3.6l-1.1 1.1M4.7 11.3l-1.1 1.1"/>'),
};
