#!/usr/bin/env node
/**
 * 看板指标计算器（唯一取数口径）。
 *
 * 起因：汇报材料里的看板数字曾由人手写进 HTML，数据一改就全部失真，
 * 且无法发现。本脚本把口径写成代码，材料与页面都从这里取数。
 *
 * 用法：
 *   node tools/compute-metrics.mjs            打印 JSON
 *   node tools/compute-metrics.mjs --markdown 打印 Markdown 表格
 *   node tools/compute-metrics.mjs --check    检查材料里的数字是否与实测一致
 *
 * 口径（与 reports/任务中心看板-跨仓指标口径表.md 一致）：
 *   未终态 = status ∉ {completed, cancelled}
 *   待指派 = dispatchStatus = 待指派 且 status ≠ cancelled
 *   负荷比 = 未终态任务数 ÷ 在岗单元数（分母只含已登录单元）
 */

import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

/* 路径一律按本文件位置定位，不依赖 process.cwd()：
   脚本按本文件位置定位路径，不依赖 process.cwd()
   用 cwd 拼相对路径会在换目录运行时落空，existsSync 过滤后校验 0 个文件却报「通过」。 */
const HERE = path.dirname(fileURLToPath(import.meta.url));
const PROTO = path.resolve(HERE, '..');        // 本目录即自包含根
const DATA = path.join(PROTO, 'shared/data');

const read = (name) => JSON.parse(fs.readFileSync(path.join(DATA, name + '.json'), 'utf8'));

const TERMINAL = new Set(['completed', 'cancelled']);

export function computeMetrics() {
  const putaway = read('putaway-tasks');
  const relocation = read('relocation-tasks');
  const stocktaking = read('stocktaking-tasks');
  const units = read('exec-units');
  const warehouses = read('warehouses');

  const tasks = [
    ...putaway.map((r) => ({ ...r, _chain: '上架' })),
    ...relocation.map((r) => ({ ...r, _chain: '移库' })),
    ...stocktaking.map((r) => ({ ...r, _chain: '盘点' })),
  ];

  const open = (r) => !TERMINAL.has(r.status);
  const byStatus = {};
  for (const r of tasks) byStatus[r.status] = (byStatus[r.status] || 0) + 1;

  /* 执行单元池：一人一单元口径，只统计已登录的人型单元 */
  const onDuty = units.filter((u) => u.currentOperatorId);
  const busyUnits = onDuty.filter((u) => (u.currentTaskIds || []).length > 0);
  const offlineEntry = onDuty.filter((u) => u.deviceStatus === 'offline');

  const perWarehouse = warehouses.map((w) => {
    const t = tasks.filter((r) => r.warehouse === w.name);
    const u = units.filter((x) => x.warehouse === w.name);
    const on = u.filter((x) => x.currentOperatorId);
    const openCount = t.filter(open).length;
    return {
      warehouse: w.name,
      isEntity: !!w.isEntity,
      tasks: t.length,
      open: openCount,
      exception: t.filter((r) => r.status === 'exception').length,
      pendingAssign: t.filter((r) => r.dispatchStatus === '待指派' && r.status !== 'cancelled').length,
      units: u.length,
      onDuty: on.length,
      /* 分母为 0 时不产出比率，避免出现 Infinity 或 0 假象 */
      load: on.length ? Number((openCount / on.length).toFixed(2)) : null,
    };
  });

  return {
    generatedAt: new Date().toISOString().slice(0, 19).replace('T', ' '),
    tasks: {
      total: tasks.length,
      byChain: {
        上架: putaway.length,
        移库: relocation.length,
        盘点: stocktaking.length,
      },
      byStatus,
      open: tasks.filter(open).length,
      completed: tasks.filter((r) => r.status === 'completed').length,
      cancelled: tasks.filter((r) => r.status === 'cancelled').length,
      exception: tasks.filter((r) => r.status === 'exception').length,
      pendingAssign: tasks.filter((r) => r.dispatchStatus === '待指派' && r.status !== 'cancelled').length,
      deviceNamedOperator: tasks.filter((r) => /^(AGV|EMB|HUM|FORK)-/.test(r.operatorName || '')).length,
      unitCodes: [...new Set(tasks.map((r) => r.execUnitCode).filter(Boolean))].sort(),
    },
    units: {
      total: units.length,
      onDuty: onDuty.length,
      busy: busyUnits.length,
      restricted: offlineEntry.length,
      available: onDuty.length - busyUnits.length,
      unlogged: units.length - onDuty.length,
      types: [...new Set(units.map((u) => u.type))].sort(),
    },
    perWarehouse,
  };
}

