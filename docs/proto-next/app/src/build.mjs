#!/usr/bin/env node
/**
 * APP 端构建。
 *
 * 数据边界：只允许读 ../../shared/data/。读同级 pc 目录时立即抛错。
 *
 * 定位：APP 是执行单元（PDA 型）的设备端。
 * 每个已登录的 PDA 单元 = 一台 APP 设备实例；任务按 operatorName 归属到人。
 * 演示「不同作业员看到不同任务」：URL 参数 ?unit=EU-HP-002 切换终端。
 */
import fs from 'node:fs';
import path from 'node:path';
import crypto from 'node:crypto';
import { fileURLToPath } from 'node:url';
import { verifyTaskRules } from '../../shared/verify-task-rules.mjs';
import { verifyClassNames } from '../../shared/verify-class-names.mjs';
import { AppShell, APP_CSS } from './app-shell.mjs';
import {
  renderHome, renderTaskList, renderWork,
  renderMyDevice, renderActivate, renderSwitch, renderLoginGate, renderLogin, renderMine, renderAppVersion, renderSimplePage, renderMyTasks, renderInvite,
  APP_PAGES_CSS, APP_MYDEV_CSS, APP_BIND_CSS, APP_LOGIN_CSS, APP_MINE_CSS, APP_MYTASK_CSS,
} from './app-pages.mjs';

const HERE = path.dirname(fileURLToPath(import.meta.url));
const SRC = path.resolve(HERE, '..');
const NEXT = path.resolve(SRC, '..');
const SHARED = path.join(NEXT, 'shared/data');
const PEER = path.join(NEXT, 'pc');
const OUT = path.join(NEXT, 'dist', 'app.html');

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
  if (bad.length) throw new Error('[数据边界] 构建期读到同级对端目录：\n  ' + bad.join('\n  '));
}

/* 作业步骤：按模块定义，扫码驱动 */
function stepsFor(kind) {
  const s = (label) => ({ label, done: false, active: false });
  if (kind === 'putaway') return [s('扫描商品条码'), s('扫描目标库位'), s('录入数量'), s('归还空载具')];
  if (kind === 'relocation') return [s('扫描源库位'), s('扫描载具码'), s('扫描容器编码'), s('扫描商品条码'), s('扫描目标库位'), s('录入数量')];
  return [s('扫描库位'), s('扫描商品'), s('录入实盘数量'), s('提交盘点结果')];
}
function activateFirst(steps) {
  const out = steps.map((x) => ({ ...x }));
  if (out.length) out[0].active = true;
  return out;
}

const PENDING = {
  putaway: ['pending', 'putaway'],
  relocation: ['pending', 'moving'],
  stocktaking: ['pending', 'counting'],
};

/* 今日完成统计：只统计状态为 completed 且完成日期为今天的任务 */
const TODAY_STR = '2026-10-30';
function todayStats(data, who) {
  const out = { total: 0, putaway: 0, relocation: 0, stocktaking: 0 };
  if (!who) return out;
  const hit = (rows) => rows.filter((r) => r.operatorName === who
    && r.status === 'completed'
    && String(r.completedAt || '').startsWith(TODAY_STR)).length;
  out.putaway = hit(data.putaway);
  out.relocation = hit(data.relocation);
  out.stocktaking = hit(data.stocktaking);
  out.total = out.putaway + out.relocation + out.stocktaking;
  return out;
}

