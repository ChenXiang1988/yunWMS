/**
 * 容器档案（总仓管理 › 容器档案）。
 *
 * 与「单仓配置 › 容器管理」是两个页面，不得合并：
 *   容器档案 = 类型档案。按主体共享，管类型编码、编码前缀、层级、允许子类型、
 *              规格与能力、是否计资产。一个类型对应多只容器实例，
 *              这里不关心某一只实例在哪个仓、装着什么。
 *   单仓容器 = 实例台账。管本仓某一只容器的位置、载货、占用、父子关系与流转。
 *
 * 列名与列序照搬旧版产物 admin_warehousedemo.html 的 wm-container-type 定义：
 *   类型编码 / 类型名称 / 编码前缀 / 物理类别 / 层级 / 可装货 / 可承载下级 /
 *   允许子类型 / 可绑工位 / 计资产 / 容积（L）/ 最大载重（kg）/ 可堆叠层数 /
 *   适用工序 / 启用状态
 * 在其后补「实例数」一列：该类型下实际存在的容器数，数据来自 containers.json。
 * 旧版没有这一列，供总仓看类型的使用情况。
 *
 * 行内「实例分布」开抽屉，按仓列出该类型的实例数与明细，总仓由此看总量与闲置率。
 * 抽屉挂在 body 上（规范 §3），不写在 .page 内。
 */
import { ListPage, StatusTag } from '../../../shared/ui/l3.mjs';

