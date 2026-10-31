/**
 * L4 应用布局。
 *
 * 结构、类名与样式照搬旧版产物
 * _archive/2026-10-29-handover/proto/admin_warehousedemo.html
 * （源见 _archive/.../proto/admin/src/shell/body.html 与 style.css），不自行设计。
 *
 *   顶栏 54px 整宽：品牌 | 全局搜索 | 工具区（采购车/收藏/消息/通知/用户/店铺切换）
 *   ├ 侧栏 188px（收起 64px），position:fixed；主区 margin-left 跟随
 *   │   一级 .nav-item（44px 高、图标 + 文案 + 折叠箭头），组间 .nav-divider
 *   │   二级 .nav-child（38px 高、缩进 35px）
 *   │   底部 .sidebar-footer：帮助 + 折叠两个小图标
 *   └ 主区
 *      ├ .page-header：三级页签通栏 + 右侧「仓库」作用域下拉
 *      └ .page-body（浅灰底，白卡浮上）：页标题行 / 内容 / 整页空态
 *
 * 导航只有两处出口：侧栏承载一级 + 二级，页头页签承载三级。
 */

/* 图标：照搬旧版 ICONS（admin_warehousedemo.html 第 2613 行起），
   统一 viewBox 24 / stroke-width 1.9 / round 端点，与旧版视觉一致。 */
const ICONS = {
  package: '<svg class="ui-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.9" stroke-linecap="round" stroke-linejoin="round"><path d="M21 16V8a2 2 0 0 0-1-1.73l-7-4a2 2 0 0 0-2 0l-7 4A2 2 0 0 0 3 8v8a2 2 0 0 0 1 1.73l7 4a2 2 0 0 0 2 0l7-4A2 2 0 0 0 21 16z"/><polyline points="3.27 6.96 12 12.01 20.73 6.96"/><line x1="12" y1="22.08" x2="12" y2="12"/></svg>',
  chevronDown: '<svg class="ui-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="m7 10 5 5 5-5"/></svg>',
  search: '<svg class="ui-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.9" stroke-linecap="round" stroke-linejoin="round"><circle cx="10.5" cy="10.5" r="6.5"/><path d="m16 16 5 5"/></svg>',
  close: '<svg class="ui-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round"><line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/></svg>',
  help: '<svg class="ui-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.9" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="9"/><path d="M9.1 9a3 3 0 0 1 5.8 1c0 2-3 3-3 3"/><path d="M12 17h.01"/></svg>',
};

