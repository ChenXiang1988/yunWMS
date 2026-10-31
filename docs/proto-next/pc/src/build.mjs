#!/usr/bin/env node
/**
 * PC 后台构建。
 *
 * 数据边界：只允许读 ../../shared/data/。读同级 pda 目录时立即抛错。
 * 视觉基线：全部样式来自 shared/ui/tokens.css 与 l3.mjs，本文件不含色值。
 *
 *   node src/build.mjs            构建产物
 *   node src/build.mjs --check    漂移检查
 */
import fs from 'node:fs';
import path from 'node:path';
import crypto from 'node:crypto';
import { fileURLToPath } from 'node:url';
import { renderInventoryPage, verifyQtyRules } from './pages/inventory.mjs';
import { renderBinPage } from './pages/bins.mjs';
import { renderContainerPage } from './pages/container.mjs';
import { renderContainerArchivePage, CTA_CSS } from './pages/container-archive.mjs';
import { renderHardwarePage, renderHardwareTransferPage, hardwareGlobals, HW_CSS, WS_CSS, BIND_CSS } from './pages/hardware.mjs';
import { renderHardwareArchivePage, HWA_CSS } from './pages/hardware-archive.mjs';
import { renderExecUnitPage } from './pages/exec-unit.mjs';
import { computeMetrics, verifyMetrics, renderDashboardPage } from './pages/dashboard.mjs';
import { PageHeader, BaseTable, Tag, ListPage, StatusTag } from '../../shared/ui/l3.mjs';
import { L3_CSS } from '../../shared/ui/l3.mjs';
import { BASE_CSS } from '../../shared/ui/base.css.mjs';
import { BASELINE_TOKENS } from '../../shared/ui/baseline-tokens.css.mjs';
import { AppLayout, Page, L4_CSS } from '../../shared/ui/l4.mjs';
import { MENU, buildIndex, tabsOf, builtKeys, PAGE_KEY_MAP } from './menu.mjs';
import { verifyTaskRules } from '../../shared/verify-task-rules.mjs';
import { verifyDeviceRules } from '../../shared/verify-device-rules.mjs';
import { verifyClassNames } from '../../shared/verify-class-names.mjs';

const HERE = path.dirname(fileURLToPath(import.meta.url));
const SRC = path.resolve(HERE, '..');
const NEXT = path.resolve(SRC, '..');
const SHARED = path.join(NEXT, 'shared/data');
const UI = path.join(NEXT, 'shared/ui');
const PEER = path.join(NEXT, 'app');
const OUT = path.join(NEXT, 'dist', 'pc.html');

/* ---------- 数据边界 ---------- */

const OPENED = [];
for (const fn of ['readFileSync', 'existsSync']) {
  const orig = fs[fn];
  fs[fn] = function (p, ...rest) {
    if (typeof p === 'string') OPENED.push(path.resolve(p));
    return orig.call(fs, p, ...rest);
  };
}

function readShared(name) {
  const file = path.join(SHARED, name + '.json');
  if (!fs.existsSync(file)) throw new Error(`[数据边界] 共享数据不存在：${name}.json`);
  const rows = JSON.parse(fs.readFileSync(file, 'utf8'));
  if (!Array.isArray(rows)) throw new Error(`[数据边界] ${name}.json 必须是行数组`);
  return rows;
}

function assertNoPeerReads() {
  const bad = OPENED.filter((f) => f.startsWith(PEER + path.sep));
  if (bad.length) {
    throw new Error('[数据边界] 构建期读到同级对端目录：\n  ' + bad.join('\n  ') + '\n  两端只允许读 ../../shared/。');
  }
}

/* ---------- 页面 ---------- */



/* 页面切换：hash 驱动，无框架依赖。
 *
 * 导航只有两处出口，与旧版 admin shell 一致，不再并行：
 *   侧栏 = 一级分组 + 二级项。一级点击只做手风琴展开/收起，不跳页；
 *          二级点击进入该段，并把三级页交给页头页签行。
 *   页头页签行 = 三级页，是唯一的换页入口。
 *
 * PAGE_LOC: 页面 key → { top, sec }，用于反查高亮与面包屑。
 * SECTIONS: 二级 key → 三级页签列表 [{ key, name }]。
 */
/* 导航脚本：源码在 src/shell/nav.js，构建期注入页面定位表后内联进产物。
   与旧版 shell/fixed.js 同构 —— 不在这里内联大段脚本。 */
function navScript({ PAGE_LOC, SECTIONS, DEFAULT_PAGE }) {
  const src = fs.readFileSync(path.join(HERE, 'shell/nav.js'), 'utf8');
  return src
    .replace('__PAGE_LOC__', JSON.stringify(PAGE_LOC))
    .replace('__SECTIONS__', JSON.stringify(SECTIONS))
    .replace('__DEFAULT_PAGE__', JSON.stringify(DEFAULT_PAGE));
}


/* 分页脚本：源码在 src/shell/pager.js，构建期内联进产物 */
function pagerScript() {
  return fs.readFileSync(path.join(HERE, 'shell/pager.js'), 'utf8');
}