const esc = (v) => String(v ?? '')
  .replace(/&/g, '&amp;').replace(/</g, '&lt;')
  .replace(/>/g, '&gt;').replace(/"/g, '&quot;');
const dash = (v) => (v === '' || v == null ? '—' : esc(v));
const num = (v) => (v === '' || v == null ? '—' : Number(v).toLocaleString('zh-CN'));
const uniq = (list) => [...new Set(list.filter((v) => v != null && v !== ''))].sort();

export function renderContainerArchivePage(types, containers = [], warehouses = []) {
  const entityWh = warehouses.filter((w) => w.isEntity).map((w) => w.name);
  const ofType = (name) => containers.filter((c) => c.contType === name);

  const rows = types.map((t) => {
    const list = ofType(t.typeName);
    const byWh = new Map();
    list.forEach((c) => {
      byWh.set(c.warehouse, (byWh.get(c.warehouse) || 0) + 1);
    });
    return {
      ...t,
      instanceCount: list.length,
      idleCount: list.filter((c) => c.status === 'idle').length,
      byWh,
      stateLabel: t.enabled === '是' ? '启用' : '停用',
    };
  });

  const enabled = rows.filter((r) => r.enabled === '是').length;
  const totalInstance = rows.reduce((s, r) => s + r.instanceCount, 0);

  const page = ListPage({
    statusTabs: [
      { label: '全部', count: rows.length, active: true },
      { label: '启用', count: enabled },
      { label: '停用', count: rows.length - enabled },
    ],
    filters: [
      { label: '类型名称', type: 'text', placeholder: '请输入类型名称或编码' },
      { label: '物理类别', type: 'select',
        options: ['全部', ...uniq(types.map((t) => t.physicalClass))] },
      { label: '容器层级', type: 'select', options: ['全部', '1', '2'], extra: true },
      { label: '可装货', type: 'select', options: ['全部', '是', '否'], extra: true },
      { label: '可承载下级', type: 'select', options: ['全部', '是', '否'], extra: true },
      { label: '计资产', type: 'select', options: ['全部', '是', '否'], extra: true },
      { label: '启用状态', type: 'select', options: ['全部', '是', '否'], extra: true },
    ],
    toolbar: [
      { label: '新建类型（待开放）', primary: true, disabled: true },
      { label: '导出数据', ghost: true },
    ],
    showCount: true,
    unit: '个',
    scopeNote: `共 ${rows.length} 个类型 · ${totalInstance} 只容器`,
    columns: [
      { key: 'typeCode', label: '类型编码', width: '110px', cls: 'col-strong' },
      { key: 'typeName', label: '类型名称', width: '140px' },
      { key: 'prefix', label: '编码前缀', width: '90px' },
      { key: 'physicalClass', label: '物理类别', width: '100px' },
      { key: 'level', label: '层级', width: '70px', align: 'right' },
      { key: 'loadable', label: '可装货', width: '80px' },
      { key: 'canContain', label: '可承载下级', width: '110px' },
      { key: 'allowedChildTypesLabel', label: '允许子类型', width: '260px',
        render: (r) => dash(r.allowedChildTypesLabel) },
      { key: 'bindable', label: '可绑工位', width: '90px' },
      { key: 'assetTracked', label: '计资产', width: '80px' },
      { key: 'capacityL', label: '容积（L）', width: '90px', align: 'right',
        render: (r) => num(r.capacityL) },
      { key: 'maxLoadKg', label: '最大载重（kg）', width: '110px', align: 'right',
        render: (r) => num(r.maxLoadKg) },
      { key: 'stackLayers', label: '可堆叠层数', width: '100px', align: 'right',
        render: (r) => num(r.stackLayers) },
      { key: 'processLabel', label: '适用工序', width: '220px', render: (r) => dash(r.processLabel) },
      { key: 'instanceCount', label: '实例数', width: '90px', align: 'right',
        render: (r) => `${r.instanceCount} 只` },
      { key: 'stateLabel', label: '启用状态', width: '100px',
        render: (r) => StatusTag(r.stateLabel, r.enabled === '是' ? 'enabled' : 'disabled') },
      { key: 'op', label: '操作', width: '100px', fixed: true,
        render: (r) => `<button class="cta-link" data-cont-type="${esc(r.typeCode)}">实例分布</button>` },
    ],
    rows,
    emptyTitle: '暂无容器类型',
    emptyDesc: '点击上方「新建类型」开始录入。',
  });

  return { html: page, overlay: drawerPayload(rows, entityWh) };
}

/* ---------- 抽屉：实例分布 ---------- */

function drawerPayload(rows, entityWh) {
  const data = rows.map((r) => ({
    code: r.typeCode,
    name: r.typeName,
    unit: r.physicalClass,
    total: r.instanceCount,
    idle: r.idleCount,
    byWh: entityWh.map((w) => ({ wh: w, count: r.byWh.get(w) || 0 })),
    outside: [...r.byWh.entries()]
      .filter(([w]) => !entityWh.includes(w))
      .map(([wh, count]) => ({ wh, count })),
  }));

  return `
<div class="cta-drawer-mask" id="ctaMask" hidden></div>
<aside class="cta-drawer" id="ctaDrawer" hidden>
  <div class="cta-drawer-head">
    <h2 id="ctaTitle">实例分布</h2>
    <button type="button" class="cta-drawer-x" data-cta-close aria-label="关闭">×</button>
  </div>
  <div class="cta-drawer-body" id="ctaBody"></div>
</aside>
<script>
(function(){
  var DATA = ${JSON.stringify(data)};
  var mask = document.getElementById('ctaMask');
  var box = document.getElementById('ctaDrawer');
  var title = document.getElementById('ctaTitle');
  var body = document.getElementById('ctaBody');

  function esc(s){ return String(s == null ? '' : s).replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;'); }

  function whTable(m){
    var rows = m.byWh.map(function(x){
      return '<tr><td>' + esc(x.wh) + '</td><td>' + x.count + ' 只</td></tr>';
    }).join('');
    var html = '<table class="dt cta-ver"><thead><tr><th>仓库</th><th>实例数</th></tr></thead><tbody>'
      + rows + '</tbody></table>';
    if (m.outside.length) {
      html += '<p class="cta-empty">不在实体仓的实例：'
        + m.outside.map(function(x){ return esc(x.wh) + ' ' + x.count + ' 只'; }).join('、')
        + '</p>';
    }
    return html;
  }

  function open(code){
    var m = DATA.filter(function(x){ return x.code === code; })[0];
    if (!m) return;
    title.textContent = m.name + ' · 实例分布';
    body.innerHTML = '<div class="cta-block"><h3>按仓分布</h3>' + whTable(m) + '</div>'
      + '<div class="cta-block"><h3>使用情况</h3>'
      + '<table class="dt cta-ver"><thead><tr><th>项</th><th>数量</th></tr></thead><tbody>'
      + '<tr><td>实例总数</td><td>' + m.total + ' 只</td></tr>'
      + '<tr><td>其中空闲</td><td>' + m.idle + ' 只</td></tr>'
      + '</tbody></table></div>';
    mask.hidden = false;
    box.hidden = false;
  }

  function close(){
    mask.hidden = true;
    box.hidden = true;
  }

  document.addEventListener('click', function(e){
    var t = e.target.closest && e.target.closest('[data-cont-type]');
    if (t) { open(t.getAttribute('data-cont-type')); return; }
    if (e.target.closest && e.target.closest('[data-cta-close]')) close();
    if (e.target === mask) close();
  });
  document.addEventListener('keydown', function(e){ if (e.key === 'Escape') close(); });
})();
</script>`;
}

/**
 * 本页自有样式。
 *
 * 类名统一用 cta- 前缀，不借用 hardware.mjs 的 hw-drawer 系列：
 * 那套是全屏遮罩容器（position:fixed;inset:0），借来当右侧抽屉会被拉满整屏。
 * A.11.9.147 已记过一次同类事故（hwa-drawer 与 hw-drawer 同名）。
 * 页面模块必须自带样式定义，构建期的类名冲突校验才扫得到。
 */
export const CTA_CSS = `
.cta-drawer-mask{position:fixed;inset:0;background:var(--wds-color-mask,rgba(0,0,0,.45));z-index:300}
.cta-drawer-mask[hidden]{display:none}
.cta-drawer{
  position:fixed;top:0;right:0;bottom:0;width:min(720px,92vw);z-index:301;
  display:flex;flex-direction:column;background:var(--wds-color-background-container,var(--gray-0));
  border-left:1px solid var(--wds-color-border-secondary,var(--gray-200));
  box-shadow:var(--wds-size-shadow-lg,var(--shadow-lg));
}
.cta-drawer[hidden]{display:none}
.cta-drawer-head{
  display:flex;align-items:center;justify-content:space-between;gap:12px;
  padding:16px 20px;border-bottom:1px solid var(--wds-color-border-secondary,var(--gray-200));flex-shrink:0;
}
.cta-drawer-head h2{margin:0;font-size:16px;font-weight:650;color:var(--wds-color-text-primary,var(--gray-900))}
.cta-drawer-x{
  width:28px;height:28px;display:grid;place-items:center;cursor:pointer;
  border:0;background:transparent;color:var(--wds-color-text-tertiary,var(--gray-500));font-size:20px;line-height:1;
  border-radius:var(--wds-size-radius-sm,var(--radius-sm));
}
.cta-drawer-x:hover{background:var(--wds-color-background-hover,var(--gray-100))}
.cta-drawer-body{flex:1;overflow:auto;padding:20px}
.cta-block{margin-bottom:22px}
.cta-block h3{margin:0 0 10px;font-size:13px;font-weight:650;color:var(--wds-color-text-primary,var(--gray-800))}
.cta-ver{width:100%;border-collapse:collapse}
.cta-ver th,.cta-ver td{
  padding:8px 10px;text-align:left;font-size:13px;
  border-bottom:1px solid var(--wds-color-border-secondary,var(--gray-100));
  color:var(--wds-color-text-primary,var(--gray-800));
}
.cta-ver th{color:var(--wds-color-text-tertiary,var(--gray-500));font-weight:600;
  background:var(--wds-color-background-subtle,var(--gray-50))}
.cta-ver td:last-child,.cta-ver th:last-child{text-align:right}
.cta-empty{margin:8px 0 0;font-size:12px;color:var(--wds-color-text-tertiary,var(--gray-500))}
.cta-link{border:0;background:transparent;padding:0;cursor:pointer;font:inherit;
  color:var(--wds-color-link,var(--primary-600))}
.cta-link:hover{text-decoration:underline}
`;