/* ---------- Markdown 输出 ---------- */

export function toMarkdown(m) {
  const lines = [];
  lines.push('| 仓 | 任务 | 未终态 | 异常 | 待指派 | 执行单元 | 在岗 | 负荷比 |');
  lines.push('| --- | --- | --- | --- | --- | --- | --- | --- |');
  for (const w of m.perWarehouse) {
    lines.push(`| ${w.warehouse} | ${w.tasks} | ${w.open} | ${w.exception} | ${w.pendingAssign} | ${w.units} | ${w.onDuty} | ${w.load == null ? '—' : w.load.toFixed(2)} |`);
  }
  const sum = (k) => m.perWarehouse.reduce((a, w) => a + w[k], 0);
  lines.push(`| 合计 | ${sum('tasks')} | ${sum('open')} | ${sum('exception')} | ${sum('pendingAssign')} | ${sum('units')} | ${sum('onDuty')} | — |`);
  return lines.join('\n');
}

/* ---------- 一致性检查 ---------- */

/**
 * 材料里不允许再出现这些「手写口径」的旧总数。命中即说明有人把数字抄进了正文，
 * 数据一变就失真。规则不猜语义，只拦已知的旧值组合。
 */
const STALE = [
  { pattern: /168 行|168 条任务|168<\/b>/, why: '任务总行数已由 168 变为实测值，材料里出现旧的 168' },
  { pattern: /32 行填的是|32\/168/, why: '设备名作业员行已清零，材料里仍写 32 行' },
  { pattern: /89 条未终态|89<\/b>|11\/89|26\/89/, why: '未终态与派工口径已变，材料里出现旧的 89' },
];

function check() {
  const m = computeMetrics();
  const targets = [
    'reports/任务中心思路.html',
    'reports/任务中心看板调研.html',
    'reports/任务中心看板-跨仓指标口径表.md',
  ];
  const bad = [];
  let checked = 0;
  for (const rel of targets) {
    const p = path.join(PROTO, rel);
    if (!fs.existsSync(p)) continue;
    checked++;
    const src = fs.readFileSync(p, 'utf8');
    /* 去掉脚本与样式，只查正文，避免被 mermaid / 字体子集里的数字误伤 */
    const body = src.replace(/<script[\s\S]*?<\/script>/g, '').replace(/<style[\s\S]*?<\/style>/g, '');
    for (const s of STALE) {
      if (s.pattern.test(body)) bad.push(`${rel}：${s.why}`);
    }
  }
  if (!checked) {
    console.log(`未校验：${targets.length} 份目标材料均不存在，本项没有检查任何文件。`);
    return;
  }
  if (bad.length) {
    console.error('指标口径检查失败：');
    bad.forEach((b) => console.error('  - ' + b));
    console.error('\n实测值：任务 ' + m.tasks.total + ' 行，未终态 ' + m.tasks.open + '，异常 ' + m.tasks.exception
      + '，待指派 ' + m.tasks.pendingAssign + '，设备名作业员 ' + m.tasks.deviceNamedOperator);
    console.error('改法：跑 node tools/compute-metrics.mjs --markdown 取新表，替换材料里的手写数字。');
    process.exit(1);
  }
  console.log(`指标口径检查通过：${checked} 份材料的旧数字均已清除`);
}

/* ---------- CLI ---------- */

if (process.argv[1] && process.argv[1] === fileURLToPath(import.meta.url)) {
  if (process.argv.includes('--check')) check();
  else if (process.argv.includes('--markdown')) console.log(toMarkdown(computeMetrics()));
  else console.log(JSON.stringify(computeMetrics(), null, 2));
}