const esc = (v) => String(v ?? '')
  .replace(/&/g, '&amp;').replace(/</g, '&lt;')
  .replace(/>/g, '&gt;').replace(/"/g, '&quot;');

export const EMPTY_ICON_SVG =
  '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round">'
  + '<rect x="3" y="3" width="18" height="18" rx="2" ry="2"/>'
  + '<line x1="9" y1="9" x2="15" y2="15"/><line x1="15" y1="9" x2="9" y2="15"/></svg>';

/**
 * @param {object} o
 * @param {Array}  o.menu        一级分组 [{ key, label, icon, sections: [{ key, name, children }] }]
 * @param {string} o.activeTop   当前一级 key
 * @param {string} o.activeSec   当前二级 key
 * @param {string} o.activePage  当前三级页 key
 * @param {Array}  o.open        初始展开的一级 key
 * @param {Array}  o.tabs        当前二级项的三级页签 [{ key, name }]
 * @param {string} o.content     主区内容
 * @param {string} o.logo        内联 SVG
 */
export function AppLayout({
  menu = [], activeTop = '', activeSec = '', activePage = '',
  open = [], tabs = [], content = '', logo = '',
  warehouses = [], warehouse = '', shopTypes = [], shop = '',
  pageTitle = '', pageActions = '',
} = {}) {
  /* 侧栏：一级 + 二级，组间分隔线 */
  const navItems = menu.map((g) => {
    const on = g.key === activeTop;
    const secs = g.sections || [];
    const expanded = open.includes(g.key) || on;
    const childList = secs.length
      ? `<div class="nav-children"${expanded ? '' : ' hidden'}>`
        + secs.map((s) => `<button type="button" class="nav-child${s.key === activeSec ? ' current' : ''}"`
          + ` data-nav-sec="${esc(s.key)}">${esc(s.name)}</button>`).join('')
        + `</div>`
      : '';
    const chev = secs.length
      ? `<span class="nav-chevron">${ICONS.chevronDown}</span>` : '';
    return `<button type="button" class="nav-item${on ? ' current' : ''}"`
      + ` data-nav="${esc(g.key)}"${secs.length ? ` aria-expanded="${expanded ? 'true' : 'false'}"` : ''}>`
      + `<span class="nav-icon">${ICONS[g.icon] || ICONS.package}</span>`
      + `<span class="nav-label">${esc(g.label)}</span>${chev}`
      + `</button>${childList}`;
  }).join('<div class="nav-divider"></div>');

  const tabsRow = tabs.length
    ? `<div class="tabs-row">${tabs.map((t) =>
        `<button type="button" class="tab${t.key === activePage ? ' active' : ''}${t.built === false ? ' is-off' : ''}" data-ctx="${esc(t.key)}"${t.built === false ? ' disabled' : ''}>${esc(t.name)}</button>`
      ).join('')}</div>`
    : '';

  const scope = warehouses.length
    ? `<div class="tabs-scope"><span>仓库</span>`
      + `<select id="warehouseSelect" aria-label="选择仓库">`
      + warehouses.map((w) => `<option${w === warehouse ? ' selected' : ''}>${esc(w)}</option>`).join('')
      + `</select></div>`
    : '';

  const shopSel = shopTypes.length
    ? `<div class="shop-switcher"><button class="shop-switcher-trigger" id="shopSwitcherTrigger">`
      + `<span id="shopSwitcherLabel">${esc(shop || shopTypes[0])}</span>${ICONS.chevronDown}</button>`
      + `<div class="shop-switcher-menu" id="shopSwitcherMenu"></div></div>`
    : '';

  return `<div class="app">
<header class="topbar">
  <a class="brand" href="#">
    <span class="brand-mark"><svg class="ui-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round"><path d="M3 9l9-7 9 7v11a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z"/><polyline points="9 22 9 12 15 12 15 22"/></svg></span>
    <span class="brand-copy"><strong class="brand-title">西之月</strong><small class="brand-sub">数字化跨境 B2B 平台</small></span>
  </a>

  <div class="global-search">
    ${ICONS.search}
    <input id="globalSearch" type="text" placeholder="搜索全站" aria-label="搜索全站">
    <button class="search-clear" id="globalSearchClear" title="清空" aria-label="清空关键词">${ICONS.close}</button>
  </div>

  <div class="topbar-tools">
    <button class="topbar-icon" title="采购车" data-toast="采购车：12 件待结算">
      <svg class="ui-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.75" stroke-linecap="round" stroke-linejoin="round"><circle cx="9" cy="20" r="1.4"/><circle cx="18" cy="20" r="1.4"/><path d="M2 3h3l2.2 11.2a2 2 0 0 0 2 1.6h8.6a2 2 0 0 0 2-1.6L21.5 7H6"/></svg>
    </button>
    <button class="topbar-icon" title="收藏夹" data-toast="收藏夹（原型示意）">
      <svg class="ui-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.75" stroke-linecap="round" stroke-linejoin="round"><path d="M20.8 5.6a5 5 0 0 0-7.1 0L12 7.3l-1.7-1.7a5 5 0 1 0-7.1 7.1l8.8 8.8 8.8-8.8a5 5 0 0 0 0-7.1z"/></svg>
    </button>
    <button class="topbar-icon" title="消息" data-toast="消息中心（原型示意）">
      <svg class="ui-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.75" stroke-linecap="round" stroke-linejoin="round"><path d="M21 11.5a8.4 8.4 0 0 1-9 8.4 9 9 0 0 1-3.3-.6L3 21l1.7-4.9A8.4 8.4 0 0 1 3 11.5 8.4 8.4 0 0 1 12 3a8.4 8.4 0 0 1 9 8.5z"/></svg>
    </button>
    <button class="topbar-icon notification-icon" title="通知" data-toast="3 条未读通知">
      <svg class="ui-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.75" stroke-linecap="round" stroke-linejoin="round"><path d="M18 8A6 6 0 0 0 6 8c0 7-3 9-3 9h18s-3-2-3-9"/><path d="M13.73 21a2 2 0 0 1-3.46 0"/></svg>
    </button>

    <div class="user-profile" title="个人中心" data-toast="个人中心（原型示意）">
      <span class="user-avatar">商</span><span class="user-name">陈明</span>
      <span class="profile-caret">${ICONS.chevronDown}</span>
    </div>

    ${shopSel}
  </div>
</header>

<div class="app-shell">
  <aside class="sidebar" id="sidebar">
    <div class="sidebar-scroll" id="sidebarMenu">${navItems}</div>
    <div class="sidebar-footer">
      <button id="helpBtn" title="帮助中心" aria-label="帮助中心">${ICONS.help}</button>
      <button id="collapseBtn" title="收起 / 展开侧栏" aria-label="收起或展开侧栏">
        <svg class="ui-icon collapse-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><polyline points="15 18 9 12 15 6"/></svg>
      </button>
    </div>
  </aside>

  <div class="sidebar-overlay" id="sidebarOverlay"></div>

  <main class="main">
    <div class="page-header">
      ${tabsRow}
      ${scope}
    </div>

    <div class="page-body">
      <div class="page-title-bar" id="pageTitleBar">
        <h1 class="page-title" id="pageTitle">${esc(pageTitle)}</h1>
        <div class="page-title-actions" id="pageTitleActions">${pageActions || ''}</div>
      </div>

      <div class="empty-state" id="emptyState" hidden>
        <div>
          <div class="empty-icon">${EMPTY_ICON_SVG}</div>
          <div class="empty-title" data-blank-title>暂未设计</div>
          <div class="empty-desc" data-blank-desc>该模块的功能页面待填充。</div>
        </div>
      </div>

      <div id="pageContent">${content}</div>
    </div>
  </main>
</div>

<div class="toast-wrap" id="toastWrap"></div>
</div>`;
}

export function Page({ key, html, hidden = false }) {
  return `<div class="page" data-page="${esc(key)}"${hidden ? ' hidden' : ''}>${html}</div>`;
}

export const L4_CSS = `
/* ===== 外壳 ===== */
.app{min-height:100vh;background:var(--gray-50);color:var(--gray-900);
  font-family:system-ui,-apple-system,"Segoe UI","PingFang SC","Hiragino Sans GB","Microsoft YaHei",sans-serif}
.ui-icon{width:17px;height:17px;display:block;flex:0 0 auto}

/* ===== 顶栏 54px ===== */
.topbar{
  position:fixed;top:0;left:0;right:0;height:var(--topbar-height);z-index:100;
  display:grid;grid-template-columns:auto 1fr auto;align-items:center;gap:20px;
  padding:0 20px;background:var(--gray-0);border-bottom:1px solid var(--gray-200);
}
.brand{display:flex;align-items:center;gap:10px;text-decoration:none;color:inherit}
.brand-mark{width:32px;height:32px;display:grid;place-items:center;
  border-radius:var(--radius-lg);background:var(--primary-50);color:var(--primary-600)}
.brand-copy{display:flex;flex-direction:column;line-height:1.15}
.brand-title{font-size:15px;font-weight:700;color:var(--gray-900)}
.brand-sub{font-size:11px;color:var(--gray-400)}

.global-search{
  display:flex;align-items:center;gap:8px;max-width:480px;
  height:36px;padding:0 10px;border:1px solid var(--gray-200);
  border-radius:var(--radius-pill);background:var(--gray-0);
}
.global-search .ui-icon{width:16px;height:16px;color:var(--gray-400)}
.global-search input{
  flex:1;min-width:0;border:0;outline:none;background:transparent;
  font-family:inherit;font-size:13px;color:var(--gray-900);
}
.global-search input::placeholder{color:var(--gray-400)}
.search-clear{border:0;background:transparent;color:var(--gray-400);cursor:pointer;padding:0;display:none}
.search-clear .ui-icon{width:14px;height:14px}
.global-search.has-value .search-clear{display:block}

.topbar-tools{display:flex;align-items:center;gap:6px}
.topbar-icon{
  width:32px;height:32px;display:grid;place-items:center;padding:0;cursor:pointer;
  border:0;background:transparent;color:var(--gray-600);border-radius:var(--radius-md);
}
.topbar-icon:hover{background:var(--gray-50);color:var(--gray-900)}
.topbar-icon .ui-icon{width:18px;height:18px}

.user-profile{display:flex;align-items:center;gap:8px;padding:4px 8px;cursor:pointer;
  border-radius:var(--radius-md);margin-left:4px}
.user-profile:hover{background:var(--gray-50)}
.user-avatar{width:26px;height:26px;display:grid;place-items:center;border-radius:var(--radius-pill);
  background:var(--primary-500);color:#fff;font-size:12px;font-weight:700}
.user-name{font-size:13px;color:var(--gray-800);font-weight:600}
.profile-caret{display:grid;place-items:center;color:var(--gray-400)}
.profile-caret .ui-icon{width:14px;height:14px}

.shop-switcher{position:relative;margin-left:6px}
.shop-switcher-trigger{
  display:flex;align-items:center;gap:6px;height:32px;padding:0 10px;cursor:pointer;
  background:var(--gray-0);border:1px solid var(--gray-200);border-radius:var(--radius-md);
  font-family:inherit;font-size:13px;color:var(--gray-800);font-weight:600;
}
.shop-switcher-trigger:hover{border-color:var(--primary-500);color:var(--primary-600)}
.shop-switcher-trigger .ui-icon{width:14px;height:14px}
.shop-switcher-menu{display:none;position:absolute;top:calc(100% + 4px);right:0;min-width:170px;
  background:var(--gray-0);border:1px solid var(--gray-200);border-radius:var(--radius-lg);
  box-shadow:var(--shadow-md);padding:6px;z-index:120}
.shop-switcher-menu.open{display:block}

/* ===== 侧栏：fixed + 主区 margin-left 跟随 ===== */
/* 固定高度而非 min-height：页体成为唯一滚动容器，页头页签不随列表滚走 */
.app-shell{display:flex;margin-top:var(--topbar-height);height:calc(100vh - var(--topbar-height))}
.sidebar{
  position:fixed;top:var(--topbar-height);left:0;bottom:0;width:var(--sidebar-width);
  background:var(--gray-0);border-right:1px solid var(--gray-200);
  display:flex;flex-direction:column;z-index:90;
  transition:width var(--transition-normal);overflow:hidden;
}
.sidebar.collapsed{width:var(--sidebar-collapsed-width)}
.sidebar-scroll{flex:1;overflow-y:auto;overflow-x:hidden;padding:6px 8px 8px}

.nav-item,.nav-child{
  display:flex;align-items:center;width:100%;text-align:left;white-space:nowrap;
  background:transparent;border:0;border-radius:var(--radius-md);cursor:pointer;
  font-family:inherit;
  transition:background var(--transition-fast),color var(--transition-fast);
}
.nav-item{min-height:44px;padding:0 8px;gap:10px;font-size:14px;font-weight:550;color:var(--gray-800)}
.nav-child{min-height:38px;padding:0 8px 0 35px;font-size:13px;font-weight:500;color:var(--gray-700)}
.nav-item:hover,.nav-child:hover{background:var(--gray-50)}
.nav-item.current,.nav-child.current{color:var(--primary-500);background:var(--primary-50)}
.nav-icon{display:grid;place-items:center;width:20px;flex:0 0 auto;color:var(--gray-500)}
.nav-item.current .nav-icon,.nav-child.current .nav-icon{color:var(--primary-500)}
.nav-label{flex:1;overflow:hidden;text-overflow:ellipsis}
.nav-chevron{margin-left:auto;display:grid;place-items:center;color:var(--gray-400);
  transition:transform var(--transition-fast)}
.nav-chevron .ui-icon{width:14px;height:14px}
.nav-item[aria-expanded="true"] .nav-chevron{transform:rotate(180deg)}
.nav-children[hidden]{display:none}
.nav-divider{height:1px;margin:6px 8px;background:var(--gray-200)}

.sidebar.collapsed .nav-label,
.sidebar.collapsed .nav-chevron,
.sidebar.collapsed .nav-children,
.sidebar.collapsed .nav-divider{display:none}
.sidebar.collapsed .nav-item{justify-content:center;padding:0}
.sidebar.collapsed ~ .main{margin-left:var(--sidebar-collapsed-width)}

.sidebar-footer{
  display:flex;justify-content:flex-end;gap:12px;padding:8px 12px 6px;flex-shrink:0;
  border-top:1px solid var(--gray-100);
}
.sidebar-footer button{
  width:24px;height:28px;display:grid;place-items:center;padding:0;cursor:pointer;
  border:0;background:transparent;color:var(--gray-500);border-radius:var(--radius-sm);
}
.sidebar-footer button:hover{background:var(--gray-100);color:var(--gray-800)}
.sidebar-footer .ui-icon{width:16px;height:16px}
.collapse-icon{transition:transform var(--transition-fast)}
.sidebar.collapsed .collapse-icon{transform:rotate(180deg)}

/* ===== 主区 ===== */
.main{flex:1;margin-left:var(--sidebar-width);display:flex;flex-direction:column;
  min-width:0;min-height:0;transition:margin-left var(--transition-normal)}

/* 页头：三级页签通栏 + 右侧作用域 */
.page-header{
  display:flex;align-items:center;gap:24px;padding:0 20px;flex-shrink:0;
  background:var(--gray-0);border-bottom:1px solid var(--gray-200);
}
.tabs-row{display:flex;align-items:center;flex-wrap:wrap;flex:1;min-width:0;column-gap:28px;row-gap:0}
.tab{
  position:relative;min-height:54px;padding:0 2px;cursor:pointer;white-space:nowrap;
  border:0;background:transparent;font-family:inherit;font-size:15px;color:var(--gray-900);
  transition:color var(--transition-fast);
}
.tab:hover{color:var(--primary-600)}
.tab.active{color:var(--primary-500);font-weight:700}
.tab.active::after{
  content:"";position:absolute;left:0;right:0;bottom:-1px;height:3px;
  background:var(--primary-500);border-radius:3px 3px 0 0;
}
/* 未实现的页面：置灰不可点，不进空态页 */
.tab.is-off{color:var(--gray-400);cursor:not-allowed}
.tab.is-off:hover{color:var(--gray-400)}
.tab[disabled]{cursor:not-allowed}
.tabs-scope{display:flex;align-items:center;gap:8px;flex:0 0 auto;
  color:var(--gray-500);font-size:13px;white-space:nowrap}
.tabs-scope select{
  height:32px;padding:0 8px;font-family:inherit;font-size:13px;
  background:var(--gray-0);color:var(--gray-800);
  border:1px solid var(--gray-200);border-radius:var(--radius-md);
}

/* 页体：浅灰底，白色卡片浮在上面 */
.page-body{flex:1;min-height:0;padding:20px;overflow:auto;background:var(--gray-50)}

.page-title-bar{display:flex;align-items:flex-start;justify-content:space-between;
  gap:16px;margin:0 0 18px}
.page-title{font-size:22px;font-weight:700;color:var(--gray-900);letter-spacing:-.4px;
  flex:1;min-width:0;margin:0}
.page-title-actions{display:flex;align-items:center;gap:8px;flex-shrink:0}
.page-title-bar.is-hidden{display:none}
.page-title-bar.is-title-hidden{justify-content:flex-end}
.page-title-bar.is-title-hidden .page-title{display:none}

/* 整页空态 */
.empty-state{display:grid;place-items:center;min-height:280px;color:var(--gray-500);text-align:center}
.empty-state[hidden]{display:none}
.empty-icon{width:54px;height:54px;margin:0 auto 14px;border-radius:var(--radius-lg);
  background:var(--primary-50);color:var(--primary-500);display:grid;place-items:center}
.empty-icon svg{width:24px;height:24px}
.empty-title{font-size:15px;font-weight:650;color:var(--gray-700);margin-bottom:6px}
.empty-desc{font-size:13px;color:var(--gray-500);max-width:420px}

/* 页面容器 */
.page{display:block}
.page[hidden]{display:none}

/* Toast */
.toast-wrap{position:fixed;top:calc(var(--topbar-height) + 12px);left:50%;transform:translateX(-50%);
  z-index:400;display:flex;flex-direction:column;gap:8px;align-items:center;pointer-events:none}

@media (max-width: 900px){
  .sidebar{transform:translateX(-100%);width:var(--sidebar-width)}
  .sidebar.mobile-open{transform:translateX(0)}
  .sidebar.collapsed{width:var(--sidebar-width)}
  .sidebar.collapsed .nav-label,.sidebar.collapsed .nav-chevron,
  .sidebar.collapsed .nav-children,.sidebar.collapsed .nav-divider{display:block}
  .sidebar.collapsed .nav-children[hidden]{display:none}
  .sidebar.collapsed .nav-item{justify-content:flex-start;padding:0 8px}
  .main,.sidebar.collapsed ~ .main{margin-left:0}
  .global-search{display:none}
  .page-body{padding:12px}
  .page-title-bar{align-items:stretch;flex-direction:column}
}

/* Toast */
.toast{
  padding:8px 14px;border-radius:var(--radius-md);
  background:var(--gray-900);color:#fff;font-size:13px;
  box-shadow:var(--shadow-md);opacity:1;transition:opacity var(--transition-fast);
}
.toast.is-out{opacity:0}
.dropdown-item{
  display:block;width:100%;text-align:left;padding:8px 10px;cursor:pointer;
  border:0;background:transparent;font-family:inherit;font-size:13px;
  color:var(--gray-700);border-radius:var(--radius-md);
}
.dropdown-item:hover{background:var(--gray-50);color:var(--gray-900)}
`;
