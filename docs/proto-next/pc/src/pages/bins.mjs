/**
 * 库位管理页。
 *
 * 只负责取数与列定义，页面结构全部交给 L3 组件。
 * 不拼 HTML、不写样式、不出现色值。
 */
import { ListPage, StatusTag, Tag } from '../../../shared/ui/l3.mjs';

const n = (v) => Number(v || 0).toLocaleString('zh-CN');
const dash = (v) => (v === '' || v == null ? '—' : v);
/* 数据里的布尔是 1/0 整数，不是 true/false。统一归一，避免漏判。 */
const on = (v) => v === true || v === 1 || v === '1';

/* 库位类型语义：决定这个位子做什么作业。 */
const BIN_TYPE = {
  '存储位': { tone: 'default', desc: '存放' },
  '整托位': { tone: 'info', desc: '整托存放' },
  '拣货位': { tone: 'ok', desc: '拣选面' },
  '暂存位': { tone: 'low', desc: '临时落位' },
  '退货位': { tone: 'warning', desc: '退货暂存' },
  '不良品位': { tone: 'out', desc: '不良隔离' },
};

export function binTypeMeta(t) {
  return BIN_TYPE[t] || { tone: 'default', desc: '未登记类型' };
}

/* 混放三轴：任一为否，说明该库位对混放有限制。 */
export function mixState(row) {
  const blocked = [];
  if (!on(row.mixSkuAllowed)) blocked.push('SKU');
  if (!on(row.mixBatchAllowed)) blocked.push('批次');
  if (!on(row.mixOwnerAllowed)) blocked.push('货主');
  return blocked.length ? blocked : null;
}

export function renderBinPage(rows) {
  const sum = (k) => rows.reduce((s, r) => s + Number(r[k] || 0), 0);
  const pickFace = rows.filter((r) => on(r.pickFace)).length;
  const staging = rows.filter((r) => r.binType === '暂存位').length;
  const withDevice = rows.filter((r) => r.deviceAddress).length;

  const enabled = rows.filter((r) => r.status === 'enabled').length;

  /* 状态页签计数按当前数据实算，不写死 */
  /* 旧版 wm-bin 的页签按启用态分：全部 / 已启用 / 已停用 */

  const list = ListPage({
    statusTabs: [
      { label: '全部', count: rows.length, active: true },
      { label: '已启用', count: enabled, field: 'status', value: 'enabled' },
      { label: '已停用', count: rows.length - enabled, field: 'status', value: 'disabled' },
    ],
    filters: [
      { label: '库位编码', type: 'text', placeholder: '请输入库位编码' },
      { label: '库区', type: 'select', options: ['全部', ...[...new Set(rows.map((r) => r.zone))].sort()] },
      { label: '库位类型', type: 'select', options: ['全部', '存储位', '整托位', '拣货位', '暂存位', '退货位'] },
      { label: '温区', type: 'select', options: ['全部', ...[...new Set(rows.map((r) => r.tempZone))].sort()], extra: true },
      { label: '巷道', type: 'text', placeholder: '请输入巷道', extra: true },
      { label: '固定库位', type: 'select', options: ['全部', '是', '否'], extra: true },
    ],
    toolbar: [
      { label: '新增库位', primary: true },
      { label: '批量启用' },
      { label: '导出数据', ghost: true },
    ],
    showCount: true,
    scopeNote: '当前范围：—',
    /* 列名与宽度照搬旧版 wm-bin 的 11 列 */
    columns: [
      { key: 'id', label: '库位编码', width: '130px', cls: 'col-strong' },
      { key: 'warehouse', label: '归属仓库', width: '110px' },
      { key: 'zone', label: '所属库区', width: '110px' },
      { key: 'binType', label: '库位类型', width: '100px', render: (r) => Tag(r.binType, binTypeMeta(r.binType).tone) },
      { key: 'tempZone', label: '温区', width: '90px', render: (r) => dash(r.tempZone) },
      { key: 'volumeLimitL', label: '容积上限（L）', width: '110px', align: 'right', render: (r) => n(r.volumeLimitL) },
      { key: 'weightLimitKg', label: '承重上限（kg）', width: '120px', align: 'right', render: (r) => n(r.weightLimitKg) },
      { key: 'palletCapacity', label: '托盘位', width: '90px', align: 'right', render: (r) => n(r.palletCapacity) },
      { key: 'fixedBin', label: '固定库位', width: '90px', render: (r) => (on(r.fixedBin) ? '是' : '否') },
      { key: 'status', label: '状态', width: '100px',
        render: (r) => StatusTag(r.statusLabel, r.status === 'enabled' ? 'enabled' : 'disabled') },
      { key: 'updatedAt', label: '更新时间', width: '150px', render: (r) => dash(r.updatedAt) },
    ],
    rows,
    /* 单仓页：每行标出归属仓与状态口径，供页头仓库下拉过滤行、重算页签计数 */
    rowAttrs: (r) => ({ 'data-wh': r.warehouse, 'data-status': r.status }),
    emptyTitle: '暂无库位',
    emptyDesc: '点击上方「新增库位」开始录入，或从其它系统导入。',
  });

  return list;
}
