/**
 * 执行看板页。
 *
 * 数据的唯一来源是 shared/data 的三份任务表与 exec-units，**页面里不写任何数字**。
 * 任何统计值都由 computeMetrics() 在构建期算出，数据一改看板自动跟着变。
 *
 * 口径（与 docs/reports/任务中心看板-跨仓指标口径表.md 一致）：
 *   未终态 = status ∉ { completed, cancelled }
 *   待指派 = dispatchStatus = 待指派 且未取消
 *   在办负荷 = 未终态任务数 ÷ 在岗单元数
 *
 * 骨架用 BaseTable 而非 ListPage：看板是按仓与按链路的分组汇总，不是可筛行表。
 */
import { BaseTable, Tag } from '../../../shared/ui/l3.mjs';

const TERMINAL = new Set(['completed', 'cancelled']);
const pct = (a, b) => (b ? Math.round((a / b) * 1000) / 10 : null);
const show = (v, unit = '') => (v == null ? '—' : `${v}${unit}`);

/**
 * 计算看板指标。纯函数，不读文件，便于构建期与校验脚本共用。
 * @param {object} o
 * @param {Array} o.putaway 上架任务
 * @param {Array} o.relocation 移库任务
 * @param {Array} o.stocktaking 盘点任务
 * @param {Array} o.units 执行单元
 * @param {Array} o.warehouses 仓库档案
 */
export function computeMetrics({ putaway = [], relocation = [], stocktaking = [], units = [], warehouses = [] }) {
  const chains = [
    { key: '上架', rows: putaway },
    { key: '移库', rows: relocation },
    { key: '盘点', rows: stocktaking },
  ];
  const all = chains.flatMap((c) => c.rows.map((r) => ({ ...r, chain: c.key })));
  const open = (r) => !TERMINAL.has(r.status);

  const byChain = chains.map((c) => {
    const rows = c.rows;
    const nt = rows.filter(open);
    return {
      chain: c.key,
      total: rows.length,
      open: nt.length,
      exception: rows.filter((r) => r.status === 'exception').length,
      completed: rows.filter((r) => r.status === 'completed').length,
      cancelled: rows.filter((r) => r.status === 'cancelled').length,
      pendingAssign: rows.filter((r) => r.dispatchStatus === '待指派' && r.status !== 'cancelled').length,
    };
  });

  const perWarehouse = warehouses.map((w) => {
    const rows = all.filter((r) => r.warehouse === w.name);
    const nt = rows.filter(open);
    const onDuty = units.filter((u) => u.warehouse === w.name && u.currentOperatorId);
    const done = rows.filter((r) => r.status === 'completed');
    return {
      warehouse: w.name,
      type: w.warehouseType,
      isEntity: !!w.isEntity,
      total: rows.length,
      open: nt.length,
      exception: rows.filter((r) => r.status === 'exception').length,
      overdue: nt.filter((r) => r.slaStatus === '已超期').length,
      near: nt.filter((r) => r.slaStatus === '临期').length,
      pendingAssign: rows.filter((r) => r.dispatchStatus === '待指派' && r.status !== 'cancelled').length,
      /* 一次做对：已完成且无异常记录 */
      rightFirst: pct(done.filter((r) => !(r.exceptionLogs || []).length).length, done.length),
      units: units.filter((u) => u.warehouse === w.name).length,
      onDuty: onDuty.length,
      /* 无在岗单元时分母无意义，返回 null，页面显示「—」而不是 ∞ */
      load: onDuty.length ? Math.round((nt.length / onDuty.length) * 100) / 100 : null,
    };
  });

  const nt = all.filter(open);
  return {
    tasks: {
      total: all.length,
      open: nt.length,
      exception: all.filter((r) => r.status === 'exception').length,
      completed: all.filter((r) => r.status === 'completed').length,
      cancelled: all.filter((r) => r.status === 'cancelled').length,
      pendingAssign: all.filter((r) => r.dispatchStatus === '待指派' && r.status !== 'cancelled').length,
      overdue: nt.filter((r) => r.slaStatus === '已超期').length,
      near: nt.filter((r) => r.slaStatus === '临期').length,
      noOperator: all.filter((r) => !r.operatorName).length,
    },
    units: {
      total: units.length,
      onDuty: units.filter((u) => u.currentOperatorId).length,
      busy: units.filter((u) => u.currentOperatorId && (u.currentTaskIds || []).length > 0).length,
      unlogged: units.filter((u) => !u.currentOperatorId).length,
    },
    byChain,
    perWarehouse,
  };
}

/** 数据自检：看板数字之间必须自洽，不自洽就中止构建 */
export function verifyMetrics(m) {
  const bad = [];
  const sum = (k, rows) => rows.reduce((a, r) => a + r[k], 0);
  if (sum('total', m.byChain) !== m.tasks.total) bad.push(`三链路行数合计 ${sum('total', m.byChain)} ≠ 任务总数 ${m.tasks.total}`);
  if (sum('open', m.byChain) !== m.tasks.open) bad.push(`三链路未终态合计 ${sum('open', m.byChain)} ≠ 总数 ${m.tasks.open}`);
  if (m.tasks.completed + m.tasks.cancelled + m.tasks.open !== m.tasks.total) {
    bad.push(`未终态 ${m.tasks.open} + 已完成 ${m.tasks.completed} + 已取消 ${m.tasks.cancelled} ≠ 总数 ${m.tasks.total}`);
  }
  for (const w of m.perWarehouse) {
    if (w.onDuty > w.units) bad.push(`${w.warehouse}：在岗 ${w.onDuty} 大于单元数 ${w.units}`);
  }
  return bad;
}

export function renderDashboardPage(m) {
  const whCols = [
    { key: 'warehouse', title: '仓' },
    { key: 'type', title: '类型' },
    { key: 'total', title: '任务', align: 'right' },
    { key: 'open', title: '未终态', align: 'right' },
    { key: 'exception', title: '异常', align: 'right' },
    { key: 'overdue', title: '已超期', align: 'right' },
    { key: 'near', title: '临期', align: 'right' },
    { key: 'pendingAssign', title: '待指派', align: 'right' },
    { key: 'rightFirst', title: '一次做对', align: 'right', render: (r) => show(r.rightFirst, '%') },
    { key: 'units', title: '单元', align: 'right' },
    { key: 'onDuty', title: '在岗', align: 'right' },
    { key: 'load', title: '在办负荷', align: 'right', render: (r) => (r.load == null ? '—' : r.load.toFixed(2)) },
  ];

  const chainCols = [
    { key: 'chain', title: '链路' },
    { key: 'total', title: '任务', align: 'right' },
    { key: 'open', title: '未终态', align: 'right' },
    { key: 'exception', title: '异常', align: 'right' },
    { key: 'pendingAssign', title: '待指派', align: 'right' },
    { key: 'completed', title: '已完成', align: 'right' },
    { key: 'cancelled', title: '已取消', align: 'right' },
  ];

  const t = m.tasks;
  const u = m.units;
  const summary = `任务 ${t.total} · 未终态 ${t.open} · 异常 ${t.exception} · 待指派 ${t.pendingAssign} · 在岗单元 ${u.onDuty}`;

  /* 计数写在 BaseTable 的 summary 位，不另起统计卡片或自绘样式（05 号规范第 2、5 节） */
  return `<div class="page-card">`
    + BaseTable({ columns: whCols, rows: m.perWarehouse, summary })
    + BaseTable({ columns: chainCols, rows: m.byChain, summary: '链路面板：三类任务分表统计，口径不合并' })
    + `</div>`;
}
