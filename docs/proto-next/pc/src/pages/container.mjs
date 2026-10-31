/**
 * 容器管理页（总仓管理 › 容器管理）。
 *
 * 列名、宽度、状态页签与状态类型照搬旧版产物
 * _archive/2026-10-29-handover/proto/admin_warehousedemo.html 的 wm-container 定义。
 * 数据取 shared/data/containers.json + container-types.json，不写死行。
 * 只负责取数与列定义，结构交给 L3 的 ListPage，本文件不拼 HTML、不写色值。
 */
import { ListPage, StatusTag } from '../../../shared/ui/l3.mjs';

const esc = (v) => String(v ?? '')
  .replace(/&/g, '&amp;').replace(/</g, '&lt;')
  .replace(/>/g, '&gt;').replace(/"/g, '&quot;');
const dash = (v) => (v === '' || v == null ? '—' : esc(v));

/* 状态类型映射：旧版 statusMeta 的五种取值 */
const STATUS_TYPE = { idle: 'disabled', inuse: 'enabled', repair: 'pending', scrapped: 'rejected' };
const OCCUPY_TYPE = { free: 'disabled', overtime: 'rejected' };

export function renderContainerPage(containers, types = []) {
  const typeName = new Map(types.map((t) => [t.typeName, t]));

  const count = (x) => containers.filter((r) => r.status === x).length;

  return ListPage({
    statusTabs: [
      { label: '全部', count: containers.length, active: true },
      { label: '空闲', count: count('idle'), field: 'status', value: 'idle' },
      { label: '在用', count: count('inuse'), field: 'status', value: 'inuse' },
      { label: '维修', count: count('repair'), field: 'status', value: 'repair' },
      { label: '报废', count: count('scrapped'), field: 'status', value: 'scrapped' },
      { label: '占用超时', count: containers.filter((r) => r.occupyStatus === 'overtime').length,
        field: 'occupyStatus', value: 'overtime' },
    ],
    filters: [
      { label: '容器编码', type: 'text', placeholder: '请输入容器编码' },
      { label: '容器类型', type: 'select', options: ['全部', ...uniq(containers.map((r) => r.contType))] },
      { label: '归属仓库', type: 'select', options: ['全部', ...uniq(containers.map((r) => r.warehouse))] },
      { label: '所属方', type: 'select', options: ['全部', ...uniq(containers.map((r) => r.owner))], extra: true },
      { label: '当前库区', type: 'text', placeholder: '请输入库区', extra: true },
      { label: '占用状态', type: 'select', options: ['全部', '未占用', '占用超时'], extra: true },
    ],
    toolbar: [
      { label: '新增容器', primary: true },
      { label: '批量回收' },
      { label: '导出数据', ghost: true },
    ],
    showCount: true,
    /* 单仓页：计数说明跟着页头仓库下拉走，由 shell/warehouse-scope.js 覆写 */
    scopeNote: '当前范围：—',
    columns: [
      { key: 'id', label: '容器编码', width: '120px', cls: 'col-strong' },
      { key: 'contType', label: '容器类型', width: '130px' },
      { key: 'containerLevelLabel', label: '容器层级', width: '90px', render: (r) => dash(r.containerLevelLabel) },
      { key: 'owner', label: '所属方', width: '90px' },
      /* 容积 / 载重 / 堆叠层数是容器类型的属性，行上不存，按类型回查 */
      { key: 'capacityL', label: '容积（L）', width: '90px', align: 'right',
        render: (r) => dash(typeOf(r, typeName).capacityL) },
      { key: 'maxLoadKg', label: '最大载重（kg）', width: '110px', align: 'right',
        render: (r) => dash(typeOf(r, typeName).maxLoadKg) },
      { key: 'stackLayers', label: '可堆叠层数', width: '100px', align: 'right',
        render: (r) => dash(typeOf(r, typeName).stackLayers) },
      { key: 'warehouse', label: '归属仓库', width: '100px' },
      { key: 'parentContainerCode', label: '上层容器', width: '120px', render: (r) => dash(r.parentContainerCode) },
      { key: 'zone', label: '当前库区', width: '110px', render: (r) => dash(r.zone) },
      { key: 'binCode', label: '当前库位', width: '120px', render: (r) => dash(r.binCode) },
      { key: 'loaded', label: '是否载货', width: '90px', render: (r) => dash(r.loaded) },
      { key: 'emptyReturnFlag', label: '待回收', width: '80px', render: (r) => dash(r.emptyReturnFlag) },
      { key: 'onBin', label: '是否占库位', width: '100px', render: (r) => dash(r.onBin) },
      { key: 'occupyStatus', label: '占用状态', width: '100px',
        render: (r) => StatusTag(r.occupyStatusLabel || '—', OCCUPY_TYPE[r.occupyStatus] || 'disabled') },
      { key: 'status', label: '状态', width: '90px',
        render: (r) => StatusTag(r.statusLabel || r.status, STATUS_TYPE[r.status] || 'disabled') },
      { key: 'currentTask', label: '当前任务', width: '190px', render: (r) => dash(r.currentTask) },
      { key: 'updatedAt', label: '更新时间', width: '140px', render: (r) => dash(r.updatedAt) },
    ],
    rows: containers,
    /* 单仓页：每行标出归属仓与状态口径，供页头仓库下拉过滤行、重算页签计数 */
    rowAttrs: (r) => ({
      'data-wh': r.warehouse,
      'data-status': r.status,
      'data-occupyStatus': r.occupyStatus,
    }),
    emptyTitle: '暂无容器',
    emptyDesc: '点击上方「新增容器」开始录入，或从其它系统导入。',
  });
}

/* ---------- 内部 ---------- */

function uniq(list) {
  return [...new Set(list.filter((v) => v != null && v !== ''))].sort();
}

function typeOf(row, typeName) {
  return typeName.get(row.contType) || {};
}