/* 为一个执行单元生成全部场景 */
function buildScenes(unit, data) {
  const who = unit.currentOperatorName;
  /* 未登录单元没有作业员，不得匹配任何任务（否则会命中未指派任务） */
  const mine = (rows, kind) => !who ? [] : rows.filter(
    (r) => r.operatorName === who && PENDING[kind].includes(r.status),
  );

  const myPut = mine(data.putaway, 'putaway');
  const myMove = mine(data.relocation, 'relocation');
  const myCount = mine(data.stocktaking, 'stocktaking');

  const dev = data.hardware.find((x) => x.id === unit.terminalId) || {};
  const cls = data.classes.find((c) => c.code === dev.classCode) || {};

  const ctx = {
    operator: who,
    device: unit.terminalId,
    unitId: unit.id,
    warehouse: unit.warehouse || '',
    zone: unit.zone || '',
    devInfo: {
      id: dev.id, name: dev.name, form: dev.form,
      className: cls.name, accessType: dev.accessType,
      imei: dev.imei, appUuid: dev.appUuid, sn: dev.sn,
      statusLabel: dev.statusLabel,
      bindStatus: dev.bindStatus || '已绑定',
      boundAt: dev.boundAt || '',
    },
  };
  const time = (unit.loginAt || '').split(' ')[1] || '';

  /* 当前在做：三类任务里第一条在办 */
  const IN_ST = ['putaway','moving','counting'];
  const firstDoing = [
    ...myPut.map(t => ({ ...t, kind:'putaway', kindName:'上架' })),
    ...myMove.map(t => ({ ...t, kind:'relocation', kindName:'移库' })),
    ...myCount.map(t => ({ ...t, kind:'stocktaking', kindName:'盘点' })),
  ].find(t => IN_ST.includes(t.status)) || null;

  const homeStats = { putaway: myPut.length, relocation: myMove.length, stocktaking: myCount.length, time, doing: firstDoing };
  /* 门禁：未登录 → 登录；已登录但设备未绑定 → 必须先激活，不得进入作业 */
  const bound = (dev.bindStatus || '已绑定') === '已绑定';
  const home = !unit.currentOperatorId
    ? renderLogin({ phase: 'pwd' })
    : !bound
      ? renderActivate({ ...ctx, phase: 'identify', deviceInfo: ctx.devInfo,
                         errMsg: '本机尚未绑定。绑定成功后才能接任务。' })
      : renderHome({ ...ctx, stats: homeStats });

  const listPut = renderTaskList({ title: '上架作业任务', kind: 'putaway', tasks: myPut, ...ctx });
  const listMove = renderTaskList({ title: '移库作业任务', kind: 'relocation', tasks: myMove, ...ctx });
  const listCount = renderTaskList({ title: '盘点作业任务', kind: 'stocktaking', tasks: myCount, ...ctx });

  const workOf = (kind, rows, first) => rows.length
    ? renderWork({ kind, task: rows[0], steps: activateFirst(stepsFor(kind)), ...ctx })
    : null;

  const scenes = {
    home,
    /* 未登录设备：登录成功后落到作业主页 */
    'work-home': renderHome({
      ...ctx,
      operator: '张伟',
      device: unit.terminalId,
      stats: { putaway: 2, relocation: 0, stocktaking: 0, time: '08:00' },
    }),
    'putaway-list': listPut,
    'relocation-list': listMove,
    'stocktaking-list': listCount,
    mine: renderMine({
      operator: ctx.operator, device: ctx.device,
      warehouse: ctx.warehouse, zone: ctx.zone, unitId: ctx.unitId,
      shift: unit.shift || '', accountId: unit.currentOperatorId || '',
      loginAt: unit.loginAt || '', today: todayStats(data, who),
    }),
    'app-version': renderAppVersion({ operator: ctx.operator, device: ctx.device,
      current: '1.0.4', latest: '1.0.6',
      notes: ['修复扫码后偶发白屏', '上架页支持一次扫多箱', '提升弱网下的提交成功率'] }),
    'app-version-updating': renderAppVersion({ operator: ctx.operator, device: ctx.device,
      current: '1.0.4', latest: '1.0.6', state: 'updating' }),
    'app-version-done': renderAppVersion({ operator: ctx.operator, device: ctx.device,
      current: '1.0.4', latest: '1.0.6', state: 'done' }),
    'my-device': renderMyDevice({
      ...ctx,
      deviceInfo: ctx.devInfo,
      binding: { bound: true, since: unit.loginAt || '' },
    }),
    login: renderLogin({ phase: 'pwd' }),
    'login-face': renderLogin({ phase: 'face', faceStep: 0 }),
    'face-noface': renderLogin({ phase: 'face', faceStep: 1, faceInfo: { account: 'EMP-004', name: '王芳' } }),
    'face-capture': renderLogin({ phase: 'face', faceStep: 2 }),
    'face-submitted': renderLogin({ phase: 'face', faceStep: 3 }),
    'face-scan': renderLogin({ phase: 'face', faceStep: 4 }),
    invite: renderInvite({ operator: ctx.operator, phase: 'input',
                           deviceInfo: { appUuid: 'uuid-' + unit.terminalId.toLowerCase() + '-a1b2c3',
                                         model: 'Redmi 13C', os: 'Android 13' } }),
    'invite-done': renderInvite({ operator: ctx.operator, phase: 'done',
                                  deviceInfo: { appUuid: 'uuid-' + unit.terminalId.toLowerCase() + '-a1b2c3',
                                                model: 'Redmi 13C', os: 'Android 13' } }),
    activate: renderActivate({ ...ctx, phase: 'identify', deviceInfo: ctx.devInfo }),
    'activate-confirm': renderActivate({ ...ctx, phase: 'verify', deviceInfo: ctx.devInfo }),
    'switch-device': renderSwitch({ ...ctx, fromDevice: unit.terminalId }),
  };
  const w1 = workOf('putaway', myPut);
  const w2 = workOf('relocation', myMove);
  const w3 = workOf('stocktaking', myCount);
  if (w1) scenes['putaway-work'] = w1;
  if (w2) scenes['relocation-work'] = w2;
  if (w3) scenes['stocktaking-work'] = w3;
  return { scenes, counts: { putaway: myPut.length, relocation: myMove.length, stocktaking: myCount.length } };
}

