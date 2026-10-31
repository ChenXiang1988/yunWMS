/**
 * 设备模型一致性校验。
 *
 * 只校验设备管理，不涉及执行单元与任务。
 * 依据 DOC-SPC-001 硬件管理 PRD 与 DOC-SPC-004 设备身份。
 */

const FIVE_STATE = new Set(['未到货', '待分配', '已分配', '调拨中', '已核销']);
const EMPTY_WAREHOUSE = new Set(['未到货', '调拨中', '已核销']);

/**
 * @param {object} o
 * @param {Array} o.devices    单台设备
 * @param {Array} o.models     型号档案
 * @param {Array} o.classes    设备类
 * @param {Array} o.transfers  调拨单
 * @returns {string[]} 违规描述
 */
export function verifyDeviceRules({ devices = [], models = [], classes = [], transfers = [], bins = [], warehouses = [] }) {
  const bad = [];
  const modelBy = new Map(models.map((m) => [m.code, m]));
  const classBy = new Map(classes.map((c) => [c.code, c]));
  const devBy = new Map(devices.map((d) => [d.id, d]));
  const binBy = new Map(bins.map((b) => [b.id, b]));
  /* 实体仓名单：总仓是概念集合，不是实体，设备只能归属实体仓 */
  const entityWh = new Set(warehouses.filter((w) => w.isEntity).map((w) => w.name));

  for (const d of devices) {
    /* 一、型号必须存在（防止关联断裂，如少写 PRT- 前缀） */
    if (d.modelCode && !modelBy.has(d.modelCode)) {
      bad.push(`设备 ${d.id}：型号 ${d.modelCode} 不存在于型号档案`);
      continue;
    }
    const mdl = modelBy.get(d.modelCode) || {};

    /* 二、型号的设备类必须与设备一致 */
    if (mdl.classCode && mdl.classCode !== d.classCode) {
      bad.push(`设备 ${d.id}：型号 ${d.modelCode} 属 ${mdl.classCode}，与设备类 ${d.classCode} 不符`);
    }

    /* 三、归属状态取值合法，且与当前仓存在性一致 */
    const s = d.assignStatus;
    if (!FIVE_STATE.has(s)) {
      bad.push(`设备 ${d.id}：归属状态 "${s}" 不在五态内`);
    } else if (EMPTY_WAREHOUSE.has(s) && d.currentWarehouse) {
      bad.push(`设备 ${d.id}：状态 ${s} 时当前仓应为空，实为 ${d.currentWarehouse}`);
    } else if (!EMPTY_WAREHOUSE.has(s) && !d.currentWarehouse) {
      bad.push(`设备 ${d.id}：状态 ${s} 时须有当前仓`);
    }

    /* 三之二、设备只能归属实体仓（总仓是概念集合，不是实体） */
    const where = d.currentWarehouse || '';
    if (where && entityWh.size && !entityWh.has(where)) {
      bad.push(`设备 ${d.id}：归属仓「${where}」不是实体仓。总仓是概念集合，设备只能归属实体仓`);
    }

    /* 四、打印机须有接入形态；USB 形态须挂 PC */
    if (d.classCode === 'HW-PRINTER') {
      if (!d.printerForm) {
        bad.push(`打印机 ${d.id}：缺接入形态`);
      } else if (d.printerForm === 'USB接PC' && !d.boundPc) {
        bad.push(`打印机 ${d.id}：USB 形态未挂 PC`);
      }
      if (!d.mediaType) bad.push(`打印机 ${d.id}：缺当前介质类型`);

      /* 五、型号须有打印宽度与 DPI，否则无法校验标签尺寸 */
      if (mdl.classCode === 'HW-PRINTER') {
        if (!mdl.maxPrintWidthMm) bad.push(`型号 ${d.modelCode}：缺最大打印宽度，无法校验标签尺寸`);
        if (!mdl.dpi) bad.push(`型号 ${d.modelCode}：缺分辨率`);
      }
    }

    /* 六、作业终端须有 IMEI（自有设备除外，走应用 UUID） */
    if (d.classCode === 'HW-PDA') {
      if (d.accessType === '自有设备') {
        if (!d.appUuid) bad.push(`设备 ${d.id}：自有设备缺应用 UUID`);
        if (d.imei) bad.push(`设备 ${d.id}：自有设备不应采集 IMEI`);
      } else if (!d.imei) {
        bad.push(`设备 ${d.id}：受管终端缺 IMEI`);
      }
    }
  }

  /* 七、唯一性：设备编号、序列号、资产编号、IMEI */
  const uniq = (field, label) => {
    const seen = new Map();
    for (const d of devices) {
      const v = d[field];
      if (!v) continue;
      if (seen.has(v)) bad.push(`${label}重复：${v}（${seen.get(v)} 与 ${d.id}）`);
      else seen.set(v, d.id);
    }
  };
  uniq('sn', '序列号');
  uniq('assetNo', '资产编号');
  uniq('imei', 'IMEI');

  /* 八、PC 与打印机须挂工位，且工位必须是工位型库位、同仓 */
  for (const d of devices) {
    if (d.classCode !== 'HW-PC' && d.classCode !== 'HW-PRINTER') continue;

    const wsId = d.workstationId;
    if (!wsId) {
      /* 未挂工位是合法状态（待挂载），但不得同时声称已就位 */
      if (d.location) {
        bad.push(`设备 ${d.id}：未挂工位却填了位置「${d.location}」`);
      }
      continue;
    }
    const ws = binBy.get(wsId);
    if (!ws) {
      bad.push(`设备 ${d.id}：工位 ${wsId} 不存在于库位`);
      continue;
    }
    if (ws.binType !== '工位') {
      bad.push(`设备 ${d.id}：所挂 ${wsId} 的库位类型为「${ws.binType}」，不是工位`);
    }
    if (entityWh.size && !entityWh.has(ws.warehouse)) {
      bad.push(`设备 ${d.id}：所挂工位 ${wsId} 属「${ws.warehouse}」，不是实体仓`);
    }
    if (ws.warehouse !== (d.currentWarehouse || d.warehouse)) {
      bad.push(`设备 ${d.id}：所在仓 ${d.currentWarehouse || d.warehouse} 与工位所属仓 ${ws.warehouse} 不一致`);
    }
    if (d.location !== wsId) {
      bad.push(`设备 ${d.id}：location 应为工位编号 ${wsId}，实为「${d.location}」`);
    }
  }

  /* 十一、打印机接入形态与适配器、地址须匹配 */
  /* USB 形态不再作为独立设备管理，已并入 PC 的 attachedPrinters */
  const FORM_ADAPTER = { '网口直连': ['zpl-tcp-v1', 'raw-tcp-v1'], '云打印': ['cloud-print-v1'] };
  for (const d of devices) {
    if (d.classCode !== 'HW-PRINTER') continue;
    const form = d.printerForm;
    if (!form) continue;
    const ok = FORM_ADAPTER[form];
    if (!ok) { bad.push(`打印机 ${d.id}：接入形态「${form}」不在三形态内`); continue; }
    if (!ok.includes(d.adapter)) {
      bad.push(`打印机 ${d.id}：接入形态 ${form} 应配 ${ok.join('/')}，实为 ${d.adapter}`);
    }
    if (!d.endpoint) bad.push(`打印机 ${d.id}：缺接入地址`);
    if (form === '网口直连' && !d.ip) bad.push(`打印机 ${d.id}：网口形态缺 IP`);
    if (form === '云打印' && !d.cloudDeviceId) bad.push(`打印机 ${d.id}：云形态缺云设备号`);
  }

  /* 十二、PC 须有主板序列号与代理信息（代理是唯一识别路径） */
  for (const d of devices) {
    if (d.classCode !== 'HW-PC') continue;
    if (!d.mainboardSn) bad.push(`PC ${d.id}：缺主板序列号`);
    if (!d.mac) bad.push(`PC ${d.id}：缺 MAC，代理须同时上报序列号与 MAC`);
    if (!d.adapter || d.adapter !== 'pc-agent-v1') bad.push(`PC ${d.id}：适配器应为 pc-agent-v1`);
    if (!d.endpoint) bad.push(`PC ${d.id}：缺代理地址`);
    /* 代理端口不得与打印机 RAW 端口 9100 冲突 */
    const port = d.agentPort;
    if (port === 9100) bad.push(`PC ${d.id}：代理端口 9100 与打印机 RAW 端口冲突，须改其他端口`);

    /* 挂载打印机作为 PC 的配置项，须有名称与介质；超出 2 台告警 */
    const ps = d.attachedPrinters || [];
    if (ps.length > 2) bad.push(`PC ${d.id}：挂了 ${ps.length} 台打印机，超出常见工位配置`);
    for (const pr of ps) {
      if (!pr.name) bad.push(`PC ${d.id}：挂载打印机缺名称`);
      if (!pr.mediaType) bad.push(`PC ${d.id} 的 ${pr.name}：缺介质类型`);
      if (!pr.mediaSize) bad.push(`PC ${d.id} 的 ${pr.name}：缺介质尺寸`);
    }
  }

  /* 一个工位只能挂一台 PC */
  const pcByWs = new Map();
  for (const d of devices) {
    if (d.classCode !== 'HW-PC' || !d.workstationId) continue;
    if (pcByWs.has(d.workstationId)) {
      bad.push(`工位 ${d.workstationId}：挂了多台 PC（${pcByWs.get(d.workstationId)} 与 ${d.id}），应为 1 工位 1 台`);
    } else {
      pcByWs.set(d.workstationId, d.id);
    }
  }

  /* 十三、调拨行引用的设备须存在 */
  for (const t of transfers) {
    for (const line of t.lines || []) {
      if (!devBy.has(line.hardwareId)) {
        bad.push(`调拨单 ${t.id}：引用不存在的设备 ${line.hardwareId}`);
      }
    }
  }

  return bad;
}
