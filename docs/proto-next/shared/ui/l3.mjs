/**
 * L3 扩展组件：单页 HTML 版。
 *
 * 类名与结构对齐 @westmonth/ui-react@0.1.69 的真实实现，
 * 便于将来迁移到 React 时逐项对照。
 * 样式全部来自 tokens.css 的 --wds-* 变量，本文件不写死色值。
 *
 * 规范依据：design.westmonth.com L3 扩展组件
 */

const esc = (v) => String(v ?? '')
  .replace(/&/g, '&amp;').replace(/</g, '&lt;')
  .replace(/>/g, '&gt;').replace(/"/g, '&quot;');

/* ---------------------------------------------------------------------------
   PageHeader 页面头部
   对应 @westmonth/ui-react 的 PageHeader。
   结构：header.wm-page-header > div.wm-page-header__body
           > [button.wm-page-header__back]
           > div.wm-page-header__heading (h1.title + span.subtitle + div.tags)
           > div.wm-page-header__extra
   规范：统一使用 PageHeader，不叠加自定义标题栏。
   --------------------------------------------------------------------------- */
export function PageHeader({ title, subTitle, tags, extra, onBack, backText = '返回', spacing = 'default' } = {}) {
  const back = onBack
    ? `<button type="button" class="wm-page-header__back ant-btn ant-btn-text" aria-label="${esc(backText)}" onclick="${esc(onBack)}">${esc(backText)}</button>`
    : '';
  const tagsHtml = tags ? `<div class="wm-page-header__tags">${tags}</div>` : '';
  const extraHtml = extra ? `<div class="wm-page-header__extra">${extra}</div>` : '';
  const cls = ['wm-page-header', spacing === 'page' && 'wm-page-header--page'].filter(Boolean).join(' ');

  return `<header class="${cls}">
  <div class="wm-page-header__body">
    ${back}
    <div class="wm-page-header__heading">
      <h1 class="wm-page-header__title">${esc(title)}</h1>
      ${subTitle ? `<span class="wm-page-header__subtitle">${esc(subTitle)}</span>` : ''}
      ${tagsHtml}
    </div>
    ${extraHtml}
  </div>
</header>`;
}

/* ---------------------------------------------------------------------------
   BaseTable 基础表格
   对应 @westmonth/ui-react 的 BaseTable，但只复刻静态渲染部分
   （真实组件基于 antd Table，带请求、分页、列配置；单页原型不需要这些）。
   三层结构：__header（计数与工具条）+ __content（表格）+ __footer（分页）
   --------------------------------------------------------------------------- */
export function BaseTable({
  columns = [],       // [{ key, title, align, width, render }]
  rows = [],
  rowKey = 'id',
  summary,            // 左上角说明文字
  actions,            // 右上角工具条 HTML
  emptyText = '暂无数据',
  pager = false, pageSize = 20, unit = '条',
} = {}) {
  const head = columns.map((c) => {
    const style = [
      c.width ? `width:${c.width}` : '',
      c.align ? `text-align:${c.align}` : '',
    ].filter(Boolean).join(';');
    return `<th${style ? ` style="${style}"` : ''}>${c.title}</th>`;
  }).join('');

  const body = rows.length
    ? rows.map((r, ri) => {
        const tds = columns.map((c) => {
          const v = c.render ? c.render(r) : esc(r[c.key]);
          const cls = [c.align === 'right' ? 'num' : '', c.mono ? 'mono' : ''].filter(Boolean).join(' ');
          return `<td${cls ? ` class="${cls}"` : ''}>${v}</td>`;
        }).join('');
        /* 与 ListPage 同机制：全量渲染，分页脚本按 data-row-idx 显隐 */
        return `<tr data-row-idx="${ri}"${pager && ri >= pageSize ? ' hidden' : ''}>${tds}</tr>`;
      }).join('\n')
    : `<tr><td class="wm-base-table__empty" colspan="${columns.length}">${esc(emptyText)}</td></tr>`;

  const pagerBar = pager ? PagerBar(rows.length, pageSize, 1, unit) : '';

  const headerBar = (summary || actions)
    ? `<div class="wm-base-table__header">
    ${summary ? `<span class="wm-base-table__summary">${esc(summary)}</span>` : ''}
    ${actions ? `<div class="wm-base-table__header-actions">${actions}</div>` : ''}
  </div>`
    : '';

  return `<div class="wm-base-table${pager ? ' page-card' : ''}">
  ${headerBar}
  <div class="wm-base-table__content">
    <table class="wm-base-table__table">
      <thead><tr>${head}</tr></thead>
      <tbody>${body}</tbody>
    </table>
  </div>
  ${pagerBar}
</div>`;
}

/* ---------------------------------------------------------------------------
   Stat 指标卡
   规范未提供对应组件，按 L1 令牌自行组合。
   命名沿用 --wds-card-padding，避免与组件库冲突。
   --------------------------------------------------------------------------- */
export function StatRow(items = []) {
  const cards = items.map((it) => `<div class="wm-stat">
    <span class="wm-stat__label">${esc(it.label)}</span>
    <b class="wm-stat__value">${esc(it.value)}</b>
  </div>`).join('');
  return `<div class="wm-stat-row">${cards}</div>`;
}

/* ---------------------------------------------------------------------------
   Tag 状态标签
   基于 antd Tag 的视觉，语义色取自 --wds-component-tag-*
   --------------------------------------------------------------------------- */
const TAG_TONE = { ok: 'success', low: 'warning', out: 'danger', info: 'info', default: 'default' };

export function Tag(text, tone = 'default') {
  const t = TAG_TONE[tone] || 'default';
  return `<span class="ant-tag ant-tag-${t} wm-tag wm-tag--${t}">${esc(text)}</span>`;
}

/* ---------------------------------------------------------------------------
   组件样式：类名与 @westmonth/ui-react 一致，规则改写为单页可用形式
   --------------------------------------------------------------------------- */
/* ---------------------------------------------------------------------------
   标准列表页骨架 ListPage
   对齐基线 docs/web 端原型模板_20261013.html 的 listPage(cfg)：
     page-card › statusTabs › queryPanel › actionBar › table-wrap/dt › pagerBar
   之前 BaseTable 只覆盖「计数条 + 表格」两层，缺筛选面板、状态页签、
   分页条、行复选框与固定列，列表页因此不像基线。这里按基线补全。
   --------------------------------------------------------------------------- */

/** 状态页签：StatusTabs([{ label, count, active }]) */
/**
 * 状态页签行。viewSwitch 可选：给了就在右侧并排一组视图切换按钮。
 * 视图切换与状态页签是两个维度——前者决定"看哪套数据"，后者决定"筛哪段状态"。
 * 两边分开，语义不混：状态筛选不会把视图切换顶掉。
 */
export function StatusTabs(tabs = [], viewSwitch = []) {
  if (!tabs.length && !viewSwitch.length) return '';
  const tabsHTML = tabs.length
    ? `<div class="status-tabs">${tabs.map((t, i) => {
        const hasCount = t.count !== undefined && t.count !== null && t.count !== '';
        /* field / value 可选：声明该页签对应的筛选口径，供页头仓库切换后重算计数。
           不给这两个字段时只出文案，与旧行为完全一致。 */
        const hook = t.field
          ? ` data-tab-field="${esc(t.field)}" data-tab-value="${esc(t.value ?? '')}"`
          : '';
        return `<button class="tab-button${t.active ? ' active' : ''}" data-status-tab="${i}"${hook}>${esc(t.label)}`
          + (hasCount ? `<span class="tab-count">${esc(t.count)}</span>` : '') + `</button>`;
      }).join('')}</div>`
    : '';
  const switchHTML = viewSwitch.length
    ? `<div class="view-switch">${viewSwitch.map((v) =>
        `<button type="button" class="view-switch__btn${v.active ? ' is-on' : ''}"`
        + ` data-view="${esc(v.key)}">${esc(v.label)}</button>`).join('')}</div>`
    : '';
  return `<div class="status-tabs-row">${tabsHTML}${switchHTML}</div>`;
}

/** 状态胶囊：StatusTag('待上架', 'pending')
 *  类型：pending | enabled | disabled | rejected | info */
export function StatusTag(text, type = 'disabled') {
  return `<span class="status ${esc(type || 'disabled')}">${esc(text)}</span>`;
}

/** 进度单元格 */
export function ProgressCell(done, total) {
  const d = Number(done) || 0;
  const t = Number(total) || 0;
  const pct = t > 0 ? Math.min(100, Math.round(d / t * 100)) : 0;
  return `<span class="progress-cell${d > t ? ' is-over' : ''}">`
    + `<span class="progress-track"><span class="progress-fill" style="width:${pct}%"></span></span>`
    + `<span class="progress-text">${d} / ${t}</span></span>`;
}

/** 单元格链接（单据号等） */
export function LinkCell(text) {
  return `<button class="cell-link" type="button">${esc(text)}</button>`;
}

/** 查询面板。type：text | select | date | daterange */
export function QueryPanel({ fields = [], extra, collapsed = true } = {}) {
  if (!fields.length) return '';
  const extraCount = (extra === undefined)
    ? fields.filter((f) => f.extra).length
    : extra;

  const fieldsHTML = fields.map((f) => {
    const cls = ['field-group'];
    if (f.extra) cls.push('field-extra');
    if (f.span === 'full') cls.push('field-wide');
    if (f.type === 'daterange' || f.span === 2) cls.push('field-time');

    let ctrl = '';
    if (f.type === 'select') {
      ctrl = `<select class="field" aria-label="${esc(f.label)}">`
        + (f.options || ['全部']).map((o) => `<option>${esc(o)}</option>`).join('') + `</select>`;
    } else if (f.type === 'daterange') {
      const t = (f.native === false) ? 'text' : 'datetime-local';
      ctrl = `<div class="date-range">`
        + `<input class="field" type="${t}" aria-label="${esc(f.label)} 开始"><span>至</span>`
        + `<input class="field" type="${t}" aria-label="${esc(f.label)} 结束"></div>`;
    } else if (f.type === 'date') {
      ctrl = `<input class="field" type="date" aria-label="${esc(f.label)}">`;
    } else {
      ctrl = `<input class="field" type="text" placeholder="${esc(f.placeholder || '')}" aria-label="${esc(f.label)}">`;
    }
    return `<div class="${cls.join(' ')}"><label>${esc(f.label)}</label>${ctrl}</div>`;
  }).join('');

  const toggle = extraCount > 0
    ? `<button class="query-toggle" type="button" aria-expanded="${!collapsed}">`
      + `<span class="query-toggle-label">${collapsed ? `展开全部条件（${extraCount}）` : '收起全部条件'}</span>`
      + `<span class="query-toggle-icon${collapsed ? '' : ' is-up'}">`
      + `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="6 9 12 15 18 9"/></svg>`
      + `</span></button>`
    : '';

  return `<form class="query-panel${collapsed && extraCount > 0 ? ' is-collapsed' : ''}"`
    + ` data-extra="${extraCount}" onsubmit="return false;">`
    + fieldsHTML
    + `<div class="query-actions">`
    + `<button class="btn primary" type="submit">查询</button>`
    + `<button class="btn" type="button">重置</button>`
    + toggle
    + `</div></form>`;
}

/** 操作栏：ActionBar({ buttons:[{label,primary,ghost,disabled}], note, showCount }) */
export function ActionBar({ buttons = [], note = '', showCount = false } = {}) {
  if (!buttons.length && !note && !showCount) return '';
  const cls = (b) => 'btn' + (b.primary ? ' primary' : '') + (b.ghost ? ' ghost' : '');
  /* attrs 让按钮能带 data-* 等挂钩，供页面脚本接管点击。
     不给这个口子，按钮点了不会有任何反应。 */
  const attrs = (b) => Object.entries(b.attrs || {})
    .map(([k, v]) => ` ${k}="${esc(v)}"`).join('');
  return `<div class="action-bar">`
    + `<div class="row-actions">${buttons.map((b) =>
        `<button class="${cls(b)}" type="button"${b.disabled ? ' disabled' : ''}${attrs(b)}>${esc(b.label)}</button>`).join('')}</div>`
    + (showCount ? `<span class="selected-count">已选 <b>0</b> 项</span>` : '')
    + (note ? `<span class="muted">${esc(note)}</span>` : '')
    + `</div>`;
}

/** 分页条 */
/**
 * 标准分页器（可切页）。
 * 沿用基线 .pager-bar 的「共 N 条 + 页码」结构，补上每页条数选择与跳页。
 * 页码按钮的实际切页行为由 shell/pager.js 接管，这里只出结构与首屏态。
 * @param {number} total    总条数
 * @param {number} pageSize 每页条数
 * @param {number} page     当前页（1 起）
 * @param {string} unit     计数单位，默认「条」
 */
export function PagerBar(total = 0, pageSize = 20, page = 1, unit = '条') {
  const pages = Math.max(1, Math.ceil(total / pageSize));
  const cur = Math.min(Math.max(1, page), pages);
  const lo = total === 0 ? 0 : (cur - 1) * pageSize + 1;
  const hi = Math.min(total, cur * pageSize);

  /* 页码窗口：当前页两侧各一页，首尾固定，中间用省略号 */
  const nums = [];
  const push = (n) => { if (nums.indexOf(n) < 0 && n >= 1 && n <= pages) nums.push(n); };
  push(1);
  if (cur - 2 > 1) nums.push('...');
  [cur - 1, cur, cur + 1].forEach(push);
  if (cur + 2 < pages) nums.push('...');
  push(pages);

  const btn = (n) => (n === '...'
    ? `<span class="pager-gap">…</span>`
    : `<button type="button" data-page-num="${n}"${n === cur ? ' class="active" aria-current="page"' : ''}>${n}</button>`);

  return `<div class="pager-bar" data-pager data-total="${esc(total)}" data-page="${cur}" data-size="${esc(pageSize)}">`
    + `<span class="pager-total">共 ${esc(total)} ${esc(unit)}`
    + (total ? ` · 第 ${lo}-${hi} ${esc(unit)}` : '') + `</span>`
    + `<div class="pager-right">`
    +   `<label class="pager-size">每页 <select data-page-size>`
    +     [10, 20, 50, 100].map((n) => `<option value="${n}"${n === pageSize ? ' selected' : ''}>${n}</option>`).join('')
    +   `</select> ${esc(unit)}</label>`
    +   `<div class="pager">`
    +     `<button type="button" data-page-prev${cur <= 1 ? ' disabled' : ''}>上一页</button>`
    +     nums.map(btn).join('')
    +     `<button type="button" data-page-next${cur >= pages ? ' disabled' : ''}>下一页</button>`
    +   `</div>`
    +   `<label class="pager-jump">跳至 <input type="text" inputmode="numeric" data-page-jump value="${cur}" aria-label="跳至页"><button type="button" data-page-go>GO</button></label>`
    + `</div></div>`;
}

/**
 * 标准列表页。columns 支持 { key, label, width, align:'right', fixed:true }。
 * rows 为二维数组，单元格可以是字符串或已转义的 HTML 片段。
 */
export function ListPage({
  pager = true,
  statusTabs = [], viewSwitch = [], filters = [], filtersCollapsed = true, extraCount,
  toolbar = [], scopeNote, showCount = false,
  columns = [], rows = [], emptyTitle = '暂无数据',
  emptyDesc = '当前筛选条件下没有任何记录。',
  pageSize = 20, unit = '条',
  /* rowAttrs：行对象 → 额外属性对象，写到 <tr> 上。
     单仓页用它给每行标 data-wh，供页头仓库下拉做前端过滤。
     不传时行为与旧版完全一致。 */
  rowAttrs = null,
} = {}) {
  const label = (c) => c.label || c.title || '';
  /* 行两种形态都收：二维数组（基线原样）与对象数组（页面页在用）。
     对象行按列的 render / key 取值，取值结果默认转义，render 的返回值视为已转义 HTML。 */
  const isMatrix = Array.isArray(rows[0]);

  const thHTML = `<th class="col-check sticky-col-left"><input type="checkbox" class="dt-select-all" aria-label="全选"></th>`
    + columns.map((c) => {
        const style = (c.width ? `width:${c.width};` : '')
          + (c.align === 'right' ? 'text-align:right;' : '');
        return `<th${c.fixed ? ' class="sticky-col-right"' : ''}${style ? ` style="${style}"` : ''}>${esc(label(c))}</th>`;
      }).join('');

  const emptyRow = `<tr class="dt-empty"><td colspan="${columns.length + 1}">`
    + `<span class="empty-mini-title">${esc(emptyTitle)}</span>`
    + `<span class="empty-mini-desc">${esc(emptyDesc)}</span></td></tr>`;

  const cellHTML = (c, cell) => {
    const cls = [];
    if (c.align === 'right') cls.push('col-num');
    if (c.fixed) cls.push('col-act sticky-col-right');
    if (c.cls) cls.push(c.cls);
    const html = (cell && typeof cell === 'object') ? (cell.html || '') : (cell == null ? '' : String(cell));
    return `<td${cls.length ? ` class="${cls.join(' ')}"` : ''}>${html}</td>`;
  };

  /* 全量行都在 DOM 里（列渲染留在构建期），分页脚本按 data-row-idx 显隐。
     首屏只有第一页可见，避免 120 行一次性铺开。 */
  const extraAttr = (row) => {
    if (!rowAttrs) return '';
    const o = rowAttrs(row) || {};
    return Object.entries(o)
      .filter(([, v]) => v !== undefined && v !== null && v !== '')
      .map(([k, v]) => ` ${k}="${esc(v)}"`).join('');
  };
  const bodyHTML = rows.length
    ? rows.map((row, ri) => `<tr data-row-idx="${ri}"${ri >= pageSize ? ' hidden' : ''}${extraAttr(row)}>`
        + `<td class="sticky-col-left"><input type="checkbox" class="dt-row-check" aria-label="选择该行"></td>`
        + (isMatrix
            ? row.map((cell, ci) => cellHTML(columns[ci] || {}, cell)).join('')
            : columns.map((c) => cellHTML(c, c.render ? c.render(row) : esc(row[c.key]))).join(''))
        + `</tr>`).join('')
    : emptyRow;

  const note = (scopeNote === undefined)
    ? '当前为全部仓库数据范围'
    : scopeNote;

  return `<div class="page-card">`
    + StatusTabs(statusTabs, viewSwitch)
    + QueryPanel({ fields: filters, extra: extraCount, collapsed: filtersCollapsed })
    + ActionBar({ buttons: toolbar, note, showCount })
    + `<div class="table-wrap"><table class="dt">`
    + `<thead><tr>${thHTML}</tr></thead>`
    + `<tbody>${bodyHTML}</tbody>`
    + `</table></div>`
    /* 自定义渲染的页面（如执行单元）沿用旧版做法：计数写在工具栏，不挂分页条 */
    + (pager ? PagerBar(rows.length, pageSize, 1, unit) : '')
    + `</div>`;
}

export const L3_CSS = `
/* ===== PageHeader（对齐 @westmonth/ui-react 的 .wm-page-header）===== */
.wm-page-header{
  box-sizing:border-box;width:100%;
  color:var(--wds-color-text-primary);
  background:var(--wds-color-background-canvas);
}
.wm-page-header *,.wm-page-header :before,.wm-page-header :after{box-sizing:border-box}
.wm-page-header__body{
  display:flex;align-items:center;justify-content:space-between;
  gap:var(--wds-layout-gap);
  min-height:clamp(32px, calc(var(--wds-control-height,32px) + var(--wds-control-padding-block,4px)), 40px);
}
.wm-page-header__heading{
  display:flex;align-items:center;flex-wrap:wrap;flex:1;min-width:0;
  gap:var(--wds-toolbar-gap);
}
.wm-page-header__title{
  margin:0;font-weight:600;line-height:1.4;
  font-size:var(--wds-size-font-lg,18px);color:inherit;
}
.wm-page-header__subtitle{
  color:var(--wds-color-text-secondary);
  font-size:var(--wds-size-font-sm);
}
.wm-page-header__tags,.wm-page-header__extra{
  display:flex;align-items:center;flex-wrap:wrap;gap:var(--wds-toolbar-gap);
}
.wm-page-header__extra{flex:none}
.wm-page-header__back{
  flex:none;width:auto;height:var(--wds-control-height,32px);
  padding-inline:0;border:0;background:transparent;
  color:var(--wds-color-link);cursor:pointer;font:inherit;
  margin-inline-end:var(--wds-toolbar-gap);
}

/* ===== BaseTable（对齐 .wm-base-table 结构）===== */
.wm-base-table{
  --wm-base-table-border-color:var(--wds-component-table-border,var(--wds-color-border-secondary));
  --wm-base-table-header-background:var(--wds-component-table-header-bg,var(--wds-color-background-subtle));
  --wm-base-table-row-hover-background:var(--wds-component-table-row-hover-bg,var(--wds-color-background-hover));
  box-sizing:border-box;display:block;
  width:100%;font-family:inherit;
  background:var(--wds-color-background-container);
  border-radius:var(--wds-size-radius-md);
  box-shadow:var(--wds-size-shadow-xs);
}
.wm-base-table__header{
  display:flex;align-items:center;gap:var(--wds-layout-gap);
  min-height:var(--wds-control-height,32px);
  padding:var(--wds-layout-gap) var(--wds-card-padding);
}
.wm-base-table__summary{color:var(--wds-color-text-tertiary);flex:none;font-size:var(--wds-size-font-xs)}
.wm-base-table__header-actions{display:flex;align-items:center;gap:var(--wds-layout-gap);flex:1;justify-content:flex-start;flex-wrap:nowrap;min-width:0;overflow-x:auto}
.wm-base-table__content{position:relative;overflow:auto;max-height:calc(100vh - 260px)}
.wm-base-table__table{border-collapse:collapse;width:100%}
.wm-base-table__table th,
.wm-base-table__table td{
  padding:var(--wds-table-cell-padding-block,var(--wds-table-cell-padding))
          var(--wds-table-cell-padding-inline,var(--wds-table-cell-padding));
  text-align:left;white-space:nowrap;
  border-bottom:1px solid var(--wm-base-table-border-color);
}
.wm-base-table__table th{
  position:sticky;top:0;z-index:1;
  font-weight:500;color:var(--wds-component-table-header-text,var(--wds-color-text-secondary));
  background:var(--wm-base-table-header-background);
}
.wm-base-table__table tbody tr:hover td{background:var(--wm-base-table-row-hover-background)}
.wm-base-table__table tbody tr:last-child td{border-bottom:0}
.wm-base-table__empty{text-align:center!important;color:var(--wds-color-text-tertiary);padding-block:calc(var(--wds-layout-gap-lg) * 2)!important}
.wm-base-table__table .num{text-align:right;font-variant-numeric:tabular-nums}
.wm-base-table__table .mono{font-family:ui-monospace,Menlo,monospace;font-size:var(--wds-size-font-xs)}

/* ===== Stat（规范未提供，按 L1 令牌组合）===== */
.wm-stat-row{
  display:grid;grid-template-columns:repeat(auto-fit,minmax(120px,1fr));
  gap:var(--wds-layout-gap);
}
.wm-stat{
  background:var(--wds-color-background-container);
  border-radius:var(--wds-size-radius-md);
  box-shadow:var(--wds-size-shadow-xs);
  padding:var(--wds-card-padding);
}
.wm-stat__label{display:block;color:var(--wds-color-text-secondary);font-size:var(--wds-size-font-xs)}
.wm-stat__value{display:block;margin-top:2px;font-size:var(--wds-size-font-xl);line-height:1.3;font-variant-numeric:tabular-nums}

/* ===== Tag（对齐 antd tag 视觉，取 --wds-component-tag-* ）===== */
.wm-tag{
  display:inline-block;margin-inline-end:0;
  padding:0 7px;border-radius:var(--wds-size-radius-sm);
  font-size:var(--wds-size-font-xs);line-height:20px;height:20px;
  border:1px solid transparent;
}
.wm-tag--success{color:var(--wds-component-tag-success-text);background:var(--wds-component-tag-success-background);border-color:var(--wds-component-tag-success-border)}
.wm-tag--warning{color:var(--wds-component-tag-warning-text);background:var(--wds-component-tag-warning-background);border-color:var(--wds-component-tag-warning-border)}
.wm-tag--danger{color:var(--wds-component-tag-danger-text);background:var(--wds-component-tag-danger-background);border-color:var(--wds-component-tag-danger-border)}
.wm-tag--info{color:var(--wds-component-tag-info-text);background:var(--wds-component-tag-info-background);border-color:var(--wds-component-tag-info-border)}
.wm-tag--default{color:var(--wds-color-text-secondary);background:var(--wds-color-background-subtle);border-color:var(--wds-color-border-secondary)}

/* ===== 整页卡片（基线 .page-card）===== */
.page-card{
  min-height:calc(100vh - var(--topbar-height) - 40px);
  padding:20px;
  background:var(--gray-0);
  border:1px solid var(--gray-200);
  border-radius:var(--radius-lg);
}

/* ===== 状态页签（基线 .status-tabs）===== */
.status-tabs-row{
  display:flex;align-items:flex-end;gap:16px;margin-bottom:16px;
  border-bottom:1px solid var(--gray-200);
}
.status-tabs{
  display:flex;align-items:center;gap:24px;
  overflow-x:auto;scrollbar-width:none;flex:1;min-width:0;
}
/* 视图切换：与状态页签同一条底边，靠右；分段控件形态，区分"看哪套数据" */
.view-switch{
  display:flex;gap:2px;padding:2px;margin-bottom:6px;flex:0 0 auto;
  background:var(--gray-100);border-radius:var(--radius-sm);
}
.view-switch__btn{
  height:28px;padding:0 12px;cursor:pointer;white-space:nowrap;
  font-family:inherit;font-size:13px;color:var(--gray-700);
  background:transparent;border:0;border-radius:var(--radius-sm);
  transition:background var(--transition-fast),color var(--transition-fast);
}
.view-switch__btn:hover{color:var(--gray-900)}
.view-switch__btn.is-on{
  background:var(--gray-0);color:var(--primary-600);font-weight:650;
  box-shadow:var(--shadow-sm);
}
.status-tabs::-webkit-scrollbar{display:none}
.tab-button{
  position:relative;padding:10px 2px 12px;white-space:nowrap;
  font-family:inherit;font-size:14px;color:var(--gray-800);
  background:transparent;border:0;cursor:pointer;
}
.tab-button:hover{color:var(--primary-600)}
.tab-button.active{color:var(--primary-500);font-weight:700}
.tab-button.active::after{
  content:"";position:absolute;right:0;bottom:-1px;left:0;height:2px;background:var(--primary-500);
}
.tab-count{margin-left:5px;color:var(--gray-500);font-weight:400}

/* ===== 状态胶囊（基线 .status）===== */
.status{
  display:inline-flex;align-items:center;padding:3px 8px;
  border-radius:var(--radius-pill);font-size:12px;font-weight:650;
  background:var(--disabled-soft);color:var(--gray-500);
}
.status.pending{color:var(--warning-500);background:var(--warning-soft)}
.status.enabled{color:var(--success-500);background:var(--success-soft)}
.status.disabled{color:var(--gray-400);background:var(--disabled-soft)}
.status.rejected{color:var(--error-500);background:var(--danger-soft)}
.status.info{color:var(--primary-600);background:var(--primary-50)}

/* ===== 查询面板（基线 .query-panel）===== */
.query-panel{
  display:grid;grid-template-columns:repeat(3,minmax(150px,1fr)) auto;
  align-items:end;gap:12px;padding:14px;margin-bottom:14px;
  background:var(--surface-sunken);
  border:1px solid var(--gray-200);border-radius:var(--radius-lg);
}
.query-panel.is-collapsed .field-extra{display:none}
.query-panel.is-collapsed .query-actions{grid-column:1 / -1;justify-content:flex-end}
.field-group{display:grid;gap:5px;min-width:0}
.field-group>label{font-size:12px;font-weight:650;color:var(--gray-600)}
.field-group.field-wide{grid-column:1 / -1}
.date-range{display:flex;align-items:center;gap:6px;min-width:0}
.date-range>span{flex:0 0 auto;color:var(--gray-500);font-size:12px}
.date-range .field{min-width:0}
.query-actions{display:flex;align-items:center;flex-wrap:wrap;gap:8px}
.query-toggle{
  display:inline-flex;align-items:center;gap:4px;padding:0 4px;
  border:0;background:transparent;cursor:pointer;
  border-radius:var(--radius-sm);color:var(--primary-600);font-family:inherit;
  font-size:13px;white-space:nowrap;
}
.query-toggle:hover{color:var(--primary-700)}
.query-toggle-icon{display:inline-flex;transition:transform var(--transition-fast)}
.query-toggle-icon svg{width:14px;height:14px;display:block}
.query-toggle-icon.is-up{transform:rotate(180deg)}

/* ===== 表单控件（基线 .field）===== */
.field{
  height:36px;min-width:150px;padding:0 10px;width:100%;
  font-family:inherit;font-size:13px;color:var(--gray-900);
  background:var(--gray-0);outline:none;
  border:1px solid var(--gray-200);border-radius:var(--radius-md);
  transition:border-color var(--transition-fast),box-shadow var(--transition-fast);
}
.field::placeholder{color:var(--gray-400)}
.field:focus{border-color:var(--primary-500);box-shadow:0 0 0 3px var(--primary-50)}
select.field{cursor:pointer;padding-right:8px}

/* ===== 按钮（基线 .btn）===== */
.btn{
  display:inline-flex;align-items:center;justify-content:center;gap:6px;
  height:36px;padding:0 14px;cursor:pointer;white-space:nowrap;
  font-family:inherit;font-size:13px;font-weight:600;color:var(--gray-700);
  background:var(--gray-0);border:1px solid var(--gray-200);
  border-radius:var(--radius-md);
  transition:all var(--transition-fast);
}
.btn:hover{border-color:var(--primary-500);color:var(--primary-600)}
.btn.primary{background:var(--primary-600);border-color:var(--primary-600);color:#fff}
.btn.primary:hover{background:var(--primary-700);border-color:var(--primary-700);color:#fff}
.btn.ghost{background:transparent;border-color:var(--gray-200);color:var(--gray-600)}
.btn[disabled]{color:var(--gray-400);background:var(--gray-50);border-color:var(--gray-200);cursor:not-allowed}

/* ===== 操作栏（基线 .action-bar）===== */
.action-bar{
  display:flex;flex-wrap:wrap;align-items:center;justify-content:space-between;
  gap:12px;margin-bottom:12px;
}
.action-bar .row-actions{display:flex;flex-wrap:wrap;align-items:center;gap:8px}
.selected-count{font-size:12px;color:var(--gray-500)}
.selected-count b{color:var(--primary-600)}
.muted{color:var(--gray-500);font-size:13px}

/* ===== 数据表格（基线 .table-wrap / .dt）===== */
.table-wrap{
  width:100%;max-width:100%;overflow:auto;
  background:var(--gray-0);
  border:1px solid var(--gray-200);border-radius:var(--radius-lg);
  scrollbar-width:thin;
}
.dt{
  width:100%;min-width:940px;border-collapse:collapse;
  font-size:13px;background:var(--gray-0);
}
.dt th,.dt td{
  padding:11px 12px;text-align:left;vertical-align:middle;
  border-bottom:1px solid var(--gray-200);
}
.dt th{
  background:var(--surface-sunken);color:var(--gray-600);
  font-size:12px;font-weight:650;white-space:nowrap;
}
.dt tbody tr:last-child td{border-bottom:0}
.dt tbody tr:hover td{background:var(--row-hover)}
.dt tbody tr.selected td{background:var(--row-selected)}

/* 固定列：横向滚动时复选框列吸左、操作列吸右 */
.dt th.sticky-col-left,.dt td.sticky-col-left{
  position:sticky;left:0;z-index:2;background:var(--gray-0);
  box-shadow:6px 0 8px -8px rgb(15 23 42 / .3);
}
.dt th.sticky-col-right,.dt td.sticky-col-right{
  position:sticky;right:0;z-index:2;background:var(--gray-0);
  box-shadow:-6px 0 8px -8px rgb(15 23 42 / .3);
}
.dt th.sticky-col-left,.dt th.sticky-col-right{z-index:3;background:var(--surface-sunken)}
.dt tbody tr:hover td.sticky-col-left,
.dt tbody tr:hover td.sticky-col-right{background:var(--row-hover)}
.dt tbody tr.selected td.sticky-col-left,
.dt tbody tr.selected td.sticky-col-right{background:var(--row-selected)}

.dt .col-check{width:44px;padding-left:14px;padding-right:0}
.dt .col-num{font-variant-numeric:tabular-nums;text-align:right}
.dt .col-strong{font-weight:650;color:var(--gray-900)}
.dt .col-muted{color:var(--gray-500)}
.dt td.col-act{white-space:nowrap}
.dt td .row-actions{
  display:grid;grid-template-columns:repeat(3,max-content);
  align-items:center;justify-content:start;gap:6px 14px;
}
.dt .cell-link{
  color:var(--primary-600);font-weight:650;
  text-decoration:underline;text-underline-offset:2px;
  background:transparent;border:0;padding:0;cursor:pointer;font-family:inherit;font-size:13px;
}
.dt .cell-link:hover{color:var(--primary-700)}

/* 进度单元格 */
.progress-cell{display:inline-flex;align-items:center;min-width:132px;gap:6px}
.progress-track{flex:1 1 auto;height:6px;overflow:hidden;background:var(--gray-200);border-radius:3px}
.progress-fill{display:block;height:100%;background:var(--primary-500);border-radius:3px}
.progress-cell.is-over .progress-fill{background:var(--error-500)}
.progress-text{flex:0 0 auto;color:var(--gray-500);font-size:12px;white-space:nowrap}

/* 表内空态 */
.dt-empty td,.dt-empty:hover td{
  text-align:center;padding:56px 12px;color:var(--gray-500);background:none;
}
.dt-empty .empty-mini-title{display:block;font-size:14px;font-weight:650;color:var(--gray-700);margin-bottom:4px}
.dt-empty .empty-mini-desc{display:block;font-size:12px;color:var(--gray-500)}

/* ===== 分页（基线 .pager-bar）===== */
.pager-bar{
  display:flex;align-items:center;justify-content:space-between;gap:12px;
  padding:14px 0 0;flex-wrap:wrap;
}
.pager-total{font-size:12px;color:var(--gray-500)}
.pager{display:flex;align-items:center;gap:4px}
.pager button{
  min-width:30px;height:30px;padding:0 8px;cursor:pointer;
  font-family:inherit;font-size:12px;color:var(--gray-700);
  background:var(--gray-0);border:1px solid var(--gray-200);
  border-radius:var(--radius-sm);transition:all var(--transition-fast);
}
.pager button:hover{border-color:var(--primary-500);color:var(--primary-600)}
.pager button.active{
  background:var(--primary-50);border-color:var(--primary-200);
  color:var(--primary-600);font-weight:650;
}
.pager button[disabled]{
  color:var(--gray-400);border-color:var(--gray-200);
  background:var(--gray-50);cursor:not-allowed;
}
.pager-right{display:flex;align-items:center;gap:14px;flex-wrap:wrap}
.pager-gap{padding:0 2px;color:var(--gray-400);font-size:12px}
.pager-size,.pager-jump{
  display:flex;align-items:center;gap:6px;
  font-size:12px;color:var(--gray-500);white-space:nowrap;
}
.pager-size select,.pager-jump input{
  height:30px;padding:0 6px;font-family:inherit;font-size:12px;
  color:var(--gray-800);background:var(--gray-0);
  border:1px solid var(--gray-200);border-radius:var(--radius-sm);
}
.pager-jump input{width:48px;text-align:center}
.pager-jump button{
  height:30px;padding:0 10px;cursor:pointer;font-family:inherit;font-size:12px;
  color:var(--gray-700);background:var(--gray-0);
  border:1px solid var(--gray-200);border-radius:var(--radius-sm);
}
.pager-jump button:hover{border-color:var(--primary-500);color:var(--primary-600)}
tr[hidden]{display:none}

@media (max-width: 900px){
  .page-card{min-height:0;padding:14px}
  .query-panel{grid-template-columns:1fr}
  .query-panel .query-actions{grid-column:1 / -1;justify-content:flex-end}
  .field-group.field-time{grid-column:auto}
}
`;
