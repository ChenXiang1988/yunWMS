/**
 * PC 后台菜单树。
 *
 * 结构与文案照搬旧版产物 _archive/2026-10-29-handover/proto/admin_warehousedemo.html
 * 的 MENU（第 2672 行起）与 shell/fixed.js，不自行设计。
 * 旧版三层职责：
 *   侧栏一级 = 本数组项；侧栏二级 = children[]；页头三级页签 = 二级项的 children[]。
 * 二级项标 hideFromTertiaryTabs 时，页签只列它的 children，不含它自己。
 *
 * 按用户裁决，一级只保留「仓储库存」；其余分组本原型无页面，不摆出。
 * built: true 的三级项由 proto-next 共享数据真实渲染，其余走整页空态。
 */

/* 图标取自旧版 ICONS（admin_warehousedemo.html 第 2613 行起），原样搬运 */
export const ICONS = {
  package: '<svg class="ui-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.9" stroke-linecap="round" stroke-linejoin="round"><path d="M21 16V8a2 2 0 0 0-1-1.73l-7-4a2 2 0 0 0-2 0l-7 4A2 2 0 0 0 3 8v8a2 2 0 0 0 1 1.73l7 4a2 2 0 0 0 2 0l7-4A2 2 0 0 0 21 16z"/><polyline points="3.27 6.96 12 12.01 20.73 6.96"/><line x1="12" y1="22.08" x2="12" y2="12"/></svg>',
  chevronDown: '<svg class="ui-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="m7 10 5 5 5-5"/></svg>',
  chevronLeft: '<svg class="ui-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><polyline points="15 18 9 12 15 6"/></svg>',
  help: '<svg class="ui-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.9" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="9"/><path d="M9.1 9a3 3 0 0 1 5.8 1c0 2-3 3-3 3"/><path d="M12 17h.01"/></svg>',
};

/**
 * proto-next 页面 key → 旧版页面 key。
 * 旧版 41 个页面里本原型只实现了 6 个，映射保留旧 key 便于对照。
 */
export const PAGE_KEY_MAP = {
  'res-hardware-archive': 'hw-archive',
  'res-hardware': 'hardware',
  'res-hardware-transfer': 'hw-transfer',
  'res-exec-unit': 'exec-unit',
  'task-dashboard': 'dashboard',
  'wm-container': 'container',
  'wm-container-archive': 'container-archive',
  'inv-total': 'inventory',
  'wm-bin': 'bin',
};