/* 仓库作用域脚本：源码在 src/shell/warehouse-scope.js，构建期内联进产物。
   单仓页的页头仓库下拉由它接管，见文件头注释。 */
function warehouseScopeScript() {
  return fs.readFileSync(path.join(HERE, 'shell/warehouse-scope.js'), 'utf8');
}

/* ---------- 主流程 ---------- */

function main() {
  assertNoPeerReads();

  const invRows = readShared('product-inventory');
  const qtyBad = verifyQtyRules(invRows);
  if (qtyBad.length) {
    throw new Error('[数量口径] 违规 ' + qtyBad.length + ' 条：\n  ' + qtyBad.join('\n  '));
  }

  const CSS = [
    fs.readFileSync(path.join(UI, 'tokens.css'), 'utf8'),
    BASELINE_TOKENS,
    fs.readFileSync(path.join(UI, 'antd.css'), 'utf8'),
    fs.readFileSync(path.join(UI, 'antd-wds-bridge.css'), 'utf8'),
    BASE_CSS,
    L3_CSS,
    L4_CSS,
    HW_CSS,
    WS_CSS,
    BIND_CSS,
    HWA_CSS,
    CTA_CSS,
  ].join('\n');

  const LOGO = fs.readFileSync(path.join(UI, 'assets/westmoon-logo.svg'), 'utf8');

  /* 任务模型一致性校验：违反业务约束即中止构建 */
  const taskCheck = verifyTaskRules({
    putaway: readShared('putaway-tasks'),
    relocation: readShared('relocation-tasks'),
    stocktaking: readShared('stocktaking-tasks'),
    units: readShared('exec-units'),
  });
  if (taskCheck.length) {
    throw new Error('[任务模型] 违规 ' + taskCheck.length + ' 条：\n  ' + taskCheck.join('\n  '));
  }

  /* 样式类名冲突校验：不同页面模块不得定义同名类 */
  const pageFiles = fs.readdirSync(path.join(SRC, 'src', 'pages')).filter((f) => f.endsWith('.mjs'));
  const clsCheck = verifyClassNames({
    modules: pageFiles.map((f) => ({
      name: f.replace(/\.mjs$/, ''),
      source: fs.readFileSync(path.join(SRC, 'src', 'pages', f), 'utf8'),
    })),
  });
  if (clsCheck.length) {
    throw new Error('[类名冲突] ' + clsCheck.length + ' 条：\n  ' + clsCheck.join('\n  '));
  }

  /* 设备模型一致性校验 */
  const devCheck = verifyDeviceRules({
    devices: readShared('hardware'),
    models: readShared('hardware-models'),
    classes: readShared('hardware-classes'),
    transfers: readShared('hardware-transfers'),
    bins: readShared('bins'),
    warehouses: readShared('warehouses'),
  });
  if (devCheck.length) {
    throw new Error('[设备模型] 违规 ' + devCheck.length + ' 条：\n  ' + devCheck.join('\n  '));
  }

  const binRows = readShared('bins');
  /* 仓库作用域下拉：只列实体仓。
     总仓是概念集合不是实体仓，虚拟仓与三方仓不承接现场作业，
     容器与库位只在自有实体仓成立（见 docs/产品边界.md BND-01）。
     名单取自 warehouses.json 的 isEntity，不手抄。 */
  const WAREHOUSES = readShared('warehouses').filter((w) => w.isEntity).map((w) => w.name).sort();
  const hw = { devices: readShared('hardware'), classes: readShared('hardware-classes'),
               models: readShared('hardware-models'), transfers: readShared('hardware-transfers'),
               templates: readShared('print-templates') };
  /* 页面模块可以返回字符串，也可以返回 { html, overlay }。
     overlay（抽屉、弹层）必须挂在 body 上，不能留在 .page 内——
     .page 切走时 display:none 会连带隐藏，导致切页后按钮失效（规范 §3）。 */
  const asPage = (r) => (typeof r === 'string' ? { html: r } : r);
  const OVERLAYS = [];

  const ENTITY_WH = new Set(WAREHOUSES);
  /* 单仓页只喂实体仓的行：虚拟仓与三方仓不承接现场作业，不应出现在
     容器管理与库位管理里（见 docs/容器与库位的归属分层.md 第三节）。
     数据文件本身不动，只在构建期过滤，避免页面出现永远选不到的筛选值。 */
  const containerRows = readShared('containers').filter((r) => ENTITY_WH.has(r.warehouse));
  const binRowsEntity = binRows.filter((r) => ENTITY_WH.has(r.warehouse));

  /* 看板指标：构建期由数据算出，页面不存任何手写数字。
     分组校验不通过即中止——看板数字自相矛盾比缺数字更糟。 */
  const DASHBOARD = computeMetrics({
    putaway: readShared('putaway-tasks'),
    relocation: readShared('relocation-tasks'),
    stocktaking: readShared('stocktaking-tasks'),
    units: readShared('exec-units'),
    warehouses: readShared('warehouses'),
  });
  const metricCheck = verifyMetrics(DASHBOARD);
  if (metricCheck.length) {
    throw new Error('[看板口径] 违规 ' + metricCheck.length + ' 条：\n  ' + metricCheck.join('\n  '));
  }

  const pages = [
    { key: 'container', title: '容器管理',
      html: renderContainerPage(containerRows, readShared('container-types')) },
    { key: 'container-archive', title: '容器档案',
      ...asPage(renderContainerArchivePage(readShared('container-types'), readShared('containers'), readShared('warehouses'))) },
    { key: 'bin', title: '库位管理', html: renderBinPage(binRowsEntity) },
    { key: 'inventory', title: '商品库存', html: renderInventoryPage(invRows, {}) },
    { key: 'hw-archive', title: '硬件档案', ...asPage(renderHardwareArchivePage(hw.models, hw.classes, hw.devices)) },
    { key: 'hardware', title: '设备', html: renderHardwarePage({ ...hw, bins: binRows, bindings: readShared('device-bindings') }) },
    { key: 'hw-transfer', title: '设备调拨', html: renderHardwareTransferPage(hw) },
    { key: 'exec-unit', title: '执行单元', html: renderExecUnitPage({ units: readShared('exec-units') }) },
    { key: 'dashboard', title: '执行看板', html: renderDashboardPage(DASHBOARD) },
  ];

  /* 菜单树来自 menu.mjs：结构与文案照搬旧版产物，不在这里另写一份。 */
  const { PAGE_LOC, SECTIONS } = buildIndex(MENU);
  const BUILT = builtKeys(MENU);

  /* 内部页面 key 与旧版菜单 key 不同，这里做一次映射。
     页面的 data-page 用旧版 key，便于与旧版产物对照。 */
  /* 内部 key → 旧版菜单 key。一个页面可能对应菜单里的多个位置，
     这里按内部 key 精确反查，不靠遍历顺序。 */
  const KEY_BACK = Object.fromEntries(Object.entries(PAGE_KEY_MAP)
    .map(([oldKey, inner]) => [inner, oldKey]));
  const pagesOld = pages.map((p) => {
    if (p.overlay) OVERLAYS.push(p.overlay);
    return { ...p, oldKey: KEY_BACK[p.key] || p.key };
  });

  /* 首屏：总仓管理段的第一个已实现页 */
  const START_TOP = 'warehouse';
  const START_SEC = 'total';
  const startSec = MENU.find((m) => m.key === START_TOP).sections.find((s) => s.key === START_SEC);
  const startTabs = tabsOf(startSec).map((c) => ({ key: c.key, name: c.name, built: !!c.built }));
  const startPage = startTabs.map((t) => t.key).find((k) => BUILT.has(PAGE_KEY_MAP[k] || k)) || startTabs[0].key;
  const START = PAGE_LOC[startPage] || { top: START_TOP, sec: START_SEC };

  /* 旧版固定名单：仓库取自 shared/data/bins.json，店铺类型照搬 [REPLACE-2] */
  const SHOP_TYPES = ['官方集采自营店', '官方工厂店', '平台工业品店', 'POP 店', '平台运营'];

  const firstPage = pagesOld.find((p) => p.oldKey === startPage);

  const body = AppLayout({
    menu: MENU,
    activeTop: START.top,
    activeSec: START.sec,
    activePage: startPage,
    open: [START.top],
    tabs: startTabs,
    warehouses: WAREHOUSES,
    warehouse: WAREHOUSES[0],
    shopTypes: SHOP_TYPES,
    shop: SHOP_TYPES[0],
    pageTitle: (firstPage && firstPage.title) || '',
    pageActions: '',
    content: pagesOld.map((p) => Page({ key: p.oldKey, html: p.html,
      hidden: p.oldKey !== startPage })).join('\n'),
  });

  const html = `<!doctype html>
<html lang="zh-CN" class="light" data-theme="default" data-density="standard">
<head>
<meta charset="utf-8">
<title>西之月 WMS · 原型</title>
<style>${CSS}</style>
</head>
<body>
${body}
${OVERLAYS.join('\n')}
${hardwareGlobals(hw)}
<script>${navScript({ PAGE_LOC, SECTIONS, DEFAULT_PAGE: startPage })}</script>
<script>${pagerScript()}</script>
<script>${warehouseScopeScript()}</script>
</body>
</html>`;

  const digest = crypto.createHash('sha256').update(html, 'utf8').digest('hex');

  if (process.argv.includes('--check')) {
    if (!fs.existsSync(OUT)) {
      console.error('漂移检查失败：产物不存在 ' + OUT);
      process.exit(1);
    }
    const cur = crypto.createHash('sha256').update(fs.readFileSync(OUT, 'utf8'), 'utf8').digest('hex');
    if (cur !== digest) {
      console.error('漂移检查失败\n  当前 ' + cur + '\n  期望 ' + digest);
      process.exit(1);
    }
    console.log('check ok   sha256=' + digest);
    return;
  }

  fs.mkdirSync(path.dirname(OUT), { recursive: true });
  fs.writeFileSync(OUT, html, 'utf8');
  console.log('built ' + path.relative(NEXT, OUT));
  console.log('  pages  = ' + pages.length);
  console.log('  sha256 = ' + digest);
}

main();
