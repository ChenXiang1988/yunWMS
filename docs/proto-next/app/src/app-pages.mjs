/**
 * APP 页面。
 *
 * 定位：APP 是执行单元（PDA 型）的设备端，核心是「接到什么任务、怎么干完」。
 * 结构对齐归档实现：主页 → 列表 → 作业页（扫码驱动）。
 */
import { AppNavbar, AppStatusbar } from './app-shell.mjs';

const esc = (v) => String(v ?? '')
  .replace(/&/g, '&amp;').replace(/</g, '&lt;')
  .replace(/>/g, '&gt;').replace(/"/g, '&quot;');

/* 任务状态 → 中文与色调 */
const TASK_STATE = {
  pending:   { label: '待作业', tone: 'wait' },
  putaway:   { label: '上架中', tone: 'busy' },
  moving:    { label: '移库中', tone: 'busy' },
  counting:  { label: '盘点中', tone: 'busy' },
  exception: { label: '异常待处理', tone: 'bad' },
  completed: { label: '已完成', tone: 'done' },
  cancelled: { label: '已取消', tone: 'mute' },
};
const stateOf = (s) => TASK_STATE[s] || { label: s || '—', tone: 'mute' };

/* ============================ 主页 ============================ */
export function renderHome({ operator, device, warehouse = '', zone = '', unitId = '', stats }) {
  const apps = [
    { key: 'putaway', page: 'putaway-list', name: '上架作业任务', count: stats.putaway, icon: '↥' },
    { key: 'relocation', page: 'relocation-list', name: '移库作业任务', count: stats.relocation, icon: '⇄' },
    { key: 'stocktaking', page: 'stocktaking-list', name: '盘点作业任务', count: stats.stocktaking, icon: '✓' },
  ];
  const cards = apps.map((a) => `
    <a class="app-entry-card" href="#${a.page}">
      <span class="app-entry-icon">${a.icon}</span>
      <span class="app-entry-body">
        <b>${esc(a.name)}</b>
        <small>${a.count > 0 ? '待办 ' + a.count + ' 条' : '暂无待办'}</small>
      </span>
      <span class="app-entry-arrow">›</span>
    </a>`).join('');

  /* 当前在做：从三类任务里取第一条在办，给出继续作业入口 */
  const doing = (stats.doing || null);
  const doingHtml = doing ? `
    <a class="app-doing-card" href="#${doing.kind}-work?id=${encodeURIComponent(doing.id)}">
      <span class="app-doing-tag">当前在做</span>
      <span class="app-doing-body">
        <b>${esc(doing.id)}</b>
        <small>${esc(doing.kindName)} · ${esc(doing.warehouse || '')}</small>
      </span>
      <span class="app-doing-go">继续 ›</span>
    </a>` : '';

  return {
    navbar: AppNavbar({
      title: '库内作业',
      right: `<button class="app-nav-me" data-key="to-mine" aria-label="我的">
                <span class="app-nav-me-avatar">${esc(operator.slice(0, 1))}</span>
              </button>`,
    }),
    statusbar: AppStatusbar({ operator, device, time: stats.time }),
    content: `<div class="app-home">
      <div class="app-ctx">
        <div class="app-ctx-row"><span>执行单元</span><b>${esc(unitId)}</b></div>
        <div class="app-ctx-row"><span>终端</span><b>${esc(device)}</b></div>
        <div class="app-ctx-row"><span>区域</span><b>${esc(warehouse)}${zone ? ' / ' + esc(zone) : ''}</b></div>
      </div>
      ${doingHtml}
      ${cards}
    </div>`,
    actionbar: `<div class="app-bar-note">已登录 ${esc(device)} · ${esc(operator)}</div>`,
  };
}

/* ============================ 列表页 ============================ */
export function renderTaskList({ title, kind, tasks, operator, device }) {
  const pending = tasks.filter((t) => ['pending', 'putaway', 'moving', 'counting'].includes(t.status));
  const cards = pending.length ? pending.map((t) => {
    const st = stateOf(t.status);
    return `<a class="app-task-card" href="#${kind}-work?id=${encodeURIComponent(t.id)}">
      <div class="app-task-head">
        <b class="app-task-id">${esc(t.id)}</b>
        <span class="app-state app-state--${st.tone}">${st.label}</span>
      </div>
      <div class="app-task-meta">
        <span>${esc(t.warehouse || '—')}</span>
        <span>${esc(t.taskType || t.moveType || t.countType || '—')}</span>
      </div>
      <div class="app-task-foot">
        <span class="app-task-qty">计划 ${esc(t.planQtyTotal ?? t.skuCount ?? '—')}</span>
        <span class="app-task-due">${esc(t.deadlineDate || t.planDate || '')}</span>
      </div>
    </a>`;
  }).join('') : '<div class="app-empty"><b>暂无待办</b><p>该模块当前没有待作业任务</p></div>';

  return {
    navbar: AppNavbar({ title, showBack: true, right: `<span>${pending.length} 条</span>` }),
    statusbar: AppStatusbar({ operator, device, time: '' }),
    content: `<div class="app-list">${cards}</div>`,
    actionbar: `<button class="app-btn app-btn--ghost" data-key="back">返回</button>
      <button class="app-btn app-btn--primary" data-key="scan">扫描任务</button>`,
  };
}

/* ============================ 作业页（扫码驱动）============================ */
export function renderWork({ kind, task, steps, operator, device }) {
  const st = stateOf(task.status);
  const stepHtml = steps.map((s, i) => `
    <li class="app-step${s.done ? ' is-done' : ''}${s.active ? ' is-active' : ''}" data-step="${i}">
      <span class="app-step-no">${s.done ? '✓' : i + 1}</span>
      <span class="app-step-body">
        <b>${esc(s.label)}</b>
        ${s.value ? `<small>${esc(s.value)}</small>` : ''}
      </span>
      ${s.done ? '<span class="app-step-ok">已确认</span>' : ''}
    </li>`).join('');

  const current = steps.find((s) => s.active);
  return {
    navbar: AppNavbar({
      title: task.id,
      showBack: true,
      right: `<span class="app-state app-state--${st.tone}">${st.label}</span>`,
    }),
    statusbar: AppStatusbar({ operator, device, time: '' }),
    content: `<div class="app-work">
      <div class="app-work-head">
        <div><span>仓库</span><b>${esc(task.warehouse || '—')}</b></div>
        <div><span>工序</span><b>${esc(task.taskType || task.moveType || task.countType || '—')}</b></div>
      </div>
      <ol class="app-steps">${stepHtml}</ol>
    </div>`,
    actionbar: current
      ? `<button class="app-btn app-btn--ghost" data-key="back">返回</button>
         <button class="app-btn app-btn--primary" data-key="scan">扫描${esc(current.label)}</button>`
      : `<button class="app-btn app-btn--primary" data-key="ok">提交完成</button>`,
  };
}

/* ============================ 页面样式 ============================ */
export const APP_PAGES_CSS = `
/* 执行单元上下文条 */
.app-ctx{
  display:flex;flex-direction:column;gap:4px;
  padding:8px var(--pda-space-content);border-radius:8px;
  background:var(--pda-color-brand-soft);margin-bottom:var(--pda-space-small);
}
.app-ctx-row{display:flex;justify-content:space-between;font-size:var(--pda-font-caption)}
.app-ctx-row span{color:var(--pda-color-brand);opacity:.75}
.app-ctx-row b{font-family:ui-monospace,Menlo,monospace;color:var(--pda-color-brand);font-weight:600}

/* 主页 */
.app-home{padding:var(--pda-space-content);display:flex;flex-direction:column;gap:var(--pda-space-small)}
.app-entry-card{
  display:flex;align-items:center;gap:var(--pda-space-content);
  padding:16px var(--pda-space-content);border-radius:8px;
  background:var(--pda-color-surface);text-decoration:none;color:inherit;
  box-shadow:var(--pda-size-shadow-xs,0 1px 2px rgba(0,0,0,.05));
}
.app-entry-card:active{background:var(--pda-color-brand-soft)}
.app-entry-icon{
  width:38px;height:38px;flex:none;display:grid;place-items:center;
  border-radius:8px;background:var(--pda-color-brand-soft);
  color:var(--pda-color-brand);font-size:18px;font-weight:700;
}
.app-entry-body{flex:1;min-width:0;display:flex;flex-direction:column;gap:4px}
.app-entry-body b{font-size:15px;font-weight:600}
.app-entry-body small{font-size:var(--pda-font-caption);color:var(--pda-color-text-muted)}
.app-entry-arrow{color:var(--pda-color-text-muted);font-size:18px}

/* 列表 */
.app-list{padding:var(--pda-space-content);display:flex;flex-direction:column;gap:var(--pda-space-small)}
.app-task-card{
  display:flex;flex-direction:column;gap:8px;
  padding:12px var(--pda-space-content);border-radius:8px;
  background:var(--pda-color-surface);text-decoration:none;color:inherit;
  box-shadow:var(--pda-size-shadow-xs,0 1px 2px rgba(0,0,0,.05));
}
.app-task-head{display:flex;align-items:center;justify-content:space-between;gap:8px}
.app-task-id{font-size:14px;font-weight:600;font-family:ui-monospace,Menlo,monospace}
.app-task-meta{display:flex;gap:8px;font-size:var(--pda-font-caption);color:var(--pda-color-text-secondary)}
.app-task-foot{display:flex;justify-content:space-between;font-size:var(--pda-font-caption);color:var(--pda-color-text-muted)}

.app-state{font-size:12px;padding:4px 7px;border-radius:0;white-space:nowrap}
.app-state--wait{background:var(--pda-color-warning-soft);color:var(--pda-color-warning-text)}
.app-state--busy{background:var(--pda-color-brand-soft);color:var(--pda-color-brand)}
.app-state--bad{background:var(--pda-color-danger-soft);color:var(--pda-color-danger)}
.app-state--done{background:var(--pda-color-brand-soft);color:var(--pda-color-brand)}
.app-state--mute{background:var(--pda-color-page);color:var(--pda-color-text-muted)}

.app-empty{padding:24px 16px;text-align:center;color:var(--pda-color-text-muted)}
.app-empty b{display:block;font-size:14px;color:var(--pda-color-text-secondary);margin-bottom:4px}
.app-empty p{margin:0;font-size:var(--pda-font-caption)}

/* 作业页 */
.app-work{padding:var(--pda-space-content);display:flex;flex-direction:column;gap:var(--pda-space-content)}
.app-work-head{
  display:flex;gap:var(--pda-space-content);
  padding:8px var(--pda-space-content);border-radius:8px;
  background:var(--pda-color-surface);
}
.app-work-head>div{flex:1;display:flex;flex-direction:column;gap:4px}
.app-work-head span{font-size:var(--pda-font-caption);color:var(--pda-color-text-muted)}
.app-work-head b{font-size:14px;font-weight:500}

.app-steps{list-style:none;margin:0;padding:0;display:flex;flex-direction:column;gap:4px}
.app-step{
  display:flex;align-items:center;gap:var(--pda-space-content);
  padding:12px var(--pda-space-content);background:var(--pda-color-surface);
  border-bottom:1px solid var(--pda-color-divider);
}
.app-step:first-child{border-radius:8px 10px 0 0}
.app-step:last-child{border-radius:0 0 10px 10px;border-bottom:0}
.app-step-no{
  width:22px;height:22px;flex:none;display:grid;place-items:center;
  border-radius:50%;background:var(--pda-color-page);color:var(--pda-color-text-muted);
  font-size:12px;font-weight:600;
}
.app-step.is-active .app-step-no{background:var(--pda-color-brand);color:var(--pda-color-text-inverse)}
.app-step.is-done .app-step-no{background:var(--pda-color-brand-soft);color:var(--pda-color-brand)}
.app-step-body{flex:1;min-width:0;display:flex;flex-direction:column;gap:4px}
.app-step-body b{font-size:14px;font-weight:500}
.app-step-body small{font-size:12px;color:var(--pda-color-text-muted);font-family:ui-monospace,Menlo,monospace}
.app-step.is-active .app-step-body b{color:var(--pda-color-brand);font-weight:600}
.app-step-ok{font-size:12px;color:var(--pda-color-brand)}

/* 按钮 */
.app-btn{
  flex:1;height:38px;border:0;border-radius:8px;cursor:pointer;
  font-family:inherit;font-size:14px;font-weight:500;
}
.app-btn--primary{background:var(--pda-color-action-primary);color:var(--pda-color-text-inverse)}
.app-btn--primary:active{opacity:.9}
.app-btn--ghost{background:var(--pda-color-surface);color:var(--pda-color-text-secondary);border:1px solid var(--pda-color-border)}
.app-doing-card{
  display:flex;align-items:center;gap:var(--pda-space-small);
  padding:12px var(--pda-space-content);border-radius:8px;
  background:var(--pda-color-surface);border-left:3px solid var(--pda-color-brand);
  text-decoration:none;color:inherit;
}
.app-doing-tag{
  flex:none;padding:0 6px;border-radius:8px;
  background:var(--pda-color-brand);color:var(--pda-color-text-inverse);
  font-size:12px;line-height:20px;
}
.app-doing-body{flex:1;min-width:0;display:flex;flex-direction:column;gap:2px}
.app-doing-body b{font-size:14px;font-family:ui-monospace,Menlo,monospace;font-weight:600}
.app-doing-body small{font-size:12px;color:var(--pda-color-text-muted)}
.app-doing-go{flex:none;font-size:12px;color:var(--pda-color-brand);font-weight:600}
.app-nav-me{
  width:28px;height:28px;padding:0;border:0;border-radius:50%;
  background:var(--pda-color-brand);color:var(--pda-color-text-inverse);
  display:grid;place-items:center;cursor:pointer;
  font-family:var(--pda-font-family);font-size:12px;font-weight:600;
}
.app-bar-note{flex:1;font-size:var(--pda-font-caption);color:var(--pda-color-text-muted)}
`;

/* ============================ 我的设备（本机信息 / 换机） ============================ */
export function renderMyDevice({ operator, device, deviceInfo = {}, binding = {} }) {
  const isByod = deviceInfo.accessType === '自有设备';
  const rows = [
    ['设备编号', deviceInfo.id || device || '—'],
    ['设备名称', deviceInfo.name || '—'],
    ['设备类 / 形态', `${deviceInfo.className || '作业终端'} · ${deviceInfo.form || '—'}`],
    ['接入类型', deviceInfo.accessType || '受管设备'],
    [isByod ? '应用 UUID' : 'IMEI', isByod ? (deviceInfo.appUuid || '—') : (deviceInfo.imei || '—')],
    ['序列号', deviceInfo.sn || '—'],
    ['运行状态', deviceInfo.statusLabel || '—'],
    ['绑定账号', operator],
    ['绑定时间', binding.since || '—'],
  ];
  const kv = rows.map(([k, v]) => `
    <div class="app-kv"><span>${esc(k)}</span><b>${esc(v)}</b></div>`).join('');

  const tip = isByod
    ? `<div class="app-tip app-tip--info">
         <b>自带设备</b>
         <p>本机为员工自有。系统只记录账号与设备型号，不采集 IMEI、通讯录、位置，不支持远程擦除。</p>
       </div>`
    : `<div class="app-tip">
         <b>受管设备</b>
         <p>本机已配置设备所有者，可由公司统一管理。换机请联系仓管解绑后再登录。</p>
       </div>`;

  return {
    navbar: AppNavbar({ title: '我的设备', showBack: true }),
    statusbar: AppStatusbar({ operator, device }),
    content: `<div class="app-mydev">
      <div class="app-device-hero">
        <span class="app-device-hero__icon">▣</span>
        <div>
          <b>${esc(deviceInfo.id || device || '未绑定')}</b>
          <small>${esc(deviceInfo.name || '')}</small>
        </div>
        <span class="app-state ${binding.bound ? 'app-state--done' : 'app-state--wait'}">${binding.bound ? '已绑定' : '未绑定'}</span>
      </div>
      <div class="app-card">${kv}</div>
      ${tip}
      <div class="app-actions-col">
        <button class="app-btn app-btn--ghost" data-key="reactivate">重新激活本机</button>
        <button class="app-btn app-btn--ghost" data-key="switch">换一台设备登录</button>
      </div>
    </div>`,
    actionbar: `<button class="app-btn app-btn--ghost" data-key="back">返回</button>
      <button class="app-btn app-btn--primary" data-key="home">回主页</button>`,
  };
}

/* ============================ 设备激活 ============================ */
/**
 * 设备激活。
 *
 * 关键点：App 运行在本机上，本机身份由 App 自己读取（IMEI / 应用 UUID / 序列号）
 * 并上报，不需要人拿手机扫自己的机箱码——那在物理上不成立。
 *
 * 需要人操作的是「接入码」：由管理员在 PC 侧生成，一机一码，用于把本机登记进系统。
 * 未登记的设备不能接任务。
 */
export function renderActivate({ operator, deviceInfo = {}, phase = 'identify', errMsg = '' }) {
  const isVerify = phase === 'verify';

  const hero = `<div class="app-device-hero">
      <span class="app-device-hero__icon">▣</span>
      <div>
        <b>${esc(deviceInfo.id || '未识别')}</b>
        <small>${esc(deviceInfo.name || '')}</small>
      </div>
      <span class="app-state ${isVerify ? 'app-state--wait' : 'app-state--mute'}">${isVerify ? '已登记待验证' : '未登记'}</span>
    </div>`;

  /* 本机自报身份：App 启动即读到，不需要扫码 */
  const ident = `<div class="app-card">
      <div class="app-kv"><span>设备编号</span><b>${esc(deviceInfo.id || '—')}</b></div>
      <div class="app-kv"><span>设备名称</span><b>${esc(deviceInfo.name || '—')}</b></div>
      <div class="app-kv"><span>接入类型</span><b>${esc(deviceInfo.accessType || '受管设备')}</b></div>
      ${deviceInfo.accessType === '自有设备'
        ? `<div class="app-kv"><span>应用 UUID</span><b>${esc(deviceInfo.appUuid || '—')}</b></div>`
        : `<div class="app-kv"><span>IMEI</span><b>${esc(deviceInfo.imei || '—')}</b></div>`}
      <div class="app-kv"><span>序列号</span><b>${esc(deviceInfo.sn || '—')}</b></div>
    </div>
    <p class="app-ident-note">以上信息由本机自动读取并上报，无需手工输入。</p>`;

  if (isVerify) {
    return {
      navbar: AppNavbar({ title: '确认绑定本机', showBack: true }),
      statusbar: AppStatusbar({ operator, device: deviceInfo.id || '' }),
      content: `<div class="app-bind">
        ${hero}
        ${ident}
        <div class="app-tip">
          <b>这台设备已登记</b>
          <p>接入码校验通过后，本机将与账号「${esc(operator)}」绑定，可以开始接任务。</p>
        </div>
        <div class="app-tip app-tip--warn">
          <b>注意</b>
          <p>若你此前已绑定其他设备，原绑定将自动作废。未完成的任务仍归你本人，不会退回任务池。</p>
        </div>
      </div>`,
      actionbar: `<button class="app-btn app-btn--ghost" data-key="back">取消</button>
        <button class="app-btn app-btn--primary" data-key="do-bind">确认绑定</button>`,
    };
  }

  return {
    navbar: AppNavbar({ title: '激活本机', showBack: true }),
    statusbar: AppStatusbar({ operator, device: deviceInfo.id || '' }),
    content: `<div class="app-bind">
      ${hero}
      <div class="app-tip app-tip--warn" style="margin-top:0">
        <b>首次使用需绑定本机</b>
        <p>已登录成功，但本机尚未绑定。绑定后才能接任务，绑定只需一次。</p>
      </div>
      ${ident}
      <label class="app-field">
        <span>接入码（由设备管理员在 PC 侧生成）</span>
        <input id="actCode" class="app-input" inputmode="numeric" maxlength="8"
               placeholder="请输入 8 位接入码" autocomplete="off">
      </label>
      <p class="app-bind-msg${errMsg ? ' is-bad' : ''}" id="actMsg">${esc(errMsg)}</p>
      <div class="app-tip">
        <b>为什么需要接入码</b>
        <p>接入码把本机登记进系统，防止拿错机、换机冒用。请向仓管索取：PC 端「设备 → 码管理」可生成本机接入码。</p>
      </div>
    </div>`,
    actionbar: `<button class="app-btn app-btn--ghost" data-key="back">返回</button>
      <button class="app-btn app-btn--primary" data-key="do-activate">激活</button>`,
  };
}

/* ============================ 换机 ============================ */
export function renderSwitch({ operator, fromDevice = '', toDevice = '' }) {
  return {
    navbar: AppNavbar({ title: '换设备登录', showBack: true }),
    statusbar: AppStatusbar({ operator, device: fromDevice }),
    content: `<div class="app-switch">
      <div class="app-flow">
        <div class="app-flow-step is-done"><span>1</span><b>登出当前设备</b><small>${esc(fromDevice)}</small></div>
        <div class="app-flow-line"></div>
        <div class="app-flow-step is-active"><span>2</span><b>在新设备上登录</b><small>新机 App 自动上报本机身份</small></div>
        <div class="app-flow-line"></div>
        <div class="app-flow-step"><span>3</span><b>二次验证</b><small>人脸识别</small></div>
      </div>
      <div class="app-tip app-tip--warn">
        <b>任务不会丢</b>
        <p>本机登出后，在新设备登录即可继续。任务仍归你本人，不退回任务池。旧设备上的单元结束，新设备生成新单元。</p>
      </div>
    </div>`,
    actionbar: `<button class="app-btn app-btn--ghost" data-key="back">取消</button>
      <button class="app-btn app-btn--primary" data-key="do-switch">登出本机</button>`,
  };
}

export const APP_MYDEV_CSS = `
.app-mydev,.app-bind,.app-switch{padding:var(--pda-space-content);display:flex;flex-direction:column;gap:var(--pda-space-content)}
.app-device-hero{
  display:flex;align-items:center;gap:var(--pda-space-content);
  padding:16px var(--pda-space-content);border-radius:8px;background:var(--pda-color-surface);
}
.app-device-hero__icon{
  width:38px;height:38px;flex:none;display:grid;place-items:center;border-radius:8px;
  background:var(--pda-color-brand-soft);color:var(--pda-color-brand);font-size:18px;
}
.app-device-hero b{display:block;font-size:15px;font-family:ui-monospace,Menlo,monospace}
.app-device-hero small{display:block;font-size:var(--pda-font-caption);color:var(--pda-color-text-muted)}

.app-card{padding:8px var(--pda-space-content);border-radius:8px;background:var(--pda-color-surface)}
.app-kv{display:flex;justify-content:space-between;gap:8px;padding:4px 0;font-size:14px}
.app-kv span{color:var(--pda-color-text-muted);flex:none}
.app-kv b{font-weight:500;text-align:right;word-break:break-all}

.app-tip{padding:8px var(--pda-space-content);border-radius:8px;background:var(--pda-color-brand-soft)}
.app-tip b{display:block;font-size:14px;color:var(--pda-color-brand);margin-bottom:4px}
.app-tip p{margin:0;font-size:var(--pda-font-caption);line-height:1.6;color:var(--pda-color-brand);opacity:.85}
.app-tip--warn{background:var(--pda-color-warning-soft)}
.app-tip--warn b,.app-tip--warn p{color:var(--pda-color-warning-text)}
.app-tip--info{background:var(--pda-color-brand-soft)}
.app-tip--info b,.app-tip--info p{color:var(--pda-color-brand)}

.app-actions-col{display:flex;flex-direction:column;gap:var(--pda-space-small)}
.app-btn{
  flex:1;height:38px;border:0;border-radius:8px;cursor:pointer;
  font-family:inherit;font-size:14px;font-weight:500;
}
.app-btn--primary{background:var(--pda-color-action-primary);color:var(--pda-color-text-inverse)}
.app-btn--ghost{background:var(--pda-color-surface);color:var(--pda-color-text-secondary);border:1px solid var(--pda-color-border)}

/* 扫码框 */
.app-scan-frame{
  position:relative;height:180px;border-radius:var(--pda-space-content);
  background:var(--pda-color-mask);
  display:grid;place-items:center;overflow:hidden;
}
.app-scan-frame span{color:var(--pda-color-text-inverse);font-size:36px}
.app-scan-line{
  position:absolute;left:12%;right:12%;height:2px;background:var(--pda-color-brand);
  box-shadow:var(--pda-size-shadow-xs,0 1px 2px rgba(0,0,0,.05));
  animation:appScan 2s ease-in-out infinite;
}
@keyframes appScan{0%,100%{top:22%}50%{top:78%}}
.app-bind-hint{margin:0;text-align:center;font-size:14px;color:var(--pda-color-text-secondary)}

/* 换机流程 */
.app-flow{display:flex;flex-direction:column;gap:4px}
.app-flow-step{
  display:flex;align-items:center;gap:8px;padding:12px var(--pda-space-content);
  background:var(--pda-color-surface);border-radius:8px;
}
.app-flow-step span{
  width:22px;height:22px;flex:none;display:grid;place-items:center;border-radius:50%;
  background:#eef1f0;color:var(--pda-color-text-muted);font-size:12px;font-weight:600;
}
.app-flow-step b{font-size:14px;font-weight:500;flex:1}
.app-flow-step small{font-size:12px;color:var(--pda-color-text-muted)}
.app-flow-step.is-done span{background:var(--pda-color-brand-soft);color:var(--pda-color-brand)}
.app-flow-step.is-active span{background:var(--pda-color-brand);color:var(--pda-color-text-inverse)}
.app-flow-step.is-active b{color:var(--pda-color-brand);font-weight:600}
.app-flow-line{height:10px;width:2px;margin-left:24px;background:var(--pda-color-border)}
`;

/* ============================ 未登录引导（首次使用） ============================ */
/**
 * 设备已登记但当前无人登录。
 * 本机身份已知（自报），只差登录账号即可开始作业。
 */
export function renderLoginGate({ device, deviceInfo = {} }) {
  return {
    navbar: AppNavbar({ title: '登录后开始作业' }),
    statusbar: AppStatusbar({ operator: '', device }),
    content: `<div class="app-bind">
      <div class="app-device-hero">
        <span class="app-device-hero__icon">▣</span>
        <div>
          <b>${esc(deviceInfo.id || device || '未识别设备')}</b>
          <small>${esc(deviceInfo.name || '')}</small>
        </div>
        <span class="app-state app-state--mute">未登录</span>
      </div>
      <div class="app-tip">
        <b>本机已登记</b>
        <p>设备身份已由本机自动上报。登录账号后即可接任务。</p>
      </div>
      <div class="app-actions-col">
        <button class="app-btn app-btn--primary" data-key="to-login">去登录</button>
      </div>
    </div>`,
    actionbar: `<div class="app-bar-note">未登录状态下不能接任务</div>`,
  };
}

/* ============================ 登录 ============================ */
/**
 * 三种登录方式：工号或手机号 + 密码、人脸识别、短信验证码。
 * 人脸登录依赖后端已登记的人脸特征；首次使用没有底片，走「采集 → 活体 → 提交 → 待审核」。
 */
export function renderLogin({ phase = 'pwd', err = '', account = '', pwd = '', faceStep = 0, faceInfo = {} }) {
  const tabs = `<div class="app-login-tabs">
    <button class="app-login-tab${phase === 'pwd' ? ' is-active' : ''}" data-key="login-pwd">账号密码</button>
    <button class="app-login-tab${phase === 'face' ? ' is-active' : ''}" data-key="login-face">人脸识别</button>
  </div>`;

  /* ---------- 账号密码 ---------- */
  if (phase === 'pwd') {
    return {
      navbar: AppNavbar({ title: '登录' }),
      statusbar: AppStatusbar({ operator: '', device: '' }),
      content: `<div class="app-login">
        <div class="app-login-brand">
          <span class="app-login-logo">西</span>
          <b>西之月 WMS</b>
          <small>仓储库存 · APP 端</small>
        </div>
        ${tabs}
        <div class="app-login-form">
          <label class="app-field">
            <span>工号</span>
            <input id="loginAcct" class="app-input" value="${esc(account || 'EMP-001')}" placeholder="请输入工号" autocomplete="off">
          </label>
          <label class="app-field">
            <span>密码</span>
            <input id="loginPwd" class="app-input" type="password" value="${esc(pwd || '123456')}" placeholder="请输入密码">
          </label>
          <p class="app-bind-msg is-bad" id="loginErr">${esc(err)}</p>
          <div class="app-login-row">
            <label class="app-check"><input type="checkbox" checked> <span>记住工号</span></label>
            <button class="app-link" data-key="login-forgot">忘记密码</button>
          </div>
        </div>
        <div class="app-tip app-tip--info">
          <b>原型演示说明</b>
          <p>工号与密码已预填，直接点「登录」。</p>
          <p>本机 PHONE-01 尚未绑定，登录后会要求输入接入码（原型用 12345678），绑定完成即进作业主页。</p>
          <p>想看人脸首次登记：切到「人脸识别」，EMP-001 无底片走采集流程，EMP-004 有底片直接识别。</p>
        </div>
      </div>`,
      actionbar: `<button class="app-btn app-btn--primary" data-key="do-login-pwd">登录</button>`,
    };
  }

  /* ---------- 人脸登录：三种子状态 ---------- */
  const FACE = [
    { key: 'check', title: '核对人脸底片' },
    { key: 'capture', title: '采集人脸' },
    { key: 'liveness', title: '活体检测' },
    { key: 'submit', title: '提交登记' },
    { key: 'pending', title: '等待审核' },
    { key: 'done', title: '登录成功' },
  ];

  /* 状态 0：查后端有没有该账号的人脸底片 —— 演示首次登录没有 */
  if (faceStep === 0) {
    return {
      navbar: AppNavbar({ title: '人脸登录' }),
      statusbar: AppStatusbar({ operator: '', device: '' }),
      content: `<div class="app-login">
        ${tabs}
        <div class="app-login-form">
          <label class="app-field">
            <span>工号</span>
            <input id="faceAcct" class="app-input" value="${esc(account || 'EMP-001')}" placeholder="请输入工号" autocomplete="off">
          </label>
          <button class="app-btn app-btn--primary" data-key="face-check">开始人脸识别</button>
        </div>
        <div class="app-tip">
          <b>首次使用需先登记</b>
          <p>系统里还没有你的人脸底片。首次识别会引导完成采集、活体检测与提交，审核通过后即可刷脸登录。</p>
        </div>
      </div>`,
      actionbar: `<button class="app-btn app-btn--ghost" data-key="back">返回</button>`,
    };
  }

  /* 状态 1：没有底片 —— 告知并引导采集 */
  if (faceStep === 1) {
    return {
      navbar: AppNavbar({ title: '未登记人脸' }),
      statusbar: AppStatusbar({ operator: '', device: '' }),
      content: `<div class="app-login">
        <div class="app-face-result">
          <span class="app-face-icon app-face-icon--warn">!</span>
          <b>未查询到人脸底片</b>
          <p>账号 <b>${esc(faceInfo.account || '')}</b> 尚未登记人脸。首次使用需要先完成一次采集。</p>
        </div>
        <div class="app-card">
          <div class="app-kv"><span>账号</span><b>${esc(faceInfo.account || '—')}</b></div>
          <div class="app-kv"><span>姓名</span><b>${esc(faceInfo.name || '—')}</b></div>
          <div class="app-kv"><span>人脸状态</span><b>未登记</b></div>
        </div>
        <div class="app-tip app-tip--warn">
          <b>接下来会发生什么</b>
          <p>采集正脸 → 活体检测（眨眼或转头）→ 提交给后端 → 管理员审核 → 审核通过后可刷脸登录。</p>
        </div>
      </div>`,
      actionbar: `<button class="app-btn app-btn--ghost" data-key="login-pwd">改用密码登录</button>
        <button class="app-btn app-btn--primary" data-key="face-start">开始采集</button>`,
    };
  }

  /* 状态 2：采集中 */
  if (faceStep === 2) {
    return {
      navbar: AppNavbar({ title: '采集人脸' }),
      statusbar: AppStatusbar({ operator: '', device: '' }),
      content: `<div class="app-login">
        <div class="app-face-frame">
          <div class="app-face-oval"></div>
          <span class="app-scan-line"></span>
        </div>
        <p class="app-face-hint">请将面部置于框内，正对镜头</p>
        <div class="app-face-steps">
          <div class="app-face-step is-done"><span>1</span><b>采集正脸</b><small>已捕获</small></div>
          <div class="app-face-step is-active"><span>2</span><b>活体检测</b><small>请眨一下眼</small></div>
          <div class="app-face-step"><span>3</span><b>提交登记</b><small>待完成</small></div>
        </div>
        <div class="app-tip app-tip--warn">
          <b>隐私说明</b>
          <p>仅采集人脸特征值用于身份核验，不保存原始照片，不上传与本作业无关的内容。</p>
        </div>
      </div>`,
      actionbar: `<button class="app-btn app-btn--ghost" data-key="face-cancel">取消</button>
        <button class="app-btn app-btn--primary" data-key="face-done">完成采集</button>`,
    };
  }

  /* 状态 3：提交成功待审核 */
  if (faceStep === 3) {
    return {
      navbar: AppNavbar({ title: '已提交登记' }),
      statusbar: AppStatusbar({ operator: '', device: '' }),
      content: `<div class="app-login">
        <div class="app-face-result">
          <span class="app-face-icon app-face-icon--ok">✓</span>
          <b>人脸已提交</b>
          <p>采集完成，已提交后端。管理员审核通过后即可刷脸登录。</p>
        </div>
        <div class="app-card">
          <div class="app-kv"><span>提交时间</span><b>2026-10-29 23:30</b></div>
          <div class="app-kv"><span>审核状态</span><b>待审核</b></div>
          <div class="app-kv"><span>预计时长</span><b>1 个工作日内</b></div>
        </div>
        <div class="app-tip">
          <b>审核期间如何作业</b>
          <p>人脸审核不影响本次登录。你可以先用账号密码进入系统开始作业。</p>
        </div>
      </div>`,
      actionbar: `<button class="app-btn app-btn--primary" data-key="login-pwd">用密码登录继续</button>`,
    };
  }

  /* 状态 4：已有底片，直接识别（演示已登记的情况） */
  return {
    navbar: AppNavbar({ title: '人脸识别' }),
    statusbar: AppStatusbar({ operator: '', device: '' }),
    content: `<div class="app-login">
      <div class="app-face-frame is-scanning">
        <div class="app-face-oval"></div>
        <span class="app-scan-line"></span>
      </div>
      <p class="app-face-hint">识别中，请保持面部在框内</p>
    </div>`,
    actionbar: `<button class="app-btn app-btn--ghost" data-key="login-pwd">改用密码</button>
      <button class="app-btn app-btn--primary" data-key="face-pass">识别成功</button>`,
  };
}

/* 绑定输入与反馈样式 */
export const APP_BIND_CSS = `
.app-field{display:flex;flex-direction:column;gap:4px}
.app-field>span{font-size:12px;color:var(--pda-color-text-secondary)}
.app-input{
  height:44px;padding:0 12px;border:1px solid var(--pda-color-border);
  border-radius:8px;background:var(--pda-color-surface);
  font-family:var(--pda-font-family);font-size:14px;color:var(--pda-color-text-primary);
}
.app-input:focus{outline:none;border-color:var(--pda-color-brand)}
.app-bind-msg{
  margin:0;min-height:20px;padding:0 4px;font-size:12px;line-height:20px;
  color:var(--pda-color-text-muted);
}
.app-bind-msg.is-ok{color:var(--pda-color-brand)}
.app-bind-msg.is-bad{color:var(--pda-color-dangerText)}
`;

/* 登录与人脸登记样式 */
export const APP_LOGIN_CSS = `
.app-login{padding:var(--pda-space-content);display:flex;flex-direction:column;gap:var(--pda-space-content)}
.app-login-brand{
  display:flex;flex-direction:column;align-items:center;gap:4px;
  padding:var(--pda-space-large) 0 var(--pda-space-small);
}
.app-login-logo{
  width:56px;height:56px;display:grid;place-items:center;border-radius:8px;
  background:var(--pda-color-brand);color:var(--pda-color-text-inverse);
  font-size:18px;font-weight:700;
}
.app-login-brand b{font-size:16px;font-weight:600;color:var(--pda-color-text-primary)}
.app-login-brand small{font-size:12px;color:var(--pda-color-text-muted)}

.app-login-tabs{
  display:flex;gap:0;border-bottom:1px solid var(--pda-color-border);
}
.app-login-tab{
  flex:1;height:40px;border:0;background:transparent;cursor:pointer;
  font-family:var(--pda-font-family);font-size:14px;color:var(--pda-color-text-secondary);
  position:relative;
}
.app-login-tab.is-active{color:var(--pda-color-brand);font-weight:600}
.app-login-tab.is-active::after{
  content:'';position:absolute;left:24%;right:24%;bottom:-1px;height:2px;
  background:var(--pda-color-brand);border-radius:0;
}

.app-login-form{display:flex;flex-direction:column;gap:var(--pda-space-content)}
.app-login-row{display:flex;align-items:center;justify-content:space-between}
.app-check{display:flex;align-items:center;gap:4px;font-size:12px;color:var(--pda-color-text-secondary)}
.app-check input{width:16px;height:16px;accent-color:var(--pda-color-brand)}
.app-link{
  border:0;background:transparent;padding:0;cursor:pointer;
  font-family:var(--pda-font-family);font-size:12px;color:var(--pda-color-brand);
}
.app-code-row{display:flex;gap:8px}
.app-code-row .app-input{flex:1}
.app-code-btn{flex:0 0 auto;width:88px;height:44px}

/* 人脸框 */
.app-face-frame{
  position:relative;height:260px;border-radius:8px;
  background:var(--pda-color-mask);display:grid;place-items:center;overflow:hidden;
}
.app-face-oval{
  width:170px;height:210px;border-radius:50%;
  border:2px dashed var(--pda-color-brand);
  background:rgba(255,255,255,.06);
}
.app-face-hint{margin:0;text-align:center;font-size:12px;color:var(--pda-color-text-secondary)}

.app-face-steps{display:flex;flex-direction:column;gap:4px}
.app-face-step{
  display:flex;align-items:center;gap:8px;padding:12px;
  background:var(--pda-color-surface);border-radius:8px;
}
.app-face-step span{
  width:22px;height:22px;flex:none;display:grid;place-items:center;border-radius:50%;
  background:var(--pda-color-page);color:var(--pda-color-text-muted);
  font-size:12px;font-weight:600;
}
.app-face-step b{flex:1;font-size:14px;font-weight:500}
.app-face-step small{font-size:12px;color:var(--pda-color-text-muted)}
.app-face-step.is-done span{background:var(--pda-color-brand-soft);color:var(--pda-color-brand)}
.app-face-step.is-done small{color:var(--pda-color-brand)}
.app-face-step.is-active span{background:var(--pda-color-brand);color:var(--pda-color-text-inverse)}
.app-face-step.is-active b{color:var(--pda-color-brand);font-weight:600}

/* 结果态 */
.app-face-result{
  display:flex;flex-direction:column;align-items:center;gap:8px;
  padding:var(--pda-space-large) var(--pda-space-content);text-align:center;
  background:var(--pda-color-surface);border-radius:8px;
}
.app-face-result b{font-size:16px;font-weight:600}
.app-face-result p{margin:0;font-size:12px;line-height:1.6;color:var(--pda-color-text-secondary)}
.app-face-icon{
  width:52px;height:52px;display:grid;place-items:center;border-radius:50%;
  font-size:18px;font-weight:700;
}
.app-face-icon--warn{background:var(--pda-color-warning-soft);color:var(--pda-color-warning-text)}
.app-face-icon--ok{background:var(--pda-color-brand-soft);color:var(--pda-color-brand)}
`;

/* ============================ 我的 ============================ */
/**
 * 个人中心。移动端标配页面，聚合个人身份、设备、设置与退出。
 * 替代原来语义不明的「切换模块」按钮。
 */
export function renderMine({ operator = '', device = '', warehouse = '', zone = '', unitId = '', shift = '', accountId = '', loginAt = '', today = null }) {
  /* 今日完成：只给自己看，不做排名、不做导出 */
  const t = today || { total: 0, putaway: 0, relocation: 0, stocktaking: 0 };

  const rows = [
    { key: 'my-device', icon: '▣', name: '我的设备', desc: `${esc(device)} · 序列号与换机` },
    { key: 'app-version', icon: '⤓', name: '版本与更新', desc: `当前 v1.0.4 · 检查更新` },
  ];
  const list = rows.map((r) => `
    <a class="app-entry-card" href="#${r.key}">
      <span class="app-entry-icon">${r.icon}</span>
      <span class="app-entry-body">
        <b>${r.name}</b>
        <small>${r.desc}</small>
      </span>
      <span class="app-entry-arrow">›</span>
    </a>`).join('');

  return {
    navbar: AppNavbar({ title: '我的' }),
    statusbar: AppStatusbar({ operator, device }),
    content: `<div class="app-home">
      <div class="app-mine-head">
        <span class="app-mine-avatar">${esc(operator.slice(0, 1))}</span>
        <div class="app-mine-info">
          <b>${esc(operator)}</b>
          <small>${esc(warehouse)}${zone ? ' / ' + esc(zone) : ''}${shift ? ' · ' + esc(shift) : ''}</small>
          <small class="app-mine-sub">设备 ${esc(device)}${accountId ? ' · 工号 ' + esc(accountId) : ''}${loginAt ? ' · ' + esc(loginAt.slice(11,16)) + ' 登录' : ''}</small>
        </div>
      </div>

      <div class="app-today">
        <div class="app-today-head">
          <span>今日完成</span>
          <b>${t.total}<small> 单</small></b>
        </div>
        <div class="app-today-bars">
          <div class="app-today-item"><span>上架</span><b>${t.putaway}</b></div>
          <div class="app-today-item"><span>移库</span><b>${t.relocation}</b></div>
          <div class="app-today-item"><span>盘点</span><b>${t.stocktaking}</b></div>
        </div>
      </div>

      ${list}
      <button class="app-btn app-btn--ghost app-mine-logout" data-key="do-logout">退出登录</button>
    </div>`,
    actionbar: `<button class="app-btn app-btn--ghost" data-key="back-to-work">回作业主页</button>`,
  };
}

/* ============================ 版本与更新 ============================ */
/**
 * APP 版本检查与更新。
 *
 * 现场常见问题：作业员用的是旧版本，界面与功能对不上，报故障时说不清版本号。
 * 这一页给出当前版本、服务端最新版本、更新说明与一键更新入口。
 */
export function renderAppVersion({ operator = '', device = '', current = '1.0.4', latest = '1.0.6', notes = [], state = 'idle' }) {
  const newer = current !== latest;
  const isUpdating = state === 'updating';
  const isDone = state === 'done';

  const noteList = notes.length
    ? notes.map((n) => `<li>${esc(n)}</li>`).join('')
    : '<li>暂无更新说明</li>';

  const badge = isDone
    ? '<span class="app-state app-state--done">已是最新</span>'
    : isUpdating
      ? '<span class="app-state app-state--busy">下载中</span>'
      : newer
        ? '<span class="app-state app-state--wait">有新版本</span>'
        : '<span class="app-state app-state--done">已是最新</span>';

  const body = isDone
    ? `<div class="app-face-result">
         <span class="app-face-icon app-face-icon--ok">✓</span>
         <b>已更新到 v${esc(latest)}</b>
         <p>下次启动生效。当前会话仍可继续作业。</p>
       </div>`
    : isUpdating
      ? `<div class="app-update">
         <div class="app-update-bar"><span style="width:62%"></span></div>
         <p class="app-update-pct">正在下载 62%</p>
         <p class="app-update-hint">请保持网络连接，不要退出 App</p>
       </div>`
      : `<div class="app-card">
         <div class="app-kv"><span>当前版本</span><b>v${esc(current)}</b></div>
         <div class="app-kv"><span>最新版本</span><b>v${esc(latest)}</b></div>
         <div class="app-kv"><span>安装包大小</span><b>18.4 MB</b></div>
         <div class="app-kv"><span>更新时间</span><b>2026-10-28</b></div>
       </div>
       <div class="app-tip">
         <b>本次更新内容</b>
         <ul class="app-note-list">${noteList}</ul>
       </div>`;

  return {
    navbar: AppNavbar({ title: '版本与更新', showBack: true, right: badge }),
    statusbar: AppStatusbar({ operator, device }),
    content: `<div class="app-mydev">
      ${body}
      ${!isDone && !isUpdating ? '<p class="app-ident-note">更新由系统自动下载安装，无需前往应用商店。</p>' : ''}
    </div>`,
    actionbar: isDone
      ? `<button class="app-btn app-btn--primary" data-key="back">返回</button>`
      : isUpdating
        ? `<button class="app-btn app-btn--ghost" data-key="back">后台下载</button>`
        : newer
          ? `<button class="app-btn app-btn--primary" data-key="do-update">立即更新</button>`
          : `<button class="app-btn app-btn--ghost" data-key="do-check">检查更新</button>`,
  };
}

/* 我的页样式 */
export const APP_MINE_CSS = `
.app-mine-head{
  display:flex;align-items:center;gap:var(--pda-space-content);
  padding:var(--pda-space-page) var(--pda-space-content);
  border-radius:8px;background:var(--pda-color-brand-soft);
}
.app-mine-avatar{
  width:52px;height:52px;flex:none;display:grid;place-items:center;border-radius:50%;
  background:var(--pda-color-brand);color:var(--pda-color-text-inverse);
  font-size:18px;font-weight:600;
}
.app-mine-info{display:flex;flex-direction:column;gap:4px;min-width:0}
.app-mine-info b{font-size:16px;font-weight:600;color:var(--pda-color-text-primary)}
.app-mine-info small{font-size:12px;color:var(--pda-color-brand)}
.app-mine-logout{margin-top:var(--pda-space-small);color:var(--pda-color-dangerText)}
.app-mine-sub{font-size:12px;color:var(--pda-color-text-muted)}

/* 今日完成 */
.app-today{
  padding:var(--pda-space-content) var(--pda-space-content);
  border-radius:8px;background:var(--pda-color-surface);
}
.app-today-head{
  display:flex;align-items:baseline;justify-content:space-between;
  padding-bottom:var(--pda-space-small);border-bottom:1px solid var(--pda-color-divider);
}
.app-today-head span{font-size:12px;color:var(--pda-color-text-secondary)}
.app-today-head b{font-size:18px;font-weight:700;color:var(--pda-color-brand);font-variant-numeric:tabular-nums}
.app-today-head b small{font-size:12px;font-weight:500;color:var(--pda-color-text-muted)}
.app-today-bars{display:flex;gap:var(--pda-space-small);padding-top:var(--pda-space-small)}
.app-today-item{flex:1;display:flex;flex-direction:column;gap:4px;align-items:center}
.app-today-item span{font-size:12px;color:var(--pda-color-text-muted)}
.app-today-item b{font-size:15px;font-weight:600;font-variant-numeric:tabular-nums}

/* 更新 */
.app-update{padding:var(--pda-space-large) var(--pda-space-content);border-radius:8px;background:var(--pda-color-surface)}
.app-update-bar{
  height:6px;border-radius:8px;background:var(--pda-color-page);overflow:hidden;
}
.app-update-bar span{display:block;height:100%;background:var(--pda-color-brand);border-radius:8px}
.app-update-pct{margin:var(--pda-space-small) 0 0;text-align:center;font-size:15px;font-weight:600;color:var(--pda-color-brand)}
.app-update-hint{margin:4px 0 0;text-align:center;font-size:12px;color:var(--pda-color-text-muted)}
.app-note-list{margin:6px 0 0;padding-left:16px;font-size:12px;line-height:1.8}
.app-note-list li{color:var(--pda-color-brand);opacity:.9}
`;

/* ============================ 简单占位页 ============================ */
/**
 * 我的 › 出勤 / 设置 的占位页。
 * 本期原型重点是设备与登录，这两项给出结构说明，不做完整交互。
 */
export function renderSimplePage({ title = '', operator = '', device = '', rows = [], note = '' }) {
  const kv = rows.map(([k, v]) => `<div class="app-kv"><span>${esc(k)}</span><b>${esc(v)}</b></div>`).join('');
  return {
    navbar: AppNavbar({ title, showBack: true }),
    statusbar: AppStatusbar({ operator, device }),
    content: `<div class="app-mydev">
      <div class="app-card">${kv}</div>
      ${note ? `<div class="app-tip"><b>说明</b><p>${esc(note)}</p></div>` : ''}
    </div>`,
    actionbar: `<button class="app-btn app-btn--ghost" data-key="back">返回</button>`,
  };
}

/* ============================ 我的任务 ============================ */
/**
 * 我的任务。
 *
 * 物理约束：一个人同一时刻只能在一个库位做一件事。
 * 因此分两段展示：当前在做（最多 1 个）+ 待做队列（其余），不平铺。
 * 依据 FR-EU-02 与数据表「同人同类型同时只有一个活动单元」。
 */
export function renderMyTasks({ operator = '', device = '', doing = null, queue = [] }) {
  const KIND = { putaway: '上架', relocation: '移库', stocktaking: '盘点' };
  const STATE = { putaway: '上架中', moving: '移库中', counting: '盘点中', pending: '待作业' };

  const doingCard = doing ? `
    <div class="app-doing">
      <div class="app-doing-head">
        <span class="app-doing-tag">当前在做</span>
        <b>${esc(doing.id)}</b>
      </div>
      <div class="app-doing-meta">
        <span>${esc(KIND[doing.kind] || '')}</span>
        <span>${esc(STATE[doing.status] || '')}</span>
        <span>${esc(doing.warehouse || '')}</span>
      </div>
      <a class="app-doing-go" href="#${doing.kind}-work?id=${encodeURIComponent(doing.id)}">继续作业 ›</a>
    </div>`
    : `<div class="app-empty"><b>当前没有在做的任务</b><p>去作业主页接一张单</p></div>`;

  const queueHtml = queue.length
    ? queue.map((t) => `
      <div class="app-queue-item">
        <span class="app-queue-kind">${esc(KIND[t.kind] || '')}</span>
        <b class="app-queue-id">${esc(t.id)}</b>
        <small>${esc(t.warehouse || '')}</small>
      </div>`).join('')
    : '<div class="app-empty"><b>队列为空</b><p>没有等待中的任务</p></div>';

  return {
    navbar: AppNavbar({ title: '我的任务', showBack: true }),
    statusbar: AppStatusbar({ operator, device }),
    content: `<div class="app-mytask">
      <section class="app-mytask-sec">
        <h4>当前在做</h4>
        ${doingCard}
      </section>
      <section class="app-mytask-sec">
        <h4>待做 <span class="app-mytask-count">${queue.length}</span></h4>
        <p class="app-mytask-note">做完手上这张，再按队列顺序接下一张</p>
        <div class="app-queue">${queueHtml}</div>
      </section>
    </div>`,
    actionbar: `<button class="app-btn app-btn--ghost" data-key="back">返回</button>
      <button class="app-btn app-btn--primary" data-key="home">去接任务</button>`,
  };
}

/* 我的任务样式 */
export const APP_MYTASK_CSS = `
.app-mytask{padding:var(--pda-space-content);display:flex;flex-direction:column;gap:var(--pda-space-content)}
.app-mytask-sec h4{
  margin:0 0 8px;font-size:14px;font-weight:600;color:var(--pda-color-text-primary);
  display:flex;align-items:center;gap:8px;
}
.app-mytask-count{
  display:inline-grid;place-items:center;min-width:20px;height:20px;padding:0 6px;
  border-radius:8px;background:var(--pda-color-brand-soft);color:var(--pda-color-brand);
  font-size:12px;font-weight:600;
}
.app-mytask-note{margin:0 0 8px;font-size:12px;color:var(--pda-color-text-muted)}

.app-doing{
  display:flex;flex-direction:column;gap:8px;
  padding:var(--pda-space-content);border-radius:8px;
  background:var(--pda-color-surface);border-left:3px solid var(--pda-color-brand);
}
.app-doing-head{display:flex;align-items:center;gap:8px}
.app-doing-tag{
  padding:0 6px;border-radius:8px;background:var(--pda-color-brand);
  color:var(--pda-color-text-inverse);font-size:12px;line-height:20px;
}
.app-doing-head b{font-size:14px;font-family:ui-monospace,Menlo,monospace}
.app-doing-meta{display:flex;gap:8px;font-size:12px;color:var(--pda-color-text-secondary)}
.app-doing-go{
  align-self:flex-start;padding:4px 10px;border-radius:8px;
  background:var(--pda-color-brand-soft);color:var(--pda-color-brand);
  font-size:12px;text-decoration:none;
}

.app-queue{display:flex;flex-direction:column;gap:4px}
.app-queue-item{
  display:flex;align-items:center;gap:8px;padding:10px var(--pda-space-content);
  border-radius:8px;background:var(--pda-color-surface);
}
.app-queue-kind{
  flex:none;width:36px;text-align:center;padding:0 6px;border-radius:8px;
  background:var(--pda-color-page);color:var(--pda-color-text-secondary);
  font-size:12px;line-height:20px;
}
.app-queue-id{flex:1;font-size:14px;font-family:ui-monospace,Menlo,monospace;font-weight:500}
.app-queue-item small{font-size:12px;color:var(--pda-color-text-muted)}
`;

/* ============================ 自带手机接入（接入码） ============================ */
/**
 * 员工自带手机首次接入。
 *
 * 与公司机的区别：没有资产编号、不贴二维码、不进硬件档案。
 * 身份靠应用 UUID，接入靠管理员发放的接入码。一机一码，与公司机同一套机制。
 */
export function renderInvite({ operator = '', deviceInfo = {}, phase = 'input', errMsg = '' }) {
  const hero = `<div class="app-device-hero">
      <span class="app-device-hero__icon">▣</span>
      <div>
        <b>本机 · 员工自带</b>
        <small>${esc(deviceInfo.model || 'Android 手机')}</small>
      </div>
      <span class="app-state app-state--mute">未接入</span>
    </div>`;

  const ident = `<div class="app-card">
      <div class="app-kv"><span>应用 UUID</span><b>${esc(deviceInfo.appUuid || '—')}</b></div>
      <div class="app-kv"><span>机型</span><b>${esc(deviceInfo.model || '—')}</b></div>
      <div class="app-kv"><span>系统</span><b>${esc(deviceInfo.os || 'Android')}</b></div>
      <div class="app-kv"><span>使用人</span><b>${esc(operator || '—')}</b></div>
    </div>
    <p class="app-ident-note">以上由本机自动读取。自带手机不采集 IMEI、不占资产编号。</p>`;

  if (phase === 'done') {
    return {
      navbar: AppNavbar({ title: '接入完成', showBack: true }),
      statusbar: AppStatusbar({ operator, device: '自带手机' }),
      content: `<div class="app-bind">
        <div class="app-face-result">
          <span class="app-face-icon app-face-icon--ok">✓</span>
          <b>已提交接入</b>
          <p>管理员确认后本机即可接任务。确认前可用其他已登记设备作业。</p>
        </div>
        ${ident}
      </div>`,
      actionbar: `<button class="app-btn app-btn--primary" data-key="home">返回主页</button>`,
    };
  }

  return {
    navbar: AppNavbar({ title: '接入自带手机', showBack: true }),
    statusbar: AppStatusbar({ operator, device: '自带手机' }),
    content: `<div class="app-bind">
      ${hero}
      ${ident}
      <label class="app-field">
        <span>接入码（向仓管索取）</span>
        <input id="inviteCode" class="app-input" maxlength="8" inputmode="numeric"
               placeholder="请输入 8 位接入码" autocomplete="off" style="text-transform:uppercase">
      </label>
      <p class="app-bind-msg${errMsg ? ' is-bad' : ''}" id="inviteMsg">${esc(errMsg)}</p>
      <div class="app-tip">
        <b>为什么不贴二维码</b>
        <p>这是你自己的手机，公司不贴资产标签。系统只记录「你的哪台手机接入过」，换机可解绑重绑。</p>
      </div>
    </div>`,
    actionbar: `<button class="app-btn app-btn--ghost" data-key="back">返回</button>
      <button class="app-btn app-btn--primary" data-key="do-invite">提交</button>`,
  };
}
