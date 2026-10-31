/**
 * 设备管理页（PC 端）。
 *
 * 数据：shared/data/hardware.json（132 件）+ hardware-classes/models/print-templates
 *
 * 交互模型：
 *   台账（筛选 + 表格）→ 详情抽屉（八段）→ 动作（建档 / 到货接收 / 分配 / 配置 DO / 打印机接入）
 * 所有动作在页面内模拟，改内存状态后局部重绘并提示，不写文件。
 */
import { ListPage, StatusTag } from '../../../shared/ui/l3.mjs';

const esc = (v) => String(v ?? '')
  .replace(/&/g, '&amp;').replace(/</g, '&lt;')
  .replace(/>/g, '&gt;').replace(/"/g, '&quot;');

const n = (v) => Number(v || 0).toLocaleString('zh-CN');
const dash = (v) => (v === '' || v == null ? '—' : esc(v));

const STATUS_TONE = { online: 'ok', offline: 'out', maintenance: 'low', fault: 'out' };
const ASSIGN_TONE = {
  '已分配': 'ok', '待分配': 'low', '未到货': 'default',
  '调拨中': 'info', '已核销': 'out',
};
const LIFE_TONE = { '在用': 'ok', '待接入': 'default', '维修中': 'low', '停用': 'out', '报废': 'out' };

/* 不能与系统直接交互的设备类。判定依据是 hardware-classes.json 的 channel 与 note：
   摄像头走外部视频服务商、称重机走串口/USB 接 PC、固定扫码枪走 USB-HID 接 PC，
   三者数据都来自宿主 PC 或外部服务，系统取不到设备自身状态，管不了。
   打印机保留——网口/WiFi 直连与云打印不依赖 PC，可独立取状态。
   设备档案与单仓硬件台账共用这一份口径，改这里即同步。 */
export const NOT_INTERACTIVE = new Set(['HW-CAMERA', 'HW-SCALE', 'HW-SCANNER']);

/* 生命周期：由归属与运行状态派生，不落库 */
function lifeOf(d) {
  if (d.assignStatus === '已核销') return '报废';
  if (d.assignStatus === '未到货') return '待接入';
  if (d.status === 'maintenance') return '维修中';
  if (d.status === 'offline') return '停用';
  return '在用';
}

/* 打印机接入形态说明 */
const FORM_DESC = {
  'USB接PC': { label: 'USB 接 PC', probe: 'PC 代探，PC 离线记未知', transfer: '随 PC 调拨' },
  '网口直连': { label: '网口直连', probe: 'SNMP / ^HH 直探', transfer: '可独立调拨' },
  '云打印': { label: '云打印', probe: '云平台回传状态', transfer: '可独立调拨' },
};

/* 设备页的视图切换：设备 / 工位设备 / 设备绑定。
   三套内容构建期都已渲染好，这里只切换显隐，不重新取数、不重绘表格。
   每个视图都在自己的状态页签行右侧带一份同样的开关，切走之后仍能看到并点回来；
   状态页签行各自随视图显隐，避免出现「设备的状态页签配着绑定的表格」。 */
const HW_VIEW_JS = `
(function(){
  var cur = 'device';

  function panels(){ return [].slice.call(document.querySelectorAll('[data-view-panel]')); }

  function show(key){
    cur = key;
    /* 每个视图自带一套状态页签与切换开关，整块显隐即可，不用再单独藏页签行 */
    panels().forEach(function(p){
      var on = p.getAttribute('data-view-panel') === key;
      if (on) p.removeAttribute('hidden'); else p.setAttribute('hidden','');
    });
    /* 三个视图各有一份开关，选中态要一起同步 */
    [].slice.call(document.querySelectorAll('[data-view]')).forEach(function(b){
      b.classList.toggle('is-on', b.getAttribute('data-view') === key);
    });
  }

  document.addEventListener('click', function(e){
    var b = e.target.closest && e.target.closest('[data-view]');
    if (!b) return;
    e.preventDefault();
    show(b.getAttribute('data-view'));
  });

  show('device');
})();
`;

/* 视图切换项。三个视图各渲染一份，active 各自标自己那一段。 */
const HW_VIEW_SWITCH = [
  { key: 'device', label: '设备', active: true },
  { key: 'station', label: '工位设备' },
  { key: 'binding', label: '设备绑定' },
  { key: 'code', label: '码管理' },
];

/* ============================ 台账页 ============================ */
export function renderHardwarePage({ devices, classes, models, templates = [], bins = [], bindings = [] }) {
  const cls = new Map(classes.map((c) => [c.code, c]));
  const mdl = new Map(models.map((m) => [m.code, m]));

  const inScope = devices.filter((d) => {
    const c = cls.get(d.classCode) || {};
    if (c.inScope === false) return false;
    if (NOT_INTERACTIVE.has(d.classCode)) return false;
    return true;
  });
  const count = (fn) => inScope.filter(fn).length;

  const deviceView = ListPage({
    statusTabs: [
      { label: '全部', count: inScope.length, active: true },
      { label: '在线', count: count((d) => d.status === 'online') },
      { label: '离线', count: count((d) => d.status === 'offline') },
      { label: '维修', count: count((d) => d.status === 'maintenance') },
      { label: '故障', count: count((d) => d.status === 'fault') },
      { label: '待分配', count: count((d) => d.assignStatus === '待分配') },
    ],
    /* 视图切换：设备（一行一台实物）／工位设备（一行一个工位）／设备绑定（一行一条账号与手机的关系）。
       三者粒度不同，不能合成一张表，故做并列视图。 */
    viewSwitch: HW_VIEW_SWITCH,
    filters: [
      { label: '设备编号', type: 'text', placeholder: '编号 / 序列号 / IMEI / 位置' },
      { label: '设备类', type: 'select', options: ['全部', ...classes.filter((c) => c.inScope && !NOT_INTERACTIVE.has(c.code)).map((c) => c.name)] },
      { label: '接入类型', type: 'select', options: ['全部', '受管设备'] },
      { label: '归属', type: 'select', options: ['全部', '未到货', '待分配', '已分配', '调拨中', '已核销'], extra: true },
      { label: '运行状态', type: 'select', options: ['全部', '在线', '离线', '维修', '故障'], extra: true },
      { label: '当前仓', type: 'select', options: ['全部', ...[...new Set(inScope.map((d) => d.currentWarehouse).filter(Boolean))].sort()], extra: true },
    ],
    toolbar: [
      { label: '新增设备', primary: true, attrs: { 'data-act': 'create' } },
      { label: '导出数据', ghost: true, attrs: { 'data-act': 'export' } },
    ],
    showCount: true,
    unit: '件',
    columns: [
      { key: 'id', label: '设备编号', width: '120px', cls: 'col-strong' },
      { key: 'name', label: '名称', width: '150px' },
      { key: 'classCode', label: '设备类', width: '100px', render: (r) => esc((cls.get(r.classCode) || {}).name || r.classCode) },
      { key: 'form', label: '形态', width: '100px', render: (r) => dash(r.form) },
      { key: 'modelCode', label: '型号', width: '150px', render: (r) => esc((mdl.get(r.modelCode) || {}).name || dash(r.modelCode)) },
      { key: 'accessType', label: '接入类型', width: '100px', render: (r) => dash(r.accessType || '受管设备') },
      { key: 'currentWarehouse', label: '当前仓', width: '100px', render: (r) => dash(r.currentWarehouse) },
      { key: 'location', label: '位置 / 使用人', width: '150px', render: (r) => dash(r.location) },
      { key: 'assignStatus', label: '归属', width: '100px',
        render: (r) => StatusTag(r.assignStatus, ASSIGN_TONE[r.assignStatus] || 'default') },
      { key: 'life', label: '生命周期', width: '100px', render: (r) => {
          const l = lifeOf(r);
          return StatusTag(l, LIFE_TONE[l] || 'default');
        } },
      { key: 'status', label: '运行状态', width: '100px',
        render: (r) => StatusTag(r.statusLabel, STATUS_TONE[r.status] || 'default') },
      { key: 'op', label: '操作', width: '80px', fixed: true,
        render: (r) => `<button class="hw-link" data-open="${esc(r.id)}">详情</button>` },
    ],
    rows: inScope,
    emptyTitle: '暂无设备',
    emptyDesc: '点击上方「新增设备」开始录入。',
  });

  /* 三段视图并列渲染，默认只显示设备视图。
     设备视图的行是实物（一行一台），工位视图的行是工位（一行一工位），
     绑定视图的行是一条账号与手机的关系，粒度不同，不能合成一张表。
     切换由 HW_VIEW_JS 接管。 */
  const stationView = bins.length
    ? renderStationView({ bins, devices, classes })
    : '';

  return `<div class="hw-view" data-view-panel="device">${deviceView}</div>`
    + `<div class="hw-view" data-view-panel="station" hidden>${stationView}</div>`
    + `<div class="hw-view" data-view-panel="binding" hidden>${renderBindingView({ bindings })}</div>`
    + `<div class="hw-view" data-view-panel="code" hidden>${renderCodeView({ devices, bindings })}</div>`
    + `<script>${HW_VIEW_JS}</script>`;
}

/* ---------- 详情数据（内联给前端用） ---------- */
function buildDetailPayload(devices, cls, mdl, templates) {
  const rows = devices.map((d) => {
    const c = cls.get(d.classCode) || {};
    const m = mdl.get(d.modelCode) || {};
    const form = FORM_DESC[d.printerForm] || null;
    return {
      id: d.id,
      name: d.name,
      className: c.name || d.classCode,
      classCodeRaw: d.classCode,
      modelName: m.name || '',
      form: d.form || '',
      accessType: d.accessType || '受管设备',
      assignStatus: d.assignStatus,
      life: lifeOf(d),
      status: d.status,
      statusLabel: d.statusLabel,
      currentWarehouse: d.currentWarehouse || '',
      location: d.location || '',
      sn: d.sn || '',
      imei: d.imei || '',
      mac: d.mac || '',
      assetNo: d.assetNo || '',
      appUuid: d.appUuid || '',
      adapter: d.adapter || '',
      endpoint: d.endpoint || '',
      lastSeen: d.lastSeen || '',
      receivedAt: d.receivedAt || '',
      note: d.note || '',
      qrPayload: d.qrPayload || '',
      /* 打印机专有 */
      printerForm: d.printerForm || '',
      printerFormLabel: form ? form.label : '',
      probe: form ? form.probe : '',
      transferRule: form ? form.transfer : '',
      ip: d.ip || '',
      boundPc: d.boundPc || '',
      workstationId: d.workstationId || '',
      mainboardSn: d.mainboardSn || '',
      attachedPrinters: d.attachedPrinters || [],
      agentVersion: d.agentVersion || '',
      agentStatus: d.agentStatus || '',
      cloudDeviceId: d.cloudDeviceId || '',
      mediaType: d.mediaType || '',
      mediaSize: d.mediaSize || '',
      calibrated: d.calibrated,
      /* 型号限制 */
      maxPrintWidthMm: m.maxPrintWidthMm || '',
      dpi: m.dpi || '',
    };
  });
  return `<script>window.__HW_ROWS__ = ${JSON.stringify(rows)};</script>`;
}

function drawerShell() {
  return `<div class="hw-drawer" id="hwDrawer" hidden>
    <div class="hw-drawer__mask" data-close="drawer"></div>
    <aside class="hw-drawer__panel" role="dialog" aria-label="设备详情">
      <header class="hw-drawer__head">
        <div>
          <b id="hwDrawerTitle">设备详情</b>
          <span id="hwDrawerSub"></span>
        </div>
        <button class="hw-drawer__close" data-close="drawer" aria-label="关闭">×</button>
      </header>
      <div class="hw-drawer__body" id="hwDrawerBody"></div>
      <footer class="hw-drawer__foot" id="hwDrawerFoot"></footer>
    </aside>
  </div>`;
}

function modalShell() {
  return `<div class="hw-modal" id="hwModal" hidden>
    <div class="hw-modal__mask" data-close="modal"></div>
    <div class="hw-modal__panel" role="dialog" aria-label="操作">
      <header class="hw-modal__head"><b id="hwModalTitle">操作</b>
        <button class="hw-drawer__close" data-close="modal" aria-label="关闭">×</button></header>
      <div class="hw-modal__body" id="hwModalBody"></div>
      <footer class="hw-modal__foot">
        <button class="hw-btn" data-close="modal">取消</button>
        <button class="hw-btn hw-btn--primary" id="hwModalOk">确定</button>
      </footer>
    </div>
  </div>`;
}

/* ============================ 交互脚本 ============================ */
const HW_JS = `
(function(){
  var ROWS = window.__HW_ROWS__ || [];
  var byId = {};
  ROWS.forEach(function(r){ byId[r.id] = r; });

  /* 抽屉与弹层挂在 body 末尾，晚于本脚本执行，故一律惰性查找 */
  var $ = function(id){ return document.getElementById(id); };
  var current = null;

  function esc(v){ return String(v==null?'':v); }
  function row(k,v){ return '<div class="hw-kv"><span>'+k+'</span><b>'+esc(v||'—')+'</b></div>'; }
  function seg(title, body){ return '<section class="hw-seg"><h4>'+title+'</h4>'+body+'</section>'; }

  /* ---------- 详情八段 ---------- */
  function renderDetail(r){
    var isPrinter = r.className === '打印机';
    var html = '';

    html += seg('一、档案', row('设备编号', r.id) + row('名称', r.name) + row('设备类', r.className)
      + row('型号', r.modelName) + row('形态', r.form) + row('资产编号', r.assetNo));

    html += seg('二、身份', row('序列号', r.sn)
      + (r.accessType === '自有设备'
          ? row('应用 UUID', r.appUuid) + '<p class="hw-tip">自带设备不采集 IMEI，以账号与应用 UUID 识别。UUID 清除数据即变，只作识别线索。</p>'
          : row('IMEI', r.imei) + row('MAC', r.mac))
      + row('接入类型', r.accessType) + row('二维码', r.qrPayload));

    html += seg('三、接入',
      row('适配器', r.adapter)
      + (r.className === 'PC' ? row('主板序列号', r.mainboardSn) + row('代理版本', r.agentVersion) + row('代理状态', r.agentStatus) : '')
      + row('地址', r.endpoint) + row('最后在线', r.lastSeen));

    /* 分段编号按设备类动态计算，避免重复 */
    const CN = ['', '一', '二', '三', '四', '五', '六', '七', '八', '九'];
    const N = { n: 3 };
    const nxt = () => { N.n += 1; return CN[N.n] || String(N.n); };

    /* PC 的挂载外设：作为配置项展示，不是独立资产 */
    if (r.className === 'PC') {
      var ps = r.attachedPrinters || [];
      var psHtml = ps.length
        ? ps.map(function(p){
            return '<div class="ws-card"><div class="ws-card-head"><b>' + esc(p.name) + '</b>'
              + '<span class="ant-tag wm-tag wm-tag--info">USB</span></div>'
              + '<div class="hw-kv"><span>序列号</span><b>' + esc(p.sn || '—') + '</b></div>'
              + '<div class="hw-kv"><span>当前介质</span><b>' + esc(p.mediaType || '—') + ' · ' + esc(p.mediaSize || '—') + '</b></div>'
              + '<div class="hw-kv"><span>校准</span><b>' + (p.calibrated ? '已校准' : '待校准') + '</b></div>'
              + '</div>';
          }).join('')
        : '<div class="ws-empty">本机未挂打印机</div>';
      html += seg(nxt() + '、挂载打印机（' + ps.length + '）', psHtml
        + '<p class="hw-tip">USB 打印机经本机代理转发打印任务。它不是独立资产，不占资产编号，随本机一起调拨。</p>');
    }

    if (isPrinter) {
      html += seg(nxt() + '、打印机', row('接入形态', r.printerFormLabel) + row('探活方式', r.probe)
        + row('调拨规则', r.transferRule)
        + row('所属工位', r.workstationId)
        + (r.printerForm === 'USB接PC' ? row('所属 PC', r.boundPc) : '')
        + (r.printerForm === '云打印' ? row('云设备号', r.cloudDeviceId) : '')
        + (r.printerForm === '网口直连' ? row('IP 地址', r.ip) : '')
        + row('当前介质', r.mediaType + ' · ' + r.mediaSize)
        + row('最大打印宽度', r.maxPrintWidthMm ? r.maxPrintWidthMm + ' mm' : '—')
        + row('分辨率', r.dpi ? r.dpi + ' dpi' : '—')
        + row('校准状态', r.calibrated ? '已校准' : '待校准'));
    }

    html += seg(nxt() + '、归属',
      row('当前仓', r.currentWarehouse) + row('位置 / 使用人', r.location)
      + row('归属状态', r.assignStatus) + row('到货时间', r.receivedAt));

    html += seg(nxt() + '、生命周期', row('当前状态', r.life) + row('运行状态', r.statusLabel));

    html += seg(nxt() + '、绑定',
      row('执行单元', r.className === 'PDA' || r.form === '手机' ? (r.location ? '随 ' + r.location + ' 登录生成' : '未登录') : '不适用'));

    html += seg(nxt() + '、审计',
      row('建档', r.receivedAt || '—') + row('最近变更', r.lastSeen || '—'));

    if (r.note) html += '<p class="hw-note">' + esc(r.note) + '</p>';
    return html;
  }

  /* ---------- 动作按钮按状态给 ---------- */
  function renderActions(r){
    var btns = [];
    if (r.assignStatus === '未到货') btns.push('<button class="hw-btn hw-btn--primary" data-do="receive">到货接收</button>');
    if (r.assignStatus === '待分配') btns.push('<button class="hw-btn hw-btn--primary" data-do="assign">确认本仓使用</button>');
    if (r.assignStatus === '已分配') {
      if (r.accessType === '受管设备') btns.push('<button class="hw-btn" data-do="do">配置设备所有者</button>');
      else btns.push('<button class="hw-btn" data-do="bind">重新绑定账号</button>');
      btns.push('<button class="hw-btn" data-do="activation">生成激活码</button>');
      btns.push('<button class="hw-btn" data-do="repair">报修</button>');
    }
    if (r.className === '打印机') btns.push('<button class="hw-btn" data-do="media">登记介质</button>');
    btns.push('<button class="hw-btn" data-do="audit">归属历史</button>');
    return btns.join('');
  }

  function openDrawer(id){
    var r = byId[id];
    if (!r) return;
    current = r;
    document.getElementById('hwDrawerTitle').textContent = r.id + ' · ' + r.name;
    document.getElementById('hwDrawerSub').textContent = r.className + ' · ' + r.accessType;
    $('hwDrawerBody').innerHTML = renderDetail(r);
    $('hwDrawerFoot').innerHTML = renderActions(r);
    $('hwDrawer').removeAttribute('hidden');
  }
  function closeDrawer(){ $('hwDrawer').setAttribute('hidden',''); current = null; }

  /* ---------- 弹层 ---------- */
  function openModal(title, body, onOk){
    $('hwModalTitle').textContent = title;
    $('hwModalBody').innerHTML = body;
    $('hwModal').removeAttribute('hidden');
    $('hwModalOk').onclick = function(){
      if (onOk) onOk();
      closeModal();
    };
  }
  function closeModal(){ $('hwModal').setAttribute('hidden',''); }

  /* ---------- 动作处理 ---------- */
  var ACTIONS = {
    receive: function(r){
      openModal('到货接收 · ' + r.id,
        '<p class="hw-tip">扫标签核对序列号与 IMEI，确认后写当前仓与收货记录，状态转待分配。</p>'
        + '<label class="hw-field"><span>序列号</span><input value="'+esc(r.sn)+'" readonly></label>'
        + '<label class="hw-field"><span>核对结果</span><input value="一致" readonly></label>'
        + '<label class="hw-field"><span>外观</span><select><option>完好</option><option>有瑕疵</option></select></label>',
        function(){
          r.assignStatus = '待分配'; r.receivedAt = '2026-10-29 22:00';
          openDrawer(r.id); toast(r.id + ' 已收货，转待分配');
        });
    },
    assign: function(r){
      openModal('确认本仓使用 · ' + r.id,
        '<p class="hw-tip">分配只改归属状态，不改当前仓。目标仓必须等于当前仓，跨仓请走调拨。</p>'
        + '<label class="hw-field"><span>当前仓</span><input value="'+esc(r.currentWarehouse)+'" readonly></label>'
        + '<label class="hw-field"><span>位置 / 使用人</span><input id="hwAssignLoc" placeholder="如 打包台 01 或 张伟"></label>',
        function(){
          var v = document.getElementById('hwAssignLoc');
          r.assignStatus = '已分配';
          if (v && v.value) r.location = v.value;
          openDrawer(r.id); toast(r.id + ' 已分配');
        });
    },
    do: function(r){
      openModal('配置设备所有者 · ' + r.id,
        '<p class="hw-tip">设备所有者（DO）只能在出厂或已恢复出厂的设备上设置。设置后应用可读 IMEI。</p>'
        + '<label class="hw-field"><span>设备状态</span><input value="出厂状态，未激活" readonly></label>'
        + '<label class="hw-field"><span>配置方式</span><select><option>二维码扫码配置</option><option>ADB 命令配置</option></select></label>'
        + '<p class="hw-warn">应用须内置解除设备所有者能力，否则恢复出厂亦无法移除管控。</p>',
        function(){ toast(r.id + ' 已配置为设备所有者，可读 IMEI'); });
    },
    bind: function(r){
      openModal('重新绑定账号 · ' + r.id,
        '<p class="hw-tip">自带设备换机时，新设备登录须二次验证后放行，旧绑定自动作废。</p>'
        + '<label class="hw-field"><span>账号</span><input value="'+esc(r.location)+'" readonly></label>'
        + '<label class="hw-field"><span>二次验证</span><select><option>短信验证码</option><option>人脸识别</option></select></label>'
        + '<p class="hw-tip">当前应用 UUID：' + esc(r.appUuid) + '</p>',
        function(){ toast(r.id + ' 已重新绑定'); });
    },
    activation: function(r){
      var code = String(Math.floor(10000000 + Math.random() * 89999999));
      openModal('生成激活码 · ' + r.id,
        '<p class="hw-tip">激活码用于把本机登记进系统。作业员在设备 App 上输入此码完成绑定，一机一码，用后作废。</p>'
        + '<label class="hw-field"><span>设备编号</span><input value="'+esc(r.id)+'" readonly></label>'
        + '<label class="hw-field"><span>激活码</span>'
        + '<input id="hwActCode" value="'+code+'" readonly '
        + 'style="font-family:ui-monospace,monospace;font-size:16px;letter-spacing:2px;text-align:center"></label>'
        + '<label class="hw-field"><span>有效期</span><select><option>24 小时</option><option>7 天</option></select></label>'
        + '<p class="hw-warn">激活码只显示一次。请当面告知作业员或通过内部渠道发送，不要贴在设备上。</p>',
        function(){
          toast(r.id + ' 激活码已生成，请告知作业员');
        });
    },
    repair: function(r){
      openModal('报修 · ' + r.id,
        '<label class="hw-field"><span>故障现象</span><textarea placeholder="如 屏幕碎裂、无法开机"></textarea></label>'
        + '<label class="hw-field"><span>优先级</span><select><option>普通</option><option>紧急</option></select></label>'
        + '<p class="hw-tip">提交后设备转维修中，有作业中任务时须先改派。</p>',
        function(){ r.life = '维修中'; r.statusLabel = '维护中'; r.status = 'maintenance'; openDrawer(r.id); toast(r.id + ' 已报修，转维修中'); });
    },
    media: function(r){
      /* 校验依据：模板宽度 ≤ 打印机最大打印宽度；DPI 须一致；介质与用途匹配（FR-HW-51） */
      var maxW = Number(r.maxPrintWidthMm) || 0;
      var dpi  = Number(r.dpi) || 0;

      var SIZE = [
        { v: '100×150', w: 100, h: 150, use: '面单', media: '热敏' },
        { v: '100×100', w: 100, h: 100, use: '面单', media: '热敏' },
        { v: '60×40',   w: 60,  h: 40,  use: '标签', media: '热转印' },
        { v: '40×30',   w: 40,  h: 30,  use: '标签', media: '热转印' },
        { v: '50×30',   w: 50,  h: 30,  use: '标签', media: '热敏' },
      ];

      if (!maxW) {
        openModal('无法登记介质 · ' + r.id,
          '<p class="hw-warn">该设备未关联型号，读不到最大打印宽度与分辨率，无法校验标签尺寸。</p>'
          + '<p class="hw-tip">请先在「设备档案 › 型号」补齐型号，再回来登记介质。</p>',
          null);
        return;
      }

      /* 只列出不超宽的尺寸 */
      var fit = SIZE.filter(function(s){ return s.w <= maxW; });
      var unfit = SIZE.filter(function(s){ return s.w > maxW; });

      var opts = fit.map(function(s){
        return '<option value="' + s.v + '">' + s.v + ' mm（' + s.use + ' · ' + s.media + '）</option>';
      }).join('');

      var blocked = unfit.length
        ? '<p class="hw-warn">以下尺寸超出本机最大打印宽度 ' + maxW + ' mm，已不可选：'
          + unfit.map(function(s){ return s.v; }).join('、') + '</p>'
        : '';

      openModal('登记介质 · ' + r.id,
        '<div class="hw-card">'
        + '<div class="hw-kv"><span>最大打印宽度</span><b>' + maxW + ' mm</b></div>'
        + '<div class="hw-kv"><span>分辨率</span><b>' + dpi + ' dpi</b></div>'
        + '</div>'
        + '<label class="hw-field"><span>介质类型</span>'
        + '<select id="hwMediaType"><option>热转印</option><option>热敏</option></select></label>'
        + '<label class="hw-field"><span>标签尺寸（仅列本机可打）</span>'
        + '<select id="hwMediaSize">' + opts + '</select></label>'
        + blocked
        + '<p class="hw-warn">换尺寸须在打印机上重新校准间隙或黑标传感器。一台机同一时刻只装一种介质。</p>',
        function(){
          var t = document.getElementById('hwMediaType'), s = document.getElementById('hwMediaSize');
          if (!t || !s) return;
          var picked = SIZE.filter(function(x){ return x.v === s.value; })[0];
          if (!picked) { toast('未选择尺寸'); return; }
          if (picked.w > maxW) { toast('尺寸超过本机最大宽度，已拒绝'); return; }
          r.mediaType = t.value;
          r.mediaSize = s.value;
          r.calibrated = true;
          openDrawer(r.id);
          var warn = (t.value !== picked.media) ? '（提示：该尺寸常用' + picked.media + '）' : '';
          toast(r.id + ' 已登记 ' + s.value + ' · ' + t.value + warn);
        });
    },
    audit: function(r){
      openModal('归属历史 · ' + r.id,
        '<table class="hw-audit"><thead><tr><th>时间</th><th>动作</th><th>仓库</th><th>操作人</th></tr></thead><tbody>'
        + '<tr><td>2026-10-20 09:00</td><td>建档</td><td>—</td><td>陈明</td></tr>'
        + (r.receivedAt ? '<tr><td>'+esc(r.receivedAt)+'</td><td>到货接收</td><td>'+esc(r.currentWarehouse)+'</td><td>仓管</td></tr>' : '')
        + (r.assignStatus==='已分配' ? '<tr><td>2026-10-21 10:00</td><td>确认本仓使用</td><td>'+esc(r.currentWarehouse)+'</td><td>陈明</td></tr>' : '')
        + '</tbody></table>',
        null);
    }
  };

  /* ---------- 新增设备 ---------- */
  function openCreate(){
    openModal('新增设备',
      '<label class="hw-field"><span>设备类</span><select id="hwNewClass">'
      + '<option value="HW-PDA">作业终端（手机 / PDA）</option>'
      + '<option value="HW-PRINTER">打印机</option></select></label>'
      + '<label class="hw-field"><span>型号</span><select id="hwNewModel">'
      + '<option value="PDA-DT40">DT40 手持终端</option>'
      + '<option value="AND-Redmi13">Redmi 13C 安卓手机</option>'
      + '<option value="AND-A15">Galaxy A15 安卓手机</option>'
      + '<option value="PRT-ZD421N">ZD421 网络标签打印机</option>'
      + '<option value="PRT-TSC244">TSC TE244 条码打印机</option></select></label>'
      + '<label class="hw-field"><span>序列号</span><input id="hwNewSn" placeholder="扫原厂条码"></label>'
      + '<label class="hw-field"><span>IMEI（终端）</span><input id="hwNewImei" placeholder="扫原厂条码"></label>'
      + '<label class="hw-field"><span>接入类型</span><select id="hwNewAccess">'
      + '<option>受管设备</option><option>自有设备</option></select></label>'
      + '<p class="hw-tip">建档只解决身份，不填仓。状态为未到货，生成设备编号与资产编号，打印二维码标签。</p>',
      function(){
        var sn = document.getElementById('hwNewSn');
        if (!sn || !sn.value) { toast('序列号必填'); return; }
        toast('已生成设备编号 AS-000133，状态未到货，标签已排队打印');
      });
  }

  /* ---------- 激活码管理 ---------- */
  var ACT_CODES = {};   /* 设备编号 → { code, expire, used } */

  function genCode(){
    return String(Math.floor(10000000 + Math.random() * 89999999));
  }

  function openActivationList(){
    var targets = ROWS.filter(function(r){ return r.assignStatus === '已分配'; });
    var rowsHtml = targets.slice(0, 60).map(function(r){
      var a = ACT_CODES[r.id];
      var state = a ? '<span class="hw-code">' + a.code + '</span>' : '<span class="hw-muted">未生成</span>';
      return '<tr><td class="mono">' + r.id + '</td><td>' + r.name + '</td>'
        + '<td>' + (r.currentWarehouse || '—') + '</td>'
        + '<td>' + state + '</td>'
        + '<td><button class="hw-link" data-gen="' + r.id + '">' + (a ? '重置' : '生成') + '</button></td></tr>';
    }).join('');

    $('hwModalTitle').textContent = '激活码管理 · 已分配设备 ' + targets.length + ' 台';
    $('hwModalBody').innerHTML =
        '<p class="hw-tip">激活码用于 App 端登记设备。一机一码，用后作废，只显示一次。'
      + '发给作业员时通过内部渠道，不要贴在设备上。</p>'
      + '<div class="hw-code-wrap"><table class="hw-code-table">'
      + '<thead><tr><th>设备编号</th><th>名称</th><th>当前仓</th><th>激活码</th><th>操作</th></tr></thead>'
      + '<tbody>' + rowsHtml + '</tbody></table></div>';
    $('hwModal').removeAttribute('hidden');
    $('hwModalOk').textContent = '关闭';
    $('hwModalOk').onclick = function(){
      $('hwModal').setAttribute('hidden','');
      $('hwModalOk').textContent = '确定';
    };
  }

  /* 生成 / 重置 */
  document.addEventListener('click', function(e){
    var g = e.target.closest('[data-gen]');
    if (!g) return;
    var id = g.getAttribute('data-gen');
    ACT_CODES[id] = { code: genCode(), expire: '24h', used: false };
    openActivationList();
    toast(id + ' 激活码 ' + ACT_CODES[id].code + ' 已生成');
  });

  /* ---------- 发起调拨 ---------- */
  function openNewTransfer(){
    var opts = ROWS.filter(function(r){ return r.assignStatus === '已分配'; })
      .slice(0, 40)
      .map(function(r){ return '<option value="'+r.id+'">'+r.id+' · '+r.name+'</option>'; }).join('');
    openModal('发起调拨',
      '<label class="hw-field"><span>调出仓</span><select id="hwFrom"><option>华东仓</option><option>华南仓</option><option>华北仓</option></select></label>'
      + '<label class="hw-field"><span>调入仓</span><select id="hwTo"><option>华南仓</option><option>华北仓</option><option>华东仓</option></select></label>'
      + '<label class="hw-field"><span>设备（可多选）</span><select id="hwDev" multiple size="6">'+opts+'</select></label>'
      + '<p class="hw-tip">一单多设备，行级状态独立推进。发起时逐行校验归属、在办任务、执行单元与未结维护单。</p>'
      + '<p class="hw-warn">USB 形态打印机随其 PC 同行；网口与云形态可独立调拨。</p>',
      function(){
        var sel = document.getElementById('hwDev');
        var picked = sel ? [].slice.call(sel.selectedOptions).length : 0;
        if (!picked) { toast('至少选一台设备'); return; }
        toast('已发起调拨，' + picked + ' 台设备待调出');
      });
  }

  /* ---------- 事件 ---------- */
  document.addEventListener('click', function(e){
    var openBtn = e.target.closest('[data-open]');
    if (openBtn) { openDrawer(openBtn.getAttribute('data-open')); return; }

    var closeBtn = e.target.closest('[data-close]');
    if (closeBtn) {
      if (closeBtn.getAttribute('data-close') === 'drawer') closeDrawer(); else closeModal();
      return;
    }

    var actBtn = e.target.closest('[data-act]');
    if (actBtn) {
      var act = actBtn.getAttribute('data-act');
      if (act === 'create') { openCreate(); return; }
      if (act === 'new-transfer') { openNewTransfer(); return; }
      if (act === 'activation-list') { openActivationList(); return; }
      if (act === 'export') { toast('导出为原型示意，真实系统按当前筛选条件导出'); return; }
    }

    var doBtn = e.target.closest('[data-do]');
    if (doBtn && current) {
      var fn = ACTIONS[doBtn.getAttribute('data-do')];
      if (fn) fn(current);
    }
  });

  /* ---------- 筛选 ---------- */
  function applyFilter(){
    var kw = (document.getElementById('hwKw')||{}).value || '';
    var kc = (document.getElementById('hwClass')||{}).value || '';
    var ka = (document.getElementById('hwAccess')||{}).value || '';
    var ks = (document.getElementById('hwAssign')||{}).value || '';
    kw = kw.trim().toLowerCase();

    /* 只在本页的设备表格内筛选 */
    var page = document.querySelector('.page:not([hidden])');
    if (!page) return;
    var table = page.querySelector('.wm-base-table__table tbody');
    if (!table) return;

    var trs = [].slice.call(table.querySelectorAll('tr'));
    var shown = 0;

    trs.forEach(function(tr){
      var btn = tr.querySelector('[data-open]');
      if (!btn) return;
      var r = byId[btn.getAttribute('data-open')];
      if (!r) { tr.hidden = true; return; }
      var ok = true;
      if (kw) {
        var hay = [r.id, r.sn, r.imei, r.location, r.name, r.appUuid].join(' ').toLowerCase();
        ok = hay.indexOf(kw) >= 0;
      }
      if (ok && kc) ok = r.classCodeRaw === kc;
      if (ok && ka) ok = r.accessType === ka;
      if (ok && ks) ok = r.assignStatus === ks;
      tr.hidden = !ok;
      if (ok) shown++;
    });

    var c = document.getElementById('hwCount');
    if (c) c.textContent = '共 ' + shown + ' 件';
  }
  ['hwKw','hwClass','hwAccess','hwAssign'].forEach(function(id){
    var el = document.getElementById(id);
    if (el) { el.addEventListener('input', applyFilter); el.addEventListener('change', applyFilter); }
  });

  /* ---------- 提示 ---------- */
  var FREE_PRINTERS = window.__FREE_PRINTERS__ || [];
  var FREE_PCS = window.__FREE_PCS__ || [];

  /* 通用选择弹层 */
  function pick(title, items, onPick){
    var opts = items.map(function(it){
      return '<option value="' + it.id + '">' + it.label + '</option>';
    }).join('');
    $('hwModalTitle').textContent = title;
    $('hwModalBody').innerHTML =
        '<label class="hw-field"><span>选择设备</span><select id="wsPickSel">' + opts + '</select></label>'
      + '<p class="hw-tip">只列出本仓未挂载的设备。挂载后需在设备详情补登记介质。</p>';
    $('hwModal').removeAttribute('hidden');
    $('hwModalOk').onclick = function(){
      var sel = $('wsPickSel');
      if (sel && sel.value) onPick(sel.value);
      $('hwModal').setAttribute('hidden','');
    };
  }

  function toast(msg){
    var t = document.getElementById('hwToast');
    if (!t) {
      t = document.createElement('div');
      t.id = 'hwToast'; t.className = 'hw-toast';
      document.body.appendChild(t);
    }
    t.textContent = msg;
    t.classList.add('is-show');
    clearTimeout(t._timer);
    t._timer = setTimeout(function(){ t.classList.remove('is-show'); }, 2000);
  }

  window.__HW_OPEN__ = openDrawer;
})();
`;

export const HW_CSS = `
/* 工具条 */
.hw-btn{
  height:32px;padding:0 14px;border:1px solid var(--wds-color-border-secondary);
  border-radius:var(--wds-size-radius-sm);background:var(--wds-color-background-container);
  color:var(--wds-color-text-primary);font:inherit;font-size:var(--wds-size-font-sm);cursor:pointer;
}
.hw-btn:hover{border-color:var(--wds-color-link);color:var(--wds-color-link)}
.hw-btn--primary{background:var(--wds-color-link);border-color:var(--wds-color-link);color:var(--wds-color-text-inverse)}
.hw-btn--primary:hover{opacity:.9;color:var(--wds-color-text-inverse)}
.hw-link{border:0;background:none;color:var(--wds-color-link);cursor:pointer;font:inherit;padding:0}

/* 筛选条 */
.hw-filter{
  display:flex;align-items:center;gap:var(--wds-layout-gap);flex-wrap:wrap;
  padding:var(--wds-layout-gap) 0;
}
.hw-filter__input{
  flex:1;min-width:220px;height:32px;padding:0 10px;
  border:1px solid var(--wds-color-border-secondary);border-radius:var(--wds-size-radius-sm);
  font:inherit;font-size:var(--wds-size-font-sm);
}
.hw-filter__sel{
  height:32px;padding:0 8px;border:1px solid var(--wds-color-border-secondary);
  border-radius:var(--wds-size-radius-sm);background:var(--wds-color-background-container);
  font:inherit;font-size:var(--wds-size-font-sm);
}
.hw-filter__count{color:var(--wds-color-text-tertiary);font-size:var(--wds-size-font-xs);margin-left:auto}

/* 详情抽屉 */
.hw-drawer{position:fixed;inset:0;z-index:900}
.hw-drawer[hidden],.hw-modal[hidden]{display:none!important}
.hw-drawer__mask{position:absolute;inset:0;background:var(--wds-color-mask,rgba(0,0,0,.45))}
.hw-drawer__panel{
  position:absolute;top:0;right:0;bottom:0;width:480px;max-width:92vw;
  display:flex;flex-direction:column;background:var(--wds-color-background-container);
  box-shadow:var(--wds-size-shadow-lg,0 8px 24px rgba(16,24,40,.16));
}
.hw-drawer__head{
  display:flex;align-items:flex-start;justify-content:space-between;gap:8px;
  padding:16px;border-bottom:1px solid var(--wds-color-border-secondary);
}
.hw-drawer__head b{display:block;font-size:var(--wds-size-font-lg)}
.hw-drawer__head span{color:var(--wds-color-text-tertiary);font-size:var(--wds-size-font-xs)}
.hw-drawer__close{border:0;background:none;font-size:20px;line-height:1;cursor:pointer;color:var(--wds-color-text-secondary)}
.hw-drawer__body{flex:1;overflow-y:auto;padding:0 16px 16px}
.hw-drawer__foot{
  display:flex;gap:8px;flex-wrap:wrap;justify-content:flex-end;
  padding:12px 16px;border-top:1px solid var(--wds-color-border-secondary);
}

/* 分段 */
.hw-seg{padding:12px 0;border-bottom:1px solid var(--wds-color-border-secondary)}
.hw-seg:last-of-type{border-bottom:0}
.hw-seg h4{margin:0 0 8px;font-size:var(--wds-size-font-sm);color:var(--wds-color-text-secondary);font-weight:600}
.hw-kv{display:flex;justify-content:space-between;gap:12px;padding:3px 0;font-size:var(--wds-size-font-sm)}
.hw-kv span{color:var(--wds-color-text-tertiary);flex:none}
.hw-kv b{font-weight:500;text-align:right;word-break:break-all}
.hw-tip{margin:8px 0 0;padding:8px 10px;background:var(--wds-color-background-subtle);
  border-radius:var(--wds-size-radius-sm);color:var(--wds-color-text-secondary);font-size:var(--wds-size-font-xs);line-height:1.6}
.hw-warn{margin:8px 0 0;padding:8px 10px;
  background:var(--wds-component-tag-warning-background);
  color:var(--wds-component-tag-warning-text);
  border-radius:var(--wds-size-radius-sm);font-size:var(--wds-size-font-xs);line-height:1.6}
.hw-note{margin:12px 0 0;color:var(--wds-color-text-tertiary);font-size:var(--wds-size-font-xs);line-height:1.6}

/* 弹层 */
.hw-modal{position:fixed;inset:0;z-index:950;display:grid;place-items:center}
.hw-modal__mask{position:absolute;inset:0;background:var(--wds-color-mask,rgba(0,0,0,.45))}
.hw-modal__panel{
  position:relative;width:420px;max-width:92vw;max-height:84vh;display:flex;flex-direction:column;
  background:var(--wds-color-background-container);border-radius:var(--wds-size-radius-md);
  box-shadow:var(--wds-size-shadow-lg,0 8px 24px rgba(16,24,40,.16));
}
.hw-modal__head{display:flex;align-items:center;justify-content:space-between;padding:14px 16px;
  border-bottom:1px solid var(--wds-color-border-secondary)}
.hw-modal__body{padding:16px;overflow-y:auto}
.hw-modal__foot{display:flex;gap:8px;justify-content:flex-end;padding:12px 16px;
  border-top:1px solid var(--wds-color-border-secondary)}
.hw-field{display:flex;flex-direction:column;gap:4px;margin-bottom:12px}
.hw-field span{font-size:var(--wds-size-font-xs);color:var(--wds-color-text-secondary)}
.hw-field input,.hw-field select,.hw-field textarea{
  height:32px;padding:0 10px;border:1px solid var(--wds-color-border-secondary);
  border-radius:var(--wds-size-radius-sm);font:inherit;font-size:var(--wds-size-font-sm);
}
.hw-field textarea{height:64px;padding:8px 10px;resize:vertical}
.hw-audit{width:100%;border-collapse:collapse;font-size:var(--wds-size-font-xs)}
.hw-audit th,.hw-audit td{padding:6px 8px;text-align:left;border-bottom:1px solid var(--wds-color-border-secondary)}

/* 提示 */
.hw-muted{color:var(--wds-color-text-tertiary)}
.hw-code{
  font-family:ui-monospace,Menlo,monospace;font-size:13px;font-weight:600;
  letter-spacing:1px;color:var(--wds-color-primary);
  background:var(--wds-color-primary-lighter,#f3efff);
  padding:2px 8px;border-radius:var(--wds-size-radius-sm);
}
.hw-code-wrap{max-height:420px;overflow:auto;margin-top:8px}
.hw-code-table{width:100%;border-collapse:collapse;font-size:var(--wds-size-font-xs)}
.hw-code-table th,.hw-code-table td{
  padding:7px 8px;text-align:left;white-space:nowrap;
  border-bottom:1px solid var(--wds-color-border-secondary);
}
.hw-code-table th{
  position:sticky;top:0;background:var(--wds-color-background-subtle);
  font-weight:600;color:var(--wds-color-text-secondary);
}
.hw-toast{
  position:fixed;left:50%;bottom:48px;transform:translateX(-50%) translateY(8px);
  padding:10px 18px;border-radius:var(--wds-size-radius-lg);
  background:var(--wds-color-background-inverse,rgba(0,0,0,.82));
  color:var(--wds-color-text-inverse);
  font-size:var(--wds-size-font-sm);opacity:0;pointer-events:none;transition:.18s;z-index:999;
}
.hw-toast.is-show{opacity:1;transform:translateX(-50%) translateY(0)}
`;

/* ============================ 设备调拨 ============================ */
export function renderHardwareTransferPage({ transfers, devices }) {
  const dev = new Map(devices.map((d) => [d.id, d]));
  const sum = (s) => transfers.filter((t) => t.status === s).length;

  const rows = [];
  for (const t of transfers) {
    for (const line of t.lines || []) {
      const d = dev.get(line.hardwareId) || {};
      rows.push({
        id: t.id, fromWarehouse: t.fromWarehouse, toWarehouse: t.toWarehouse,
        initiator: t.initiator, createdAt: t.createdAt, status: t.status,
        hardwareId: line.hardwareId, hardwareName: d.name || '—',
        className: d.classCode === 'HW-PRINTER' ? '打印机' : (d.classCode === 'HW-PDA' ? '作业终端' : '—'),
        lineStatus: line.lineStatus,
      });
    }
  }

  const TONE = { '待调出': 'low', '待接收': 'info', '异常': 'out', '已完成': 'ok', '已取消': 'default' };

  return ListPage({
    statusTabs: [
      { label: '全部', count: rows.length, active: true },
      { label: '待调出', count: rows.filter((r) => r.status === '待调出').length },
      { label: '待接收', count: rows.filter((r) => r.status === '待接收').length },
      { label: '异常', count: rows.filter((r) => r.status === '异常').length },
      { label: '已完成', count: rows.filter((r) => r.status === '已完成').length },
    ],
    filters: [
      { label: '调拨单号', type: 'text', placeholder: '请输入调拨单号' },
      { label: '调出仓', type: 'select', options: ['全部', ...[...new Set(rows.map((r) => r.fromWarehouse).filter(Boolean))].sort()] },
      { label: '调入仓', type: 'select', options: ['全部', ...[...new Set(rows.map((r) => r.toWarehouse).filter(Boolean))].sort()] },
      { label: '设备编号', type: 'text', placeholder: '请输入设备编号', extra: true },
      { label: '单据状态', type: 'select', options: ['全部', '待调出', '待接收', '异常', '已完成', '已取消'], extra: true },
    ],
    toolbar: [
      { label: '发起调拨（待开放）', primary: true, disabled: true },
      { label: '导出数据', ghost: true },
    ],
    showCount: true,
    unit: '行',
    columns: [
      { key: 'id', label: '调拨单号', width: '130px', cls: 'col-strong' },
      { key: 'fromWarehouse', label: '调出仓', width: '100px' },
      { key: 'toWarehouse', label: '调入仓', width: '100px' },
      { key: 'hardwareId', label: '设备编号', width: '120px' },
      { key: 'hardwareName', label: '设备名称', width: '150px' },
      { key: 'className', label: '设备类', width: '100px' },
      { key: 'status', label: '单据状态', width: '100px', render: (r) => StatusTag(r.status, TONE[r.status] || 'default') },
      { key: 'lineStatus', label: '行状态', width: '100px', render: (r) => StatusTag(r.lineStatus, TONE[r.lineStatus] || 'default') },
      { key: 'initiator', label: '发起人', width: '90px' },
      { key: 'createdAt', label: '发起时间', width: '150px' },
    ],
    rows,
    emptyTitle: '暂无调拨单',
    emptyDesc: '点击上方「发起调拨」新建。',
  });
}

/* 全局挂载：数据、抽屉与弹层、脚本统一挂到 body，
   避免被 .page 的 display:none 连带隐藏，也让调拨页共用同一套数据。 */
export function hardwareGlobals({ devices, classes, models, templates = [] }) {
  const inScope = devices.filter((d) => (classes.find((c) => c.code === d.classCode) || {}).inScope !== false);
  const cls = new Map(classes.map((c) => [c.code, c]));
  const mdl = new Map(models.map((m) => [m.code, m]));
  return buildDetailPayload(inScope, cls, mdl, templates)
    + drawerShell() + modalShell()
    + `<script>${HW_JS}</script>`;
}

/* ============================ 工位设备视图 ============================ */
/**
 * 以工位为中心看设备挂载关系。
 *
 * 模型：工位就是库位。工位上有一台工位主机（PC），PC 上挂 USB 外设；
 *       网络与云打印机直接挂工位，不经 PC。
 * 人不在这个页面上绑定，人登录时记录所在工位。
 */
export function renderStationView({ bins, devices, classes, active = false }) {
  const ws = bins.filter((b) => b.binType === '工位');
  const devByWs = new Map();
  for (const d of devices) {
    const id = d.workstationId;
    if (!id) continue;
    if (!devByWs.has(id)) devByWs.set(id, []);
    devByWs.get(id).push(d);
  }
  const cls = new Map(classes.map((c) => [c.code, c]));

  const totalDev = ws.reduce((n, w) => n + (devByWs.get(w.id) || []).length, 0);
  const hasPc = ws.filter((w) => (devByWs.get(w.id) || []).some((d) => d.classCode === 'HW-PC')).length;
  const noPc = ws.length - hasPc;

  const rows = ws.map((w) => {
    const list = devByWs.get(w.id) || [];
    const pc = list.find((d) => d.classCode === 'HW-PC');
    const printers = list.filter((d) => d.classCode === 'HW-PRINTER');
    /* USB 打印机是 PC 的配置项，不再是独立设备 */
    const usbCount = list.reduce((k, d) => k + ((d.attachedPrinters || []).length), 0);
    const net = printers.filter((p) => p.printerForm !== 'USB接PC');
    return {
      id: w.id,
      warehouse: w.warehouse,
      wsType: w.wsType || '—',
      pcId: pc ? pc.id : '',
      pcSn: pc ? (pc.mainboardSn || pc.sn) : '',
      usbCount: usbCount,
      netCount: net.length,
      total: list.length + usbCount,
      status: w.statusLabel || '—',
      devIds: list.map((d) => d.id).join('、'),
    };
  });

  const list = ListPage({
    statusTabs: [
      { label: '全部', count: rows.length, active: true },
      { label: '已配主机', count: hasPc },
      { label: '缺主机', count: noPc },
    ],
    /* 工位视图自带一份切换开关，切走之后仍能点回设备或绑定 */
    viewSwitch: HW_VIEW_SWITCH.map((v) => ({ ...v, active: v.key === 'station' })),
    filters: [
      { label: '工位编号', type: 'text', placeholder: '请输入工位编号' },
      { label: '仓库', type: 'select', options: ['全部', ...[...new Set(rows.map((r) => r.warehouse).filter(Boolean))].sort()] },
      { label: '工位类型', type: 'select', options: ['全部', ...[...new Set(rows.map((r) => r.wsType).filter((v) => v && v !== '—'))].sort()] },
      { label: '工位主机', type: 'select', options: ['全部', '已配置', '未配置'], extra: true },
    ],
    toolbar: [
      { label: '设备挂载' },
      { label: '导出数据', ghost: true },
    ],
    showCount: true,
    unit: '个',
    columns: [
      { key: 'id', label: '工位编号', width: '130px', cls: 'col-strong' },
      { key: 'warehouse', label: '仓库', width: '100px' },
      { key: 'wsType', label: '工位类型', width: '110px' },
      { key: 'pcId', label: '工位主机', width: '130px',
        render: (r) => r.pcId ? StatusTag(r.pcId, 'enabled') : StatusTag('未配置', 'disabled') },
      { key: 'pcSn', label: '主板序列号', width: '150px', render: (r) => (r.pcSn ? esc(r.pcSn) : '—') },
      { key: 'usbCount', label: 'USB 打印机', width: '110px', align: 'right', render: (r) => n(r.usbCount) },
      { key: 'netCount', label: '网络 / 云打印机', width: '130px', align: 'right', render: (r) => n(r.netCount) },
      { key: 'total', label: '挂载合计', width: '100px', align: 'right', render: (r) => n(r.total) },
      { key: 'status', label: '工位状态', width: '100px',
        render: (r) => StatusTag(r.status, r.status === '已启用' ? 'enabled' : 'disabled') },
      { key: 'op', label: '操作', width: '100px', fixed: true,
        render: (r) => `<button class="hw-link" data-ws-open="${esc(r.id)}">设备挂载</button>` },
    ],
    rows,
    emptyTitle: '暂无工位',
    emptyDesc: '工位随仓库配置生成。',
  });

  const payload = `<script>window.__WS_ROWS__ = ${JSON.stringify(
    ws.map((w) => {
      const list = devByWs.get(w.id) || [];
      return {
        id: w.id, warehouse: w.warehouse, wsType: w.wsType || '',
        status: w.statusLabel || '',
        devices: list.map((d) => ({
          id: d.id, name: d.name,
          className: (cls.get(d.classCode) || {}).name || d.classCode,
          classCode: d.classCode,
          printerForm: d.printerForm || '',
          printFormLabel: (FORM_DESC[d.printerForm] || {}).label || '',
          boundPc: d.boundPc || '',
          mediaType: d.mediaType || '',
          mediaSize: d.mediaSize || '',
          statusLabel: d.statusLabel || '',
          attachedPrinters: d.attachedPrinters || [],
        })),
      };
    })
  )};</script>`;

  /* 未挂工位的候选：供挂载选择器使用 */
  const freePrinters = devices
    .filter((d) => d.classCode === 'HW-PRINTER' && !d.workstationId && d.assignStatus === '已分配')
    .map((d) => ({
      id: d.id, name: d.name,
      formLabel: (FORM_DESC[d.printerForm] || {}).label || d.printerForm || '',
      warehouse: d.currentWarehouse || d.warehouse || '',
    }));
  const freePcs = devices
    .filter((d) => d.classCode === 'HW-PC' && !d.workstationId && d.assignStatus === '已分配')
    .map((d) => ({
      id: d.id, name: d.name, mainboardSn: d.mainboardSn || d.sn || '',
      warehouse: d.currentWarehouse || d.warehouse || '',
    }));
  const freeData = `<script>window.__FREE_PRINTERS__ = ${JSON.stringify(freePrinters)};`
    + `window.__FREE_PCS__ = ${JSON.stringify(freePcs)};</script>`;

  return list + payload + freeData + `<script>${WS_JS}</script>`;
}

const WS_JS = `
(function(){
  var WS = window.__WS_ROWS__ || [];
  var byId = {};
  WS.forEach(function(w){ byId[w.id] = w; });
  var cur = null;

  function $(id){ return document.getElementById(id); }

  function close(){ $('hwDrawer').setAttribute('hidden',''); cur = null; }

  function open(id){
    var w = byId[id];
    if (!w) return;
    cur = w;

    var pc = w.devices.filter(function(d){ return d.classCode === 'HW-PC'; })[0];
    var printers = w.devices.filter(function(d){ return d.classCode === 'HW-PRINTER'; });

    var pcBlock = pc
      ? '<div class="ws-card"><div class="ws-card-head"><b>' + pc.id + '</b>'
        + '<span class="ant-tag ant-tag-success wm-tag wm-tag--success">工位主机</span></div>'
        + '<div class="hw-kv"><span>名称</span><b>' + pc.name + '</b></div>'
        + '<div class="hw-kv"><span>运行状态</span><b>' + pc.statusLabel + '</b></div>'
        + '<div class="hw-kv"><span>挂载打印机</span><b>' + ((pc.attachedPrinters || []).length || '无') + '</b></div>'
        + ((pc.attachedPrinters || []).map(function(x){
            return '<div class="hw-kv"><span>　└ ' + x.mediaType + ' ' + x.mediaSize + '</span><b>' + x.name + '</b></div>';
          }).join(''))
        + '<div class="ws-actions"><button class="hw-btn" data-ws-act="unbind-pc">解绑此主机</button></div></div>'
      : '<div class="ws-empty">该工位尚未配置工位主机</div>';

    var printerRows = printers.length
      ? printers.map(function(p){
          return '<div class="ws-card"><div class="ws-card-head"><b>' + p.id + '</b>'
            + '<span class="ant-tag wm-tag wm-tag--info">' + (p.printFormLabel || '打印机') + '</span></div>'
            + '<div class="hw-kv"><span>型号</span><b>' + p.name + '</b></div>'
            + (p.printerForm === 'USB接PC' ? '<div class="hw-kv"><span>挂载 PC</span><b>' + (p.boundPc || '未绑定') + '</b></div>' : '')
            + '<div class="hw-kv"><span>当前介质</span><b>' + (p.mediaType || '—') + ' · ' + (p.mediaSize || '—') + '</b></div>'
            + '<div class="ws-actions">'
            + (p.printerForm === 'USB接PC'
                ? '<button class="hw-btn" data-ws-act="rebind-usb" data-dev="' + p.id + '">改挂 PC</button>'
                : '')
            + '<button class="hw-btn" data-ws-act="unbind-printer" data-dev="' + p.id + '">移出工位</button>'
            + '</div></div>';
        }).join('')
      : '<div class="ws-empty">该工位尚未挂载打印机</div>';

    $('hwDrawerTitle').textContent = w.id + ' · ' + w.warehouse;
    $('hwDrawerSub').textContent = (w.wsType || '工位') + ' · ' + w.status;
    $('hwDrawerBody').innerHTML =
        '<section class="hw-seg"><h4>工位</h4>'
      + '<div class="hw-kv"><span>工位编号</span><b>' + w.id + '</b></div>'
      + '<div class="hw-kv"><span>仓库</span><b>' + w.warehouse + '</b></div>'
      + '<div class="hw-kv"><span>工位类型</span><b>' + (w.wsType || '—') + '</b></div>'
      + '<p class="hw-tip">工位即库位。人不在工位上绑定，人登录时记录所在工位。</p>'
      + '</section>'
      + '<section class="hw-seg"><h4>工位主机</h4>' + pcBlock + '</section>'
      + '<section class="hw-seg"><h4>打印机（' + printers.length + '）</h4>' + printerRows + '</section>';

    $('hwDrawerFoot').innerHTML =
        '<button class="hw-btn hw-btn--primary" data-ws-act="add-printer">挂打印机到此工位</button>'
      + '<button class="hw-btn" data-ws-act="bind-pc">配置工位主机</button>';

    $('hwDrawer').removeAttribute('hidden');
  }

  var FREE_PRINTERS = window.__FREE_PRINTERS__ || [];
  var FREE_PCS = window.__FREE_PCS__ || [];

  /* 通用选择弹层 */
  function pick(title, items, onPick){
    var opts = items.map(function(it){
      return '<option value="' + it.id + '">' + it.label + '</option>';
    }).join('');
    $('hwModalTitle').textContent = title;
    $('hwModalBody').innerHTML =
        '<label class="hw-field"><span>选择设备</span><select id="wsPickSel">' + opts + '</select></label>'
      + '<p class="hw-tip">只列出本仓未挂载的设备。挂载后需在设备详情补登记介质。</p>';
    $('hwModal').removeAttribute('hidden');
    $('hwModalOk').onclick = function(){
      var sel = $('wsPickSel');
      if (sel && sel.value) onPick(sel.value);
      $('hwModal').setAttribute('hidden','');
    };
  }

  function toast(msg){
    var t = $('hwToast');
    if (!t) { t = document.createElement('div'); t.id='hwToast'; t.className='hw-toast'; document.body.appendChild(t); }
    t.textContent = msg; t.classList.add('is-show');
    clearTimeout(t._t); t._t = setTimeout(function(){ t.classList.remove('is-show'); }, 2000);
  }

  document.addEventListener('click', function(e){
    var op = e.target.closest('[data-ws-open]');
    if (op) { open(op.getAttribute('data-ws-open')); return; }

    if (e.target.closest('[data-close="drawer"]')) { close(); return; }

    var act = e.target.closest('[data-ws-act]');
    if (!act || !cur) return;
    var k = act.getAttribute('data-ws-act');

    if (k === 'add-printer') {
      var free = FREE_PRINTERS.filter(function(p){ return p.warehouse === cur.warehouse; });
      if (!free.length) { toast('本仓没有未挂载的打印机'); return; }
      pick('挂打印机到 ' + cur.id, free.map(function(p){
        return { id: p.id, label: p.id + ' · ' + p.name + ' · ' + p.formLabel };
      }), function(id){
        toast(id + ' 已挂到 ' + cur.id + '，须登记介质后可用');
        close();
      });
    } else if (k === 'bind-pc') {
      var freePc = FREE_PCS.filter(function(p){ return p.warehouse === cur.warehouse; });
      if (!freePc.length) { toast('本仓没有未挂工位的 PC'); return; }
      pick('为 ' + cur.id + ' 配置工位主机', freePc.map(function(p){
        return { id: p.id, label: p.id + ' · ' + p.name + ' · 主板 ' + p.mainboardSn };
      }), function(id){
        toast(id + ' 已配置为 ' + cur.id + ' 的工位主机');
        close();
      });
    } else if (k === 'unbind-pc') {
      var usb = cur.devices.filter(function(d){ return d.classCode === 'HW-PRINTER' && d.printerForm === 'USB接PC'; });
      if (usb.length) {
        toast('须先把 ' + usb.map(function(x){return x.id;}).join('、') + ' 移出或改挂，才能解绑主机');
        return;
      }
      toast('已解绑工位主机');
      close();
    } else if (k === 'unbind-printer') {
      toast(act.getAttribute('data-dev') + ' 已移出工位，转待分配');
      close();
    } else if (k === 'rebind-usb') {
      var pc = cur.devices.filter(function(d){ return d.classCode === 'HW-PC'; })[0];
      if (!pc) { toast('本工位没有主机，无法挂载 USB 打印机'); return; }
      toast(act.getAttribute('data-dev') + ' 已改挂到 ' + pc.id);
      close();
    }
  });
})();
`;

export const WS_CSS = `
.ws-card{
  padding:10px 12px;border:1px solid var(--wds-color-border-secondary);
  border-radius:var(--wds-size-radius-md);margin-bottom:8px;
}
.ws-card:last-child{margin-bottom:0}
.ws-card-head{display:flex;align-items:center;gap:8px;margin-bottom:6px}
.ws-card-head b{font-size:var(--wds-size-font-sm);font-family:ui-monospace,Menlo,monospace}
.ws-actions{display:flex;gap:8px;margin-top:8px}
.ws-empty{
  padding:14px;text-align:center;color:var(--wds-color-text-tertiary);
  background:var(--wds-color-background-subtle);border-radius:var(--wds-size-radius-md);
  font-size:var(--wds-size-font-xs);
}
`;

/* ============================ 设备绑定（员工自带机） ============================ */
/**
 * 员工自带手机：设备页内的「设备绑定」视图。
 *
 * 关键区别：员工手机不是公司资产，不建档、不占资产编号、不贴二维码。
 * 这里只记录「账号 ↔ 应用 UUID」的绑定关系，供审批与解绑使用。
 * 与设备视图、工位视图片列，不单独占三级页签。
 */
export function renderBindingView({ bindings = [] }) {
  const active = bindings.filter((b) => b.status === '已启用').length;
  const pending = bindings.filter((b) => b.status === '待审批').length;

  const TONE = { '已启用': 'enabled', '待审批': 'pending', '已解绑': 'disabled', '已拒绝': 'rejected' };

  const list = ListPage({
    statusTabs: [
      { label: '全部', count: bindings.length, active: true },
      { label: '已启用', count: active },
      { label: '待审批', count: pending },
      { label: '已解绑', count: bindings.filter((b) => b.status === '已解绑').length },
    ],
    /* 绑定视图自带一份切换开关，切走之后仍能点回设备或工位 */
    viewSwitch: HW_VIEW_SWITCH.map((v) => ({ ...v, active: v.key === 'binding' })),
    filters: [
      { label: '账号或姓名', type: 'text', placeholder: '请输入账号或姓名' },
      { label: '状态', type: 'select', options: ['全部', '已启用', '待审批', '已解绑', '已拒绝'], extra: true },
    ],
    toolbar: [
      { label: '导出数据', ghost: true },
    ],
    showCount: true,
    unit: '条',
    columns: [
      { key: 'id', label: '绑定编号', width: '120px', cls: 'col-strong' },
      { key: 'accountName', label: '账号', width: '160px',
        render: (r) => `${esc(r.accountName)}（${esc(r.accountId)}）` },
      { key: 'deviceName', label: '设备', width: '150px' },
      { key: 'form', label: '形态', width: '100px', render: (r) => dash(r.form) },
      { key: 'os', label: '系统', width: '120px', render: (r) => dash(r.os) },
      { key: 'appUuid', label: '应用 UUID', width: '180px', render: (r) => esc(r.appUuid) },
      { key: 'firstBindAt', label: '首次绑定', width: '150px', render: (r) => dash(r.firstBindAt) },
      { key: 'lastLoginAt', label: '最近登录', width: '150px', render: (r) => dash(r.lastLoginAt) },
      { key: 'status', label: '状态', width: '100px',
        render: (r) => `<span data-bind-status="${esc(r.id)}">${StatusTag(r.status, TONE[r.status] || 'disabled')}</span>` },
      { key: 'op', label: '操作', width: '90px', fixed: true,
        render: (r) => `<button class="hw-link" data-bind-op="${esc(r.id)}">`
          + (r.status === '已启用' ? '解绑' : '通过') + `</button>` },
    ],
    rows: bindings,
    emptyTitle: '暂无绑定',
    emptyDesc: '员工在设备 App 上输入邀请码后出现在这里。',
  });

  const script = `<script>
(function(){
  /* 行内状态与按钮就地更新，不重绘表格；反馈统一用页面已有的 toast */
  function toast(msg){
    var t = document.getElementById('hwToast');
    if (!t) { t = document.createElement('div'); t.id='hwToast'; t.className='hw-toast'; document.body.appendChild(t); }
    t.textContent = msg;
    t.classList.add('is-show');
    clearTimeout(t._t);
    t._t = setTimeout(function(){ t.classList.remove('is-show'); }, 2200);
  }

  function setRow(id, label, tone, opLabel){
    var box = document.querySelector('[data-bind-status="' + id + '"]');
    if (box) box.innerHTML = '<span class="status ' + tone + '">' + label + '</span>';
    var op = document.querySelector('[data-bind-op="' + id + '"]');
    if (op) op.textContent = opLabel;
  }

  document.addEventListener('click', function(e){
    var g = e.target.closest('[data-act="gen-invite"]');
    if (g) {
      var code = 'INV-' + Math.random().toString(36).slice(2, 8).toUpperCase();
      var host = document.querySelector('[data-view-panel="binding"] .hw-invite-host');
      if (!host) return;
      host.innerHTML = '<div class="hw-invite">'
        + '<div class="hw-invite-code">' + code + '</div>'
        + '<div class="hw-invite-meta"><div>有效期 7 天</div><div>已使用 0 / 50 次</div></div></div>';
      toast('邀请码 ' + code + ' 已生成，发给员工即可共享使用');
      return;
    }

    var op = e.target.closest('[data-bind-op]');
    if (!op) return;
    var id = op.getAttribute('data-bind-op');
    if (op.textContent === '解绑') {
      setRow(id, '已解绑', 'disabled', '通过');
      toast(id + ' 已解绑，该手机下次登录需重新邀请');
    } else {
      setRow(id, '已启用', 'enabled', '解绑');
      toast(id + ' 已通过审批，该手机可正常登录');
    }
  });
})();
<\/script>`;

  return `<div class="hw-invite-host"></div>` + list + script;
}

export const BIND_CSS = `
.hw-invite-host:empty{display:none}
.hw-invite{
  display:flex;align-items:center;gap:16px;margin:12px 0;padding:14px 18px;
  border:1px dashed var(--wds-color-primary-border);
  border-radius:var(--wds-size-radius-md);background:var(--wds-color-primary-lighter,#f3efff);
}
.hw-invite-code{
  font-family:ui-monospace,Menlo,monospace;font-size:18px;font-weight:700;
  letter-spacing:2px;color:var(--wds-color-primary);
}
.hw-invite-meta{font-size:var(--wds-size-font-xs);color:var(--wds-color-text-secondary);line-height:1.7}
`;

/* ============================ 码管理（统一一机一码） ============================ */
/**
 * 设备接入码统一管理。
 *
 * 统一口径：一机一码。公司机与员工自带机都走同一套机制，差别只在对象类型。
 * 不再区分「激活码」与「邀请码」——两套码会让作业员不知道该输哪个。
 *
 * 码的生命周期：生成 → 未使用 → 已使用 → 作废。换机或重装须重新生成。
 */
export function renderCodeView({ devices = [], bindings = [] }) {
  /* 设备侧的码对象：只列已分配的终端与 PC */
  const devTargets = devices
    .filter((d) => ['HW-PDA', 'HW-PC'].includes(d.classCode) && d.assignStatus === '已分配')
    .map((d) => ({
      kind: '设备',
      id: d.id,
      name: d.name,
      owner: d.location || '',
      warehouse: d.currentWarehouse || d.warehouse || '',
      code: d.accessCode || '',
      state: d.accessCodeState || '未生成',
      issuedAt: d.accessCodeIssuedAt || '',
    }));

  /* 绑定侧的码对象：员工自带机 */
  const bindTargets = bindings.map((b) => ({
    kind: '自带机',
    id: b.id,
    name: `${b.accountName} · ${b.deviceName}`,
    owner: b.accountName,
    warehouse: '',
    code: b.accessCode || '',
    state: b.accessCodeState || '未生成',
    issuedAt: b.accessCodeIssuedAt || '',
  }));

  const all = [...devTargets, ...bindTargets];
  const cnt = (s) => all.filter((r) => r.state === s).length;

  const header = `<div class="hw-code-head">
    <span class="hw-code-sub">${all.length} 个对象 · 一机一码 · 公司机与自带机同一套机制</span>
    <button class="hw-btn hw-btn--primary" data-code-act="batch" disabled title="原型未实现批量发码">批量生成（待开放）</button>
  </div>`;

  /* 视图内不重复页头统计，用一行摘要代替 */
  const stats = `<div class="hw-code-summary">
    <span>设备 <b>${devTargets.length}</b></span>
    <span>员工自带机 <b>${bindTargets.length}</b></span>
    <span>未使用 <b>${cnt('未使用')}</b></span>
    <span>已使用 <b>${cnt('已使用')}</b></span>
  </div>`;

  const note = `<div class="hw-tip" style="margin:12px 0">
    <b>一机一码</b>：每个对象一个码，用后作废。换机或重装须重新生成，旧码自动失效。
    发给作业员时走内部渠道，不要贴在设备上。
  </div>`;

  const TONE = { '未生成': 'default', '未使用': 'warning', '已使用': 'enabled', '已作废': 'disabled' };

  const table = ListPage({
    pager: false,
    filters: [],
    toolbar: [],
    columns: [
      { key: 'kind', label: '类型', width: '90px',
        render: (r) => StatusTag(r.kind, r.kind === '自带机' ? 'pending' : 'enabled') },
      { key: 'id', label: '对象编号', width: '140px', cls: 'col-strong' },
      { key: 'name', label: '名称', width: '220px' },
      { key: 'warehouse', label: '仓库', width: '100px', render: (r) => esc(r.warehouse || '—') },
      { key: 'code', label: '接入码', width: '140px',
        render: (r) => (r.code ? `<span class="hw-code">${esc(r.code)}</span>` : '<span class="hw-muted">未生成</span>') },
      { key: 'issuedAt', label: '生成时间', width: '150px', render: (r) => esc(r.issuedAt || '—') },
      { key: 'state', label: '状态', width: '100px',
        render: (r) => StatusTag(r.state, TONE[r.state] || 'default') },
      { key: 'op', label: '操作', width: '100px', fixed: true,
        render: (r) => {
          const label = r.state === '未生成' ? '生成' : (r.state === '未使用' ? '重置' : '重新生成');
          return `<button class="hw-link" data-code-gen="${esc(r.id)}" data-code-kind="${esc(r.kind)}">${label}</button>`;
        } },
    ],
    rows: all,
    emptyTitle: '暂无对象',
    emptyDesc: '设备到货分配后或员工绑定后出现在这里。',
  });

  const script = `<script>
(function(){
  var STATE = {};
  function toast(msg){
    var t = document.getElementById('hwToast');
    if (!t) { t = document.createElement('div'); t.id='hwToast'; t.className='hw-toast'; document.body.appendChild(t); }
    t.textContent = msg; t.classList.add('is-show');
    clearTimeout(t._t); t._t = setTimeout(function(){ t.classList.remove('is-show'); }, 2400);
  }
  function gen(){
    return String(Math.floor(10000000 + Math.random() * 89999999));
  }
  document.addEventListener('click', function(e){
    var g = e.target.closest('[data-code-gen]');
    if (g) {
      var id = g.getAttribute('data-code-gen');
      var code = gen();
      var tr = g.closest('tr');
      var cells = tr.querySelectorAll('td');
      cells[4].innerHTML = '<span class="hw-code">' + code + '</span>';
      cells[5].textContent = '2026-10-30 09:40';
      cells[6].innerHTML = '<span class="ant-tag ant-tag-warning wm-tag wm-tag--warning">未使用</span>';
      g.textContent = '重置';
      toast(id + ' 接入码 ' + code + ' 已生成，一机一码，用后作废');
      return;
    }
    if (e.target.closest('[data-code-act="batch"]')) {
      toast('批量为未生成的设备发码（原型示意）');
    }
  });
})();
<\/script>`;

  return header + stats + note + table + script + CODE_CSS;
}

export const CODE_CSS = `
.hw-code-head{
  display:flex;align-items:center;justify-content:space-between;gap:16px;
  padding:12px 0;
}
.hw-code-sub{font-size:var(--wds-size-font-xs);color:var(--wds-color-text-tertiary)}
.hw-code-summary{display:flex;gap:20px;padding:0 0 12px;font-size:var(--wds-size-font-xs);color:var(--wds-color-text-secondary)}
.hw-code-summary b{color:var(--wds-color-text-primary);font-weight:600;margin-left:4px}
.hw-code{
  font-family:ui-monospace,Menlo,monospace;font-size:13px;font-weight:600;
  letter-spacing:1px;color:var(--wds-color-primary);
  background:var(--wds-color-primary-lighter,#f3efff);
  padding:2px 8px;border-radius:var(--wds-size-radius-sm);
}
.hw-muted{color:var(--wds-color-text-tertiary)}
`;