function main() {
  assertNoPeerReads();

  /* 样式类名冲突校验 */
  const clsCheck = verifyClassNames({
    modules: [
      { name: 'app-pages', source: fs.readFileSync(path.join(HERE, 'app-pages.mjs'), 'utf8') },
      { name: 'app-shell', source: fs.readFileSync(path.join(HERE, 'app-shell.mjs'), 'utf8') },
    ],
  });
  if (clsCheck.length) {
    throw new Error('[类名冲突] ' + clsCheck.length + ' 条：\n  ' + clsCheck.join('\n  '));
  }

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

  const hardware = readShared('hardware');
  /* 作业终端清单：手机与 PDA */
  const bindables = hardware
    .filter((d) => d.classCode === 'HW-PDA')
    .map((d) => ({ id: d.id, name: d.name, accessType: d.accessType || '受管设备', holder: d.location || '' }));

  const data = {
    units: readShared('exec-units'),
    hardware,
    bindables,
    classes: readShared('hardware-classes'),
    putaway: readShared('putaway-tasks'),
    relocation: readShared('relocation-tasks'),
    stocktaking: readShared('stocktaking-tasks'),
  };

  /* 生成条件三条任一满足：有待办任务、是自带设备、是手机单元 */
  const hwMap = new Map(data.hardware.map((d) => [d.id, d]));
  const candidates = data.units.filter((u) => u.type === 'EXEC-PDA');
  const terminals = [];
  for (const u of candidates) {
    const dev = hwMap.get(u.terminalId) || {};
    const isByod = dev.accessType === '自有设备';
    const isPhone = dev.form === '手机';
    if (!u.currentOperatorId && !isPhone) continue;
    const built = buildScenes(u, data);
    const total = built.counts.putaway + built.counts.relocation + built.counts.stocktaking;
    if (total > 0 || isByod || isPhone) terminals.push({ unit: u, ...built, total, isByod, isPhone });
  }
  /* 默认入口：未登录设备排最前，打开即见登录页；其余按待办量降序 */
  terminals.sort((a, b) => {
    const aUn = a.unit.currentOperatorId ? 0 : 1;
    const bUn = b.unit.currentOperatorId ? 0 : 1;
    return (bUn - aUn) || (b.total - a.total) || (b.isByod - a.isByod) || (b.isPhone - a.isPhone);
  });

  if (!terminals.length) {
    throw new Error('[执行单元] 没有任何已登录 PDA 单元持有待办任务，无法构建 APP 端');
  }

  /* 所有终端的所有场景平铺，用 data-unit + data-route 双键定位 */
  const blocks = [];
  for (const t of terminals) {
    for (const [route, view] of Object.entries(t.scenes)) {
      blocks.push(AppShell({
        statusbar: view.statusbar,
        navbar: view.navbar,
        content: view.content,
        actionbar: view.actionbar,
      }).replace('class="app-stage"',
        `class="app-stage" data-unit="${t.unit.id}" data-route="${route}" hidden`));
    }
  }

  const index = terminals.map((t) => ({
    bound: (t.unit && (data.hardware.find(h=>h.id===t.unit.terminalId)||{}).bindStatus || '已绑定') === '已绑定',
    unitId: t.unit.id,
    terminal: t.unit.terminalId,
    operator: t.unit.currentOperatorName,
    warehouse: t.unit.warehouse,
    zone: t.unit.zone,
    counts: t.counts,
  }));

  const CSS = [APP_CSS, APP_PAGES_CSS, APP_MYDEV_CSS, APP_BIND_CSS, APP_LOGIN_CSS, APP_MINE_CSS, APP_MYTASK_CSS].join('\n');

  const ROUTER = `
(function(){
  var TERMINALS = ${JSON.stringify(index)};
  var stages = [].slice.call(document.querySelectorAll('.app-stage'));

  function params(){
    var q = location.search.replace(/^\\?/, '');
    var o = {};
    q.split('&').forEach(function(p){ if(!p) return; var kv=p.split('='); o[decodeURIComponent(kv[0])]=decodeURIComponent(kv[1]||''); });
    return o;
  }

  /* 当前终端：?unit= 指定，否则取第一个 */
  function currentUnit(){
    var want = params().unit;
    if (want) { for (var i=0;i<TERMINALS.length;i++) if (TERMINALS[i].unitId===want) return TERMINALS[i]; }
    return TERMINALS[0];
  }

  /* 内部路由：不依赖 location.hash，沙箱 iframe 里 hash 赋值会被静默拒绝 */
  /* 登录成功后的会话态，供门禁与头像判断 */
  var LOGGED = {};

  var CURRENT_ROUTE = (function(){
    var h=(location.hash||'').replace('#','').split('?')[0];
    return h || 'home';
  })();

  function route(){ return CURRENT_ROUTE; }

  /* 当前登录人：运行期登录优先，其次构建期静态值 */
  function currentOperatorName(){
    var u = currentUnit();
    return LOGGED[u.unitId] || u.operator || '';
  }

  /* 作业主页解析，三种情况：
     未登录 → home（登录页）；已登录未绑定 → home（首页即激活页）；已登录已绑定 → work-home */
  /* 登录成功后的去向：设备已绑定进作业主页；未绑定先进绑定流程 */
  function nextAfterLogin(){
    var u = currentUnit();
    return u.bound ? 'work-home' : 'activate';
  }

  function workHome(){
    var u = currentUnit();
    if (!(LOGGED[u.unitId] || u.operator)) return 'home';
    if (!u.bound) return 'home';
    return 'work-home';
  }

  /**
   * 混合路由：优先写 location.hash（可刷新、可后退、可分享），
   * 写入未生效时（如编辑器预览的沙箱 iframe）自动降级为内部状态。
   * 返回值表示是否走了降级路径。
   */
  function applyRoute(r){
    var want = (r === 'home' || !r) ? '' : r;
    var before = location.hash;

    try { location.hash = want; } catch(e){}

    /* 判定是否真的生效：目标为空时看是否已清空，否则看 hash 是否等于目标 */
    var after = location.hash;
    var ok = want === '' ? (after === '' || after === '#') : (after === '#' + want);

    if (!ok) {
      CURRENT_ROUTE = r || 'home';
      show();
      return false;
    }
    return true;
  }

  function go(r){
    CURRENT_ROUTE = r || 'home';
    var native = applyRoute(CURRENT_ROUTE);
    /* hash 生效则交给 hashchange 驱动渲染；降级路径已在 applyRoute 内渲染 */
    if (native) { show(); }
  }

  function show(){
    var u = currentUnit(), r = route();

    /* 登录门禁：未登录只能停在登录页。
       「我的」及各二级页都要求已登录，未登录直达一律回落。 */
    if (!u.operator && !LOGGED[u.unitId]) {
      var OPEN = /^(home|work-home|login|login-sms|login-face|face-|activate|invite)/;
      if (!OPEN.test(r)) { r = 'home'; CURRENT_ROUTE = 'home'; }
    }

    /* 作业门禁：设备未绑定不得进入作业页与作业列表。
       放在 show() 内，覆盖点击、hash 直达、前进后退所有入口。 */
    var WORK = /-list$|-work$/;
    if (WORK.test(r) && !u.bound) { r = 'home'; CURRENT_ROUTE = 'home'; }

    var hit = false;
    stages.forEach(function(el){
      var on = el.getAttribute('data-unit')===u.unitId && el.getAttribute('data-route')===r;
      if (on) { el.removeAttribute('hidden'); hit = true; } else { el.setAttribute('hidden',''); }
    });
    if (!hit) {
      stages.forEach(function(el){
        var on = el.getAttribute('data-unit')===u.unitId && el.getAttribute('data-route')==='home';
        if (on) el.removeAttribute('hidden'); else el.setAttribute('hidden','');
      });
    }
    /* 运行期同步：登录后姓名/头像/状态栏需按实际登录人刷新 */
    var who = currentOperatorName();
    if (who) {
      var vis = document.querySelector('.app-stage:not([hidden])');
      if (vis) {
        var av = vis.querySelector('.app-mine-avatar');
        var nm = vis.querySelector('.app-mine-info b');
        var na = vis.querySelector('.app-nav-me-avatar');
        var sl = vis.querySelector('.app-status-left');
        if (av) av.textContent = who.slice(0,1);
        if (nm) nm.textContent = who;
        if (na) na.textContent = who.slice(0,1);
        if (sl) sl.textContent = who;
      }
    }
    document.title = 'WMS APP 端 · ' + (who || '未登录') + ' · ' + u.terminal;
    syncPending();
  }

  /* 浏览器前进/后退或外部改 hash 时，同步内部状态再渲染 */
  window.addEventListener('hashchange', function(){
    var h=(location.hash||'').replace('#','').split('?')[0];
    CURRENT_ROUTE = h || 'home';
    show();
  });
  show();

  /* 站内锚点跳转：沙箱 iframe 里改 hash 会失效，统一拦成内部路由 */
  document.addEventListener('click', function(e){
    var link = e.target.closest('a[href^="#"]');
    /* 作业入口守卫：设备未绑定则拦截 */
    if (link) {
      var u0 = currentUnit();
      var tgt = (link.getAttribute('href')||'').replace('#','');
      if (!u0.bound && /-list$|-work$/.test(tgt)) {
        e.preventDefault();
        toast('本机未绑定，请先激活设备');
        return;
      }
    }
    if (link) {
      var target=(link.getAttribute('href')||'').replace('#','');
      if (target) { e.preventDefault(); go(target.split('?')[0]); return; }
    }

    var b = e.target.closest('[data-key]');
    if (!b) return;
    var k = b.getAttribute('data-key');
    if (k==='back') {
      /* 不依赖浏览历史：按路由层级显式返回 */
      var r0=route();
      /* 返回按导航层级：二级页回「我的」，一级页回主页 */
      if (r0 === 'home') { go('home'); }
      else if (r0 === 'work-home') { go(workHome()); }
      else if (r0 === 'mine') { go(workHome()); }
      else if (/^my-/.test(r0)) { go('mine'); }
      else if (/^activate/.test(r0)) { go('my-device'); }
      else if (/^switch-/.test(r0)) { go('my-device'); }
      else if (/^invite/.test(r0)) { go('my-device'); }
      else if (/(list|work)$/.test(r0)) { go(workHome()); }
      else if (/^login|^face-/.test(r0)) { go('login'); }
      else { go('mine'); }
    }
    else if (k==='home' || k==='menu') { go(workHome()); }
    else if (k==='to-mine') { go('mine'); }
    else if (k==='do-check') { toast('已是最新版本 v1.0.6'); }
    else if (k==='do-update') {
      go('app-version-updating');
      setTimeout(function(){ go('app-version-done'); }, 2200);
    }
    else if (k==='back-to-work') { go(workHome()); }
    else if (k==='do-logout') { toast('已退出登录'); setTimeout(function(){ go('home'); }, 800); }
    else if (k==='scan') { toast('扫描中…（原型示意）'); }
    else if (k==='ok') { toast('已提交（原型示意）'); }
    /* 设备绑定相关 */
    else if (k==='reactivate') { go('activate'); }
    else if (k==='do-switch') { toast('已登出本机'); setTimeout(function(){ go('home'); }, 800); }
    else if (k==='to-login') { go('login'); }
    else if (k==='do-login') { go('login'); }
    else if (k==='login-pwd') { go('login'); }
    else if (k==='login-face') { go('login-face'); }
    else if (k==='do-login-pwd') { doLoginPwd(); }
    else if (k==='face-check') { faceCheck(); }
    else if (k==='face-start') { go('face-capture'); }
    else if (k==='face-done') { go('face-submitted'); }
    else if (k==='face-cancel') { go('login-face'); }
    else if (k==='face-pass') { toast('识别成功'); LOGGED[currentUnit().unitId]='张伟'; setTimeout(function(){ go(nextAfterLogin()); }, 700); }
    else if (k==='login-forgot') { toast('请联系仓管重置密码'); }
    else if (k==='switch') { go('switch-device'); }
    else if (k==='do-activate') { doActivate(); }
    else if (k==='do-invite') {
      var iv = $vis('#inviteCode');
      var v = iv ? iv.value.trim().toUpperCase() : '';
      var msg = $vis('#inviteMsg');
      if (!v) { if (msg) { msg.textContent='请输入接入码'; msg.className='app-bind-msg is-bad'; } return; }
      if (!/^\\d{8}$/.test(v)) { if (msg) { msg.textContent='接入码为 8 位数字，请核对'; msg.className='app-bind-msg is-bad'; } return; }
      go('invite-done');
    }
    else if (k==='do-bind') { confirmBind(); }
    else { toast(k); }
  });

  /* ---------- 设备激活：激活码校验 ---------- */

  /* 只在当前可见舞台内查找，避免命中其它设备隐藏的同名节点 */
  function $vis(sel){
    var st = document.querySelector('.app-stage:not([hidden])');
    return st ? st.querySelector(sel) : null;
  }

  /* ---------- 登录：账号校验 ---------- */
  /* 原型内置账号：EMP-001 张伟（未登记人脸）、EMP-004 王芳（已登记人脸），密码均为 123456 */
  var ACCOUNTS = [
    { id:'EMP-001', name:'张伟', pwd:'123456', hasFace:false },
    { id:'EMP-004', name:'王芳', pwd:'123456', hasFace:true  },
  ];

  function loginErr(msg){
    var el = $vis('#loginErr');
    if (el) { el.textContent = msg; el.className = 'app-bind-msg is-bad'; }
    else toast(msg);
  }

  function findAccount(v){
    var q = String(v||'').trim();
    if (!q) return null;
    return ACCOUNTS.filter(function(a){ return a.id.toUpperCase()===q.toUpperCase(); })[0] || null;
  }

  function doLoginPwd(){
    var acct = $vis('#loginAcct'), pwd = $vis('#loginPwd');
    var a = findAccount(acct && acct.value);
    if (!acct || !acct.value.trim()) { loginErr('请输入工号'); return; }
    if (!a) { loginErr('账号不存在，请联系仓管'); return; }
    if (!pwd || !pwd.value) { loginErr('请输入密码'); return; }
    if (pwd.value !== a.pwd) { loginErr('密码错误，还可尝试 4 次'); return; }
    toast('登录成功，欢迎 ' + a.name);
    LOGGED[currentUnit().unitId] = a.name;
    /* 首次登录：登录成功但设备未绑定，必须先绑定才能作业 */
    setTimeout(function(){ go(nextAfterLogin()); }, 800);
  }

  /* ---------- 人脸：查底片 -> 没有则走登记 ---------- */
  var FACE_ACC = null;

  function faceCheck(){
    var acct = $vis('#faceAcct');
    var a = findAccount(acct && acct.value);
    if (!a) { toast('账号不存在'); return; }
    FACE_ACC = a;
    /* 有底片直接识别；没有则进登记流程 */
    go(a.hasFace ? 'face-scan' : 'face-noface');
  }

  function setMsg(text, tone){
    var el = $vis('#actMsg');
    if (!el) return;
    el.textContent = text;
    el.className = 'app-bind-msg' + (tone ? ' is-' + tone : '');
  }

  /* 激活码：原型约定 8 位数字，与 PC 侧生成规则一致 */
  /* 原型内置激活码样本，覆盖正常与四类异常 */
  var ACT_SAMPLES = {
    '12345678': { ok: true },
    '00000001': { err: 'code-wrong' },
    '00000002': { err: 'code-expired' },
    '00000003': { err: 'code-used' },
    '00000004': { err: 'code-mismatch' },
  };
  var ACT_TRY = { n: 0 };

  var ACT_MSG = {
    'code-wrong':    '激活码不正确，还可尝试 {left} 次',
    'code-expired':  '激活码已过期，请向仓管重新申请',
    'code-used':     '该激活码已被使用，一码只能绑定一台设备',
    'code-mismatch': '该激活码不属于本机，请核对设备编号',
    'locked':        '尝试次数过多，本机已锁定 15 分钟',
    'network':       '网络异常，请检查网络后重试',
  };

  function actErr(code){
    var tpl = ACT_MSG[code] || '激活失败，请重试';
    return tpl.replace('{left}', String(Math.max(0, 5 - ACT_TRY.n)));
  }

  function isOffline(){
    return (typeof navigator !== 'undefined' && navigator.onLine === false)
      || window.__OFFLINE__ === true;
  }

  function doActivate(){
    var input = $vis('#actCode');
    var v = input ? input.value.trim() : '';

    if (!v) { setMsg('请输入激活码', 'bad'); return; }
    if (!/^\\d{8}$/.test(v)) { setMsg('激活码为 8 位数字，请核对', 'bad'); return; }

    if (isOffline()) { setMsg(actErr('network') + '（激活需要联网）', 'bad'); return; }
    if (ACT_TRY.n >= 5) { setMsg(actErr('locked'), 'bad'); return; }

    var rec = ACT_SAMPLES[v];
    if (!rec) {
      ACT_TRY.n += 1;
      if (ACT_TRY.n > 5) { setMsg(ACT_MSG['locked'], 'bad'); return; }
      var left = 5 - ACT_TRY.n;
      if (left <= 0) { setMsg(ACT_MSG['locked'], 'bad'); return; }
      setMsg('激活码不正确，还可尝试 ' + left + ' 次', 'bad');
      return;
    }
    if (rec.err) {
      /* 已过期、已使用、不属本机：是码本身无效，不是猜错，不计入尝试次数 */
      setMsg(ACT_MSG[rec.err] || '激活失败', 'bad');
      return;
    }

    ACT_TRY.n = 0;
    setMsg('激活码校验通过', 'ok');
    setTimeout(function(){ go('activate-confirm'); }, 300);
  }

  function confirmBind(){
    /* 绑定成功：本机转为已绑定，进入作业主页 */
    var u = currentUnit();
    u.bound = true;
    toast('已绑定本机，可以开始作业');
    setTimeout(function(){ go('work-home'); }, 900);
  }

  function currentOperator(){
    var u = currentUnit();
    return u.operator || '';
  }

  /* 确认页按待确认设备刷新文案 */
  function syncPending(){
    var d = window.__PENDING_DEV__;
    if (!d) return;
    var el = $vis('#bindDevId');
    if (el) el.textContent = d.id;
  }

  function toast(msg){
    var stage = document.querySelector('.app-stage:not([hidden])');
    var t = stage ? stage.querySelector('.app-toast') : null;
    if (!t) return;
    t.textContent = msg; t.classList.add('is-show');
    setTimeout(function(){ t.classList.remove('is-show'); }, 1400);
  }

  window.__APP_DEVICES__ = TERMINALS;
})();
`;

  const html = `<!doctype html>
<html lang="zh-CN">
<head><meta charset="utf-8"><title>西之月 WMS · APP 端</title>
<style>${CSS}</style></head>
<body>
${blocks.join('\n')}
<script>${ROUTER}</script>
</body></html>`;

  const digest = crypto.createHash('sha256').update(html, 'utf8').digest('hex');
  if (process.argv.includes('--check')) {
    if (!fs.existsSync(OUT)) { console.error('漂移检查失败：产物不存在'); process.exit(1); }
    const cur = crypto.createHash('sha256').update(fs.readFileSync(OUT, 'utf8'), 'utf8').digest('hex');
    if (cur !== digest) { console.error('漂移检查失败\n  当前 ' + cur + '\n  期望 ' + digest); process.exit(1); }
    console.log('check ok   sha256=' + digest);
    return;
  }
  fs.mkdirSync(path.dirname(OUT), { recursive: true });
  fs.writeFileSync(OUT, html, 'utf8');
  console.log('built ' + path.relative(NEXT, OUT));
  console.log('  terminals = ' + terminals.length);
  console.log('  scenes    = ' + blocks.length);
  for (const t of terminals.slice(0, 6)) {
    console.log(`    ${t.unit.id} ${t.unit.terminalId} ${t.unit.currentOperatorName} ` +
      `上架${t.counts.putaway}/移库${t.counts.relocation}/盘点${t.counts.stocktaking}`);
  }
  console.log('  sha256 = ' + digest);
}

main();
