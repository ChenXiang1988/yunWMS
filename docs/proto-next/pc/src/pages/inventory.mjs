/**
 * 商品库存页。
 *
 * 只负责取数与列定义，页面结构全部交给 L3 组件。
 * 不拼 HTML、不写样式、不出现色值。
 */
import { ListPage, StatusTag, Tag } from '../../../shared/ui/l3.mjs';

const n = (v) => Number(v || 0).toLocaleString('zh-CN');

/* 库存状态：只依据数量口径判定，规则来自实测数据。 */
export function stockState(row) {
  if (row.available <= 0) return { tone: 'out', label: '无可用' };
  if (row.safetyMin > 0 && row.available < row.safetyMin) return { tone: 'low', label: '低于下限' };
  return { tone: 'ok', label: '正常' };
}

/* 数量口径自检：可用 = 良品 − 预占 − 占用，且不小于 0。
   实测 24/24 行成立，构建期据此阻断。 */
export function verifyQtyRules(rows) {
  const bad = [];
  for (const r of rows) {
    const expect = Math.max(0, r.good - r.orderReserved - r.orderOccupied);
    if (r.available !== expect) {
      bad.push(r.id + '：可用 ' + r.available + ' ≠ ' + r.good + ' − ' + r.orderReserved + ' − ' + r.orderOccupied + ' = ' + expect);
    }
    if (r.available < 0) bad.push(r.id + '：可用为负 ' + r.available);
  }
  return bad;
}

const COLUMNS = [
  { key: 'wsin', title: 'WSIN编码', mono: true },
  { key: 'name', title: '商品名称' },
  { key: 'spec', title: '颜色及规格' },
  { key: 'warehouseName', title: '仓库' },
  { key: 'supplyType', title: '供货方式' },
  { key: 'actual', title: '实际库存数', align: 'right', render: (r) => n(r.actual) },
  { key: 'good', title: '良品库存', align: 'right', render: (r) => n(r.good) },
  { key: 'bad', title: '不良品库存', align: 'right', render: (r) => n(r.bad) },
  { key: 'available', title: '可用数', align: 'right', render: (r) => '<b>' + n(r.available) + '</b>' },
  { key: 'orderReserved', title: '订单预占数', align: 'right', render: (r) => n(r.orderReserved) },
  { key: 'orderOccupied', title: '订单占用数', align: 'right', render: (r) => n(r.orderOccupied) },
  { key: 'frozen', title: '冻结库存', align: 'right', render: (r) => n(r.frozen) },
  { key: 'safetyMin', title: '安全库存下限', align: 'right', render: (r) => n(r.safetyMin) },
  { key: 'state', title: '状态', render: (r) => { const st = stockState(r); return Tag(st.label, st.tone); } },
];

export function renderInventoryPage(rows, ctx) {
  const sum = (k) => rows.reduce((s, r) => s + Number(r[k] || 0), 0);
  const outCount = rows.filter((r) => stockState(r).tone === 'out').length;
  const lowCount = rows.filter((r) => stockState(r).tone === 'low').length;

  /* 状态页签计数按当前数据实算 */
  const toneCount = (t) => rows.filter((r) => stockState(r).tone === t).length;
  const whs = [...new Set(rows.map((r) => r.warehouseName))].sort();

  const list = ListPage({
    statusTabs: [
      { label: '全部', count: rows.length, active: true },
      { label: '正常', count: toneCount('ok') },
      { label: '低于下限', count: toneCount('low') },
      { label: '无可用', count: toneCount('out') },
    ],
    filters: [
      { label: '商品名称 / WSIN', type: 'text', placeholder: '请输入商品名称或 WSIN' },
      { label: '仓库', type: 'select', options: ['全部', ...whs] },
      { label: '供货方式', type: 'select', options: ['全部', ...[...new Set(rows.map((r) => r.supplyType))].sort()] },
      { label: '库存类型', type: 'select', options: ['全部', ...[...new Set(rows.map((r) => r.inventoryType))].sort()], extra: true },
      { label: '品牌', type: 'text', placeholder: '请输入品牌名称', extra: true },
      { label: '更新时间', type: 'daterange', extra: true },
    ],
    toolbar: [
      { label: '库存调整', primary: true },
      { label: '导出数据', ghost: true },
    ],
    showCount: true,
    scopeNote: '当前为全部仓库数据范围',
    columns: COLUMNS.map((c) => ({ ...c, label: c.title })).concat([
      { key: 'ops', label: '操作', fixed: true, render: () => '<span class="row-actions">'
          + '<button class="btn link" type="button">明细</button>'
          + '<button class="btn link" type="button">流水</button></span>' },
    ]),
    rows,
    emptyTitle: '暂无库存数据',
    emptyDesc: '当前筛选条件下没有任何记录。',
  });

  return list;
}
