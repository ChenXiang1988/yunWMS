/**
 * 设备档案（总仓管理 › 设备档案）。
 *
 * 与「单仓配置 › 硬件」是两个页面，不得合并：
 *   设备档案 = 型号档案。按主体共享，管型号、版本、厂商、默认能力；
 *              一个型号对应多台设备，这里不关心某一台设备在哪个仓。
 *   单仓硬件 = 实际设备台账。管某一台设备的归属、位置、运行状态，含员工手机。
 *
 * 列名与列序照搬旧版 extend/hardware-center.js 的 renderModels()：
 *   型号 / 设备类 / 厂商 / 默认能力 / 设备数 / 状态
 * 在其后补「固件版本」一列：同一型号下实际出现过的 firmware 版本，
 * 数据来自 hardware.json，多版本逗号分隔。旧版没有这一列，属于本原型的补充。
 *
 * 过滤口径（三条）：
 *   1. surface === 'out' 的设备类不进（旧版口径，AGV 被排除）；
 *   2. 不能与系统直接交互的设备类不进。摄像头走外部视频服务商、
 *      称重机走串口/USB 接 PC、固定扫码枪走 USB-HID 接 PC，
 *      三者的数据都来自宿主 PC 而非设备自身，系统管不了，故排除。
 *      打印机保留——网口/WiFi 直连与云打印不依赖 PC，可独立取状态。
 *   3. deviceForm === '手机' 的型号不进（员工手机归单仓硬件页）。
 *
 * 行内「版本明细」开抽屉，展示该型号下的固件/适配器版本分布与设备清单。
 * 抽屉挂在 body 上（规范 §3），不写在 .page 内。
 */
import { ListPage, StatusTag } from '../../../shared/ui/l3.mjs';
import { NOT_INTERACTIVE } from './hardware.mjs';

