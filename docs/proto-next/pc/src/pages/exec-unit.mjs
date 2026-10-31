/**
 * 执行单元页。
 * 数据：shared/data/exec-units.json（109 个单元）
 *
 * 模型：执行单元是设备主体（手机 / PDA），人员是当前登录账号。
 * 本期只做 PDA 与 PC 人机单元；AGV 出界，不纳入单元池（见 A.11.9.124）。
 * 状态拆两轴：设备态取硬件台账，登录态由登录人派生。
 */
import { ListPage, StatusTag, Tag } from '../../../shared/ui/l3.mjs';

const n = (v) => Number(v || 0).toLocaleString('zh-CN');
const dash = (v) => (v === '' || v == null ? '—' : v);

const DEV_TONE = { online: 'ok', offline: 'out', maintenance: 'low' };
const LOGIN_TONE = { idle: 'ok', busy: 'info', offline: 'out', unl: 'default' };

/* 登录态：由 currentOperatorId 是否为空派生，不落库。 */
export function loginState(u) {
  return u.currentOperatorId
    ? { key: 'on', label: '已登录', tone: 'ok' }
    : { key: 'off', label: '未登录', tone: 'default' };
}

/* 负荷分档 */
export function loadLevel(load) {
  const v = Number(load || 0);
  if (v >= 0.8) return { label: '满载', tone: 'out' };
  if (v >= 0.5) return { label: '较忙', tone: 'low' };
  return { label: '空闲', tone: 'ok' };
}

export function renderExecUnitPage({ units: allUnits }) {
  /* 本期只出 PDA 与手机单元，AGV 出界不计入 */
  const units = allUnits.filter((u) => u.type === 'EXEC-PDA');
  const pda = units;
  const loggedIn = units.filter((u) => u.currentOperatorId);
  const busy = units.filter((u) => (u.currentTaskIds || []).length > 0);

  const whs = [...new Set(units.map((u) => u.warehouse))].sort();

  const list = ListPage({
    unit: '个',
    statusTabs: [
      { label: '全部', count: units.length, active: true },
      { label: '已登录', count: loggedIn.length },
      { label: '在办任务', count: busy.length },
      { label: '未登录', count: units.length - loggedIn.length },
    ],
    filters: [
      { label: '单元编号 / 设备号', type: 'text', placeholder: '请输入单元编号或设备号' },
      { label: '仓库', type: 'select', options: ['全部', ...whs] },
      { label: '班次', type: 'select', options: ['全部', ...[...new Set(units.map((u) => u.shift))].sort()] },
      { label: '区域', type: 'text', placeholder: '请输入区域', extra: true },
      { label: '单元状态', type: 'select', options: ['全部', ...[...new Set(units.map((u) => u.statusLabel).filter(Boolean))].sort()], extra: true },
      { label: '登录时间', type: 'daterange', extra: true },
    ],
    toolbar: [
      { label: '新增单元', primary: true },
      { label: '批量下发', disabled: true },
      { label: '导出数据', ghost: true },
    ],
    showCount: true,
    /* 旧版 renderUnits 的工具栏计数写法：N / M 个执行单元 */
    scopeNote: `${units.length} / ${units.length} 个执行单元`,
    columns: [
      { key: 'id', label: '单元编号', cls: 'col-strong' },
      { key: 'typeLabel', label: '类型', render: (r) => Tag(r.typeLabel, 'default') },
      { key: 'terminalId', label: '设备编号' },
      { key: 'currentOperatorName', label: '当前登录', render: (r) => dash(r.currentOperatorName) },
      { key: 'login', label: '登录态', render: (r) => { const s = loginState(r); return Tag(s.label, s.tone); } },
      { key: 'warehouse', label: '仓库' },
      { key: 'zone', label: '区域' },
      { key: 'shift', label: '班次' },
      { key: 'deviceStatus', label: '设备态', render: (r) => Tag(r.deviceStatus || '—', DEV_TONE[r.deviceStatus] || 'default') },
      { key: 'statusLabel', label: '单元状态', render: (r) => StatusTag(r.statusLabel || '—', LOGIN_TONE[r.status] || 'disabled') },
      { key: 'load', label: '负荷', align: 'right', render: (r) => Number(r.load || 0).toFixed(1) },
      { key: 'loadLevel', label: '负载档', render: (r) => { const l = loadLevel(r.load); return Tag(l.label, l.tone); } },
      { key: 'currentTaskIds', label: '在办任务', align: 'right', render: (r) => n((r.currentTaskIds || []).length) },
      { key: 'dispatchChannel', label: '下发通道', render: (r) => dash(r.dispatchChannel) },
      { key: 'loginAt', label: '登录时间', render: (r) => dash(r.loginAt) },
      { key: 'ops', label: '操作', fixed: true, render: () => '<span class="row-actions">'
          + '<button class="btn link" type="button">编辑</button>'
          + '<button class="btn link" type="button">任务</button></span>' },
    ],
    rows: units,
    emptyTitle: '暂无执行单元',
    emptyDesc: '点击上方「新增单元」创建第一个执行单元。',
  });

  return list;
}