export const MENU = [
  {
    key: 'warehouse', label: '仓储库存', icon: 'package',
    sections: [
      /* 总仓管理：总仓视图在前，配置页跟随。总仓/仓网随主体默认存在，
         不建独立主数据；单仓主体也先进总仓视图。字典只留一个入口。
         容器只在这里放「档案」——类型层按主体共享，管编码前缀、层级、
         允许子类型、规格与是否计资产；实例（某一只容器在哪个仓、装着什么）归单仓。 */
      {
        key: 'total', name: '总仓管理', hideFromTertiaryTabs: true,
        children: [
          { key: 'wm-network', name: '总仓视图' },
          { key: 'wm-warehouse', name: '仓库档案' },
          { key: 'res-hardware-archive', name: '设备档案', built: true },
          { key: 'res-hardware-transfer', name: '设备调拨', built: true },
          { key: 'wm-transfer', name: '跨仓调拨' },
          { key: 'wm-container-archive', name: '容器档案', built: true },
          { key: 'wm-dict', name: '字典管理' },
        ],
      },
      /* 单仓配置：一个仓怎么干活，资源与策略在这里配。
         按用户裁决，操作动作库 / 工序模板 / 单据工序编排 / 任务派生规则 /
         任务生成与分配 五个页签已从本原型移除。
         资源是现场作业的承担方：执行单元、设备、人员。
         设备发生在实体仓，总仓是管理节点不摆；工位设备与设备绑定
         都收在设备页内，作为与设备并列的视图，不各占一个页签。
         容器管理与库位管理是某个仓的现场台账：容器有位置、有载货、有占用单；
         库位是区-巷-排-列-层的物理格子。两者都只对实体仓成立，故归本段。 */
      {
        key: 'conf', name: '单仓配置', hideFromTertiaryTabs: true,
        children: [
          { key: 'wset-strategy', name: '策略管理' },
          { key: 'res-exec-unit', name: '执行单元', built: true },
          { key: 'res-hardware', name: '设备', built: true },
          { key: 'wm-container', name: '容器管理', built: true },
          { key: 'wm-bin', name: '库位管理', built: true },
          { key: 'res-staff', name: '人员' },
          { key: 'res-shift', name: '排班' },
        ],
      },
      /* 仓库单据：入库、出库、库内三个方向的主单据合到一组，按方向排页签。 */
      {
        key: 'docs', name: '仓库单据', hideFromTertiaryTabs: true,
        children: [
          { key: 'inbound-order', name: '入库单' },
          { key: 'out-order', name: '出库单' },
          { key: 'relocation-order', name: '移库单' },
          { key: 'replenish-list', name: '补货单' },
          { key: 'stocktaking-list', name: '盘点单' },
          { key: 'putaway-order', name: '上架单' },
        ],
      },
      /* 任务中心：执行看板与任务归口。主单据留在仓库单据。 */
      {
        key: 'task', name: '任务中心', hideFromTertiaryTabs: true,
        children: [
          { key: 'task-dashboard', name: '执行看板', built: true },
          { key: 'putaway-list', name: '上架作业任务' },
          { key: 'relocation-list', name: '移库作业任务' },
          { key: 'stocktaking-subtask', name: '盘点作业任务' },
          { key: 'out-wave', name: '波次' },
          { key: 'out-pick', name: '拣货任务' },
          { key: 'out-check', name: '复核作业任务' },
          { key: 'out-ship', name: '发货任务' },
        ],
      },
      /* 库存中心 */
      {
        key: 'inv', name: '库存中心', hideFromTertiaryTabs: true,
        children: [
          { key: 'inv-overview', name: '库存总览' },
          { key: 'inv-lowstock', name: '低库存预警' },
          { key: 'inv-distribution', name: '库存分布' },
          { key: 'inv-total', name: '商品库存', built: true },
          { key: 'inv-split', name: '分仓库存' },
          { key: 'inv-slot', name: '仓位库存' },
          { key: 'inv-batch', name: '批次库存' },
          { key: 'op-daily', name: '日清检查' },
          { key: 'op-opening', name: '初始库存' },
          { key: 'adjust-order', name: '库存调整任务' },
          { key: 'op-snapshot', name: '库存快照' },
          { key: 'trace-flow', name: '库存流水' },
        ],
      },
    ],
  },
];

/** 页头三级页签：二级项标 hideFromTertiaryTabs 时只列 children，否则含它自己 */
export function tabsOf(sec) {
  if (!sec) return [];
  if (!sec.children || !sec.children.length) return [sec];
  return sec.hideFromTertiaryTabs ? sec.children : [sec, ...sec.children];
}

/** 派生：页面 key → 所属一级 / 二级 */
export function buildIndex(menu = MENU) {
  const PAGE_LOC = {};
  const SECTIONS = {};
  for (const g of menu) {
    for (const sec of (g.sections || [])) {
      /* 带上 built：无页面的项在页签行里置灰不可点 */
      SECTIONS[sec.key] = tabsOf(sec).map((c) => ({ key: c.key, name: c.name, built: !!c.built }));
      for (const c of tabsOf(sec)) PAGE_LOC[c.key] = { top: g.key, sec: sec.key };
    }
  }
  return { PAGE_LOC, SECTIONS };
}

/** 本原型真实渲染的页面 key（旧 key → 内部 key） */
export function builtKeys(menu = MENU) {
  const out = new Set();
  for (const g of menu) {
    for (const sec of (g.sections || [])) {
      for (const c of (sec.children || [])) {
        if (c.built) out.add(PAGE_KEY_MAP[c.key] || c.key);
      }
    }
  }
  return out;
}