const esc = (v) => String(v ?? '')
  .replace(/&/g, '&amp;').replace(/</g, '&lt;')
  .replace(/>/g, '&gt;').replace(/"/g, '&quot;');
const dash = (v) => (v === '' || v == null ? '—' : esc(v));

export function renderHardwareArchivePage(models, classes, devices = []) {
  const cls = new Map(classes.map((c) => [c.code, c]));

  const inScope = models.filter((m) => {
    const c = cls.get(m.classCode) || {};
    if (c.surface === 'out') return false;
    if (NOT_INTERACTIVE.has(m.classCode)) return false;
    if (m.deviceForm === '手机') return false;
    return true;
  });

  const devicesOf = (code) => devices.filter((d) => d.modelCode === code);

  const rows = inScope.map((m) => {
    const list = devicesOf(m.code);
    const fw = [...new Set(list.map((d) => d.firmware).filter(Boolean))];
    const av = [...new Set(list.map((d) => d.adapterVersion).filter(Boolean))];
    return {
      ...m,
      className: (cls.get(m.classCode) || {}).name || m.classCode,
      deviceCount: list.length,
      fwList: fw,
      avList: av,
      fwText: fw.length ? fw.join('、') : '',
      stateLabel: m.status || '启用',
    };
  });

  const enabled = rows.filter((r) => r.stateLabel === '启用').length;

  const page = ListPage({
    statusTabs: [
      { label: '全部', count: rows.length, active: true },
      { label: '启用', count: enabled },
      { label: '停用', count: rows.length - enabled },
    ],
    filters: [
      { label: '型号', type: 'text', placeholder: '请输入型号名称或编码' },
      { label: '设备类', type: 'select', options: ['全部', ...classes.filter((c) => c.surface !== 'out').map((c) => c.name)] },
      { label: '厂商', type: 'select', options: ['全部', ...[...new Set(rows.map((r) => r.brand).filter(Boolean))].sort()] },
      { label: '固件版本', type: 'text', placeholder: '如 v3.1', extra: true },
    ],
    toolbar: [
      { label: '建型号', primary: true, attrs: { 'data-hwa-act': 'new-model' } },
      { label: '导出数据', ghost: true, attrs: { 'data-hwa-act': 'export' } },
    ],
    showCount: true,
    unit: '个',
    columns: [
      { key: 'name', label: '型号', width: '200px',
        render: (r) => `<div class="col-strong">${esc(r.name)}</div>`
          + `<div class="hw-mono">${esc(r.code)}</div>` },
      { key: 'className', label: '设备类', width: '110px', render: (r) => esc(r.className) },
      { key: 'brand', label: '厂商', width: '110px', render: (r) => dash(r.brand) },
      { key: 'capability', label: '默认能力', width: '120px', render: (r) => dash(r.capability) },
      { key: 'fwText', label: '固件版本', width: '150px',
        render: (r) => (r.fwText ? esc(r.fwText) : '—') },
      { key: 'deviceCount', label: '设备数', width: '90px', align: 'right',
        render: (r) => `${r.deviceCount} 台` },
      { key: 'status', label: '状态', width: '90px',
        render: (r) => StatusTag(r.stateLabel, r.stateLabel === '启用' ? 'enabled' : 'disabled') },
      { key: 'op', label: '操作', width: '90px', fixed: true,
        render: (r) => `<button class="hw-link" data-model="${esc(r.code)}">版本明细</button>` },
    ],
    rows,
    emptyTitle: '暂无型号',
    emptyDesc: '点击上方「建型号」开始录入。',
  });

  /* 抽屉必须挂在 body 上（规范 §3）：作为 overlay 单独返回，由构建放到 .page 之外 */
  return { html: page, overlay: drawerPayload(inScope, devicesOf, classes) };
}

/* ---------- 抽屉：版本明细 ---------- */

function drawerPayload(models, devicesOf, classes) {
  const data = models.map((m) => {
    const list = devicesOf(m.code);
    const group = (key) => {
      const out = {};
      list.forEach((d) => {
        const v = d[key];
        if (!v) return;
        out[v] = (out[v] || 0) + 1;
      });
      return out;
    };
    return {
      code: m.code,
      name: m.name,
      fw: group('firmware'),
      av: group('adapterVersion'),
      devices: list.map((d) => ({
        id: d.id,
        warehouse: d.currentWarehouse || d.warehouse || '',
        status: d.statusLabel || '',
        fw: d.firmware || '',
        av: d.adapterVersion || '',
      })),
    };
  });

  return `
<div class="hwa-drawer-mask" id="hwaMask" hidden></div>
<aside class="hwa-drawer" id="hwaDrawer" hidden>
  <div class="hwa-drawer-head">
    <h2 id="hwaTitle">版本明细</h2>
    <button type="button" class="hwa-drawer-x" data-hwa-close aria-label="关闭">×</button>
  </div>
  <div class="hwa-drawer-body" id="hwaBody"></div>
  <div class="hwa-drawer-foot" id="hwaFoot" hidden>
    <button type="button" class="btn" data-hwa-close>取消</button>
    <button type="button" class="btn primary" id="hwaSave">保存</button>
  </div>
</aside>
<script>
(function(){
  var DATA = ${JSON.stringify(data)};
  var mask = document.getElementById('hwaMask');
  var box = document.getElementById('hwaDrawer');
  var title = document.getElementById('hwaTitle');
  var body = document.getElementById('hwaBody');

  function esc(s){ return String(s == null ? '' : s).replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;'); }

  function verTable(map, label){
    var keys = Object.keys(map).sort();
    if (!keys.length) return '<p class="hwa-empty">暂无' + label + '记录</p>';
    return '<table class="dt hwa-ver"><thead><tr><th>' + label + '</th><th>设备数</th></tr></thead><tbody>'
      + keys.map(function(k){ return '<tr><td>' + esc(k) + '</td><td>' + map[k] + ' 台</td></tr>'; }).join('')
      + '</tbody></table>';
  }

  function open(code){
    var m = DATA.filter(function(x){ return x.code === code; })[0];
    if (!m) return;
    title.textContent = m.name + ' · 版本明细';
    var html = '<div class="hwa-block"><h3>固件版本</h3>' + verTable(m.fw, '固件版本') + '</div>'
      + '<div class="hwa-block"><h3>适配器版本</h3>' + verTable(m.av, '适配器版本') + '</div>'
      + '<div class="hwa-block"><h3>该型号下的设备（' + m.devices.length + ' 台）</h3>';
    if (!m.devices.length) {
      html += '<p class="hwa-empty">该型号暂无设备</p>';
    } else {
      html += '<div class="table-wrap"><table class="dt"><thead><tr>'
        + '<th>设备编号</th><th>当前仓</th><th>固件版本</th><th>适配器版本</th><th>运行状态</th>'
        + '</tr></thead><tbody>'
        + m.devices.map(function(d){
            return '<tr><td>' + esc(d.id) + '</td><td>' + esc(d.warehouse || '—') + '</td>'
              + '<td>' + esc(d.fw || '—') + '</td><td>' + esc(d.av || '—') + '</td>'
              + '<td>' + esc(d.status || '—') + '</td></tr>';
          }).join('')
        + '</tbody></table></div>';
    }
    html += '</div>';
    body.innerHTML = html;
    mask.hidden = false;
    box.hidden = false;
  }

  function close(){
    mask.hidden = true;
    box.hidden = true;
    var f = document.getElementById('hwaFoot');
    if (f) f.hidden = true;
  }

  /* ---- 建型号 ---- */
  var CLASSES = ${JSON.stringify(classes.map((c) => ({ code: c.code, name: c.name })))};

  function openNewModel(){
    title.textContent = '建型号';
    var opts = CLASSES.map(function(c){
      return '<option value="' + esc(c.code) + '">' + esc(c.name) + '</option>';
    }).join('');
    body.innerHTML =
        '<div class="hwa-form">'
      + '<label class="hwa-field"><span>型号编码<i>*</i></span>'
      + '<input id="hmCode" placeholder="如 PRT-ZD421N，同主体内唯一"></label>'
      + '<label class="hwa-field"><span>型号名称<i>*</i></span>'
      + '<input id="hmName" placeholder="如 ZD421 网络标签打印机"></label>'
      + '<label class="hwa-field"><span>设备类<i>*</i></span>'
      + '<select id="hmClass">' + opts + '</select></label>'
      + '<label class="hwa-field"><span>厂商</span>'
      + '<input id="hmBrand" placeholder="如 Zebra"></label>'
      + '<label class="hwa-field"><span>规格</span>'
      + '<input id="hmSpec" placeholder="如 203dpi / 网口 + WiFi"></label>'
      + '<label class="hwa-field"><span>默认能力</span>'
      + '<input id="hmCap" placeholder="如 打印"></label>'
      + '<label class="hwa-field"><span>默认适配器</span>'
      + '<input id="hmAdapter" placeholder="如 zpl-tcp-v1"></label>'
      + '<p class="hwa-tip">型号决定设备类与默认适配器。新增单台设备时先选型号，型号停用不影响已建设备。</p>'
      + '</div>';
    var foot = document.getElementById('hwaFoot');
    foot.hidden = false;
    document.getElementById('hwaSave').onclick = function(){
      var code = (document.getElementById('hmCode').value || '').trim();
      var name = (document.getElementById('hmName').value || '').trim();
      var cls = document.getElementById('hmClass').value;
      var err = '';
      if (!code) err = '请填写型号编码';
      else if (!/^[A-Z0-9-]{3,}$/i.test(code)) err = '型号编码只允许字母数字与短横线，至少 3 位';
      else if (!name) err = '请填写型号名称';
      else if (DATA.some(function(x){ return x.code.toUpperCase() === code.toUpperCase(); }))
        err = '型号编码 ' + code + ' 已存在，同主体内不可重复';
      var msg = document.getElementById('hmErr');
      if (err) {
        if (!msg) {
          msg = document.createElement('p');
          msg.id = 'hmErr'; msg.className = 'hwa-err';
          body.querySelector('.hwa-form').appendChild(msg);
        }
        msg.textContent = err;
        return;
      }
      /* 插入到表格首行，让演示可见；原型不持久化 */
      var tbody = document.querySelector('.page:not([hidden]) .dt tbody');
      if (tbody) {
        var clsName = (CLASSES.filter(function(c){ return c.code === cls; })[0] || {}).name || cls;
        var brand = (document.getElementById('hmBrand').value || '').trim();
        var cap = (document.getElementById('hmCap').value || '').trim();
        var tr = document.createElement('tr');
        tr.innerHTML =
            '<td class="sticky-col-left"><input type="checkbox" class="dt-row-check" aria-label="选择该行"></td>'
          + '<td><div class="col-strong">' + esc(name) + '</div><div class="hw-mono">' + esc(code) + '</div></td>'
          + '<td>' + esc(clsName) + '</td>'
          + '<td>' + (brand ? esc(brand) : '—') + '</td>'
          + '<td>' + (cap ? esc(cap) : '—') + '</td>'
          + '<td>—</td>'
          + '<td>0 台</td>'
          + '<td><span class="status-tag status-tag--enabled">启用</span></td>'
          + '<td class="col-act sticky-col-right"><button class="hw-link" data-model="' + esc(code) + '">版本明细</button></td>';
        tbody.insertBefore(tr, tbody.firstChild);
        tr.removeAttribute('hidden');
        /* 同步「全部」页签计数 */
        var tabAll = document.querySelector('.page:not([hidden]) .tab-button');
        if (tabAll) {
          var cnt = tabAll.querySelector('.tab-count');
          if (cnt) cnt.textContent = String(Number(cnt.textContent || 0) + 1);
        }
      }
      close();
      notify('型号 ' + code + ' 已建档，已插入列表首行（原型不持久化）');
    };
    mask.hidden = false;
    box.hidden = false;
  }

  function notify(text){
    var t = document.getElementById('hwaToast');
    if (!t) { t = document.createElement('div'); t.id = 'hwaToast'; t.className = 'hwa-toast'; document.body.appendChild(t); }
    t.textContent = text;
    t.classList.add('is-show');
    clearTimeout(t._t); t._t = setTimeout(function(){ t.classList.remove('is-show'); }, 2600);
  }

  document.addEventListener('click', function(e){
    var act = e.target.closest && e.target.closest('[data-hwa-act]');
    if (act) {
      var k = act.getAttribute('data-hwa-act');
      if (k === 'new-model') { openNewModel(); return; }
      if (k === 'export') { notify('导出为原型示意，真实系统按当前筛选条件导出'); return; }
    }
    var t = e.target.closest && e.target.closest('[data-model]');
    if (t) { open(t.getAttribute('data-model')); return; }
    if (e.target.closest && e.target.closest('[data-hwa-close]')) close();
    if (e.target === mask) close();
  });
  document.addEventListener('keydown', function(e){ if (e.key === 'Escape') close(); });
})();
</script>`;
}

export const HWA_CSS = `
.hwa-drawer-mask{position:fixed;inset:0;background:rgba(0,0,0,.32);z-index:300}
.hwa-drawer-mask[hidden]{display:none}
.hwa-drawer{
  position:fixed;top:0;right:0;bottom:0;width:min(720px,92vw);z-index:301;
  display:flex;flex-direction:column;background:var(--gray-0);
  border-left:1px solid var(--gray-200);box-shadow:var(--shadow-lg);
}
.hwa-drawer[hidden]{display:none}
.hwa-drawer-head{
  display:flex;align-items:center;justify-content:space-between;gap:12px;
  padding:16px 20px;border-bottom:1px solid var(--gray-200);flex-shrink:0;
}
.hwa-drawer-head h2{margin:0;font-size:16px;font-weight:650;color:var(--gray-900)}
.hwa-drawer-x{
  width:28px;height:28px;display:grid;place-items:center;cursor:pointer;
  border:0;background:transparent;color:var(--gray-500);font-size:20px;line-height:1;
  border-radius:var(--radius-sm);
}
.hwa-drawer-x:hover{background:var(--gray-100);color:var(--gray-900)}
.hwa-drawer-body{flex:1;overflow:auto;padding:20px}
.hwa-block{margin-bottom:22px}
.hwa-block h3{margin:0 0 10px;font-size:13px;font-weight:650;color:var(--gray-800)}
.hwa-ver{width:100%;border-collapse:collapse}
.hwa-ver th,.hwa-ver td{
  padding:8px 10px;text-align:left;font-size:13px;
  border-bottom:1px solid var(--gray-100);color:var(--gray-800);
}
.hwa-ver th{color:var(--gray-500);font-weight:600;background:var(--gray-50)}
.hwa-ver td:last-child,.hwa-ver th:last-child{text-align:right}
.hwa-empty{margin:0;font-size:13px;color:var(--gray-500)}
.hwa-drawer-foot{
  display:flex;justify-content:flex-end;gap:8px;
  padding:12px 20px;border-top:1px solid var(--gray-200);flex-shrink:0;
}
.hwa-drawer-foot[hidden]{display:none}
.hwa-form{display:flex;flex-direction:column;gap:12px}
.hwa-field{display:flex;flex-direction:column;gap:4px;font-size:13px;color:var(--gray-600)}
.hwa-field span{font-weight:500}
.hwa-field i{color:var(--err);font-style:normal;margin-left:2px}
.hwa-field input,.hwa-field select{
  height:32px;padding:0 10px;border:1px solid var(--gray-300);border-radius:var(--radius-sm);
  font-family:inherit;font-size:13px;color:var(--gray-900);
}
.hwa-tip{margin:4px 0 0;padding:10px 12px;background:var(--gray-50);border-radius:var(--radius-sm);
  font-size:12px;line-height:1.7;color:var(--gray-500)}
.hwa-err{margin:0;font-size:12px;color:var(--err)}
.hwa-toast{
  position:fixed;left:50%;bottom:48px;transform:translateX(-50%) translateY(8px);
  padding:10px 18px;border-radius:var(--radius-md);background:rgba(0,0,0,.84);color:#fff;
  font-size:13px;opacity:0;pointer-events:none;transition:.18s;z-index:400;
}
.hwa-toast.is-show{opacity:1;transform:translateX(-50%) translateY(0)}
`;
