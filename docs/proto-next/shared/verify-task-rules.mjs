/**
 * 任务模型一致性校验。
 *
 * 校验的是业务约束，不是数据格式。数据格式错了页面会显示异常，
 * 业务约束错了页面看起来正常，但会误导开发照着实现一个不存在于物理世界的模型。
 *
 * 依据：
 *   FR-EU-02  一人最多一个 PDA 单元加一个 PC 单元
 *   数据表    同人同类型同时只有一个活动单元
 *   异常场景  一人一手一设备，不可能同时在两个库区作业
 */

const IN_PROGRESS = new Set(['putaway', 'moving', 'counting']);
const KIND_NAME = { putaway: '上架', relocation: '移库', stocktaking: '盘点' };
const KNOWN_STATUS = new Set([
  'pending', 'putaway', 'moving', 'counting',
  'completed', 'cancelled', 'exception',
]);

/**
 * @param {object} o
 * @param {Array}  o.putaway     上架任务
 * @param {Array}  o.relocation  移库任务
 * @param {Array}  o.stocktaking 盘点任务
 * @param {Array}  o.units       执行单元
 * @returns {string[]} 违规描述，空数组表示通过
 */
export function verifyTaskRules({ putaway = [], relocation = [], stocktaking = [], units = [] }) {
  const bad = [];
  const tables = [
    ['上架', putaway],
    ['移库', relocation],
    ['盘点', stocktaking],
  ];

  /* --- 一、状态取值必须在已知集合内 --- */
  for (const [name, rows] of tables) {
    for (const r of rows) {
      if (!KNOWN_STATUS.has(r.status)) {
        bad.push(`${name} ${r.id}：状态取值未知 "${r.status}"`);
      }
    }
  }

  /* --- 二、一人同一时刻最多 1 个在办任务 --- */
  const progressByPerson = new Map();
  for (const [name, rows] of tables) {
    for (const r of rows) {
      if (!IN_PROGRESS.has(r.status)) continue;
      const who = r.operatorName;
      if (!who) {
        bad.push(`${name} ${r.id}：状态为在办但无作业员`);
        continue;
      }
      if (!progressByPerson.has(who)) progressByPerson.set(who, []);
      progressByPerson.get(who).push(`${name} ${r.id}`);
    }
  }
  for (const [who, list] of progressByPerson) {
    if (list.length > 1) {
      bad.push(`作业员 ${who}：同时有 ${list.length} 个在办任务（${list.join('、')}），`
        + '物理上一个时刻只能做一件');
    }
  }

  /* --- 三、执行单元的 currentTaskIds 必须与实际在办一致 --- */
  const actualByPerson = new Map();
  for (const [name, rows] of tables) {
    for (const r of rows) {
      if (!IN_PROGRESS.has(r.status)) continue;
      const who = r.operatorName;
      if (!who) continue;
      if (!actualByPerson.has(who)) actualByPerson.set(who, new Set());
      actualByPerson.get(who).add(r.id);
    }
  }

  const allTaskIds = new Set();
  for (const [, rows] of tables) for (const r of rows) allTaskIds.add(r.id);

  for (const u of units) {
    const who = u.currentOperatorName;
    const stored = new Set(u.currentTaskIds || []);

    /* 3.1 不得引用不存在的任务 */
    for (const tid of stored) {
      if (!allTaskIds.has(tid)) {
        bad.push(`单元 ${u.id}（${who || '未登录'}）：引用不存在的任务 ${tid}`);
      }
    }

    /* 3.2 未登录单元不得持有在办任务 */
    if (!u.currentOperatorId && stored.size > 0) {
      bad.push(`单元 ${u.id}：未登录却持有在办任务 ${[...stored].join('、')}`);
    }

    /* 3.3 与实际的在办任务逐项比对 */
    if (who) {
      const want = actualByPerson.get(who) || new Set();
      const onlyStored = [...stored].filter((x) => !want.has(x));
      const onlyActual = [...want].filter((x) => !stored.has(x));
      if (onlyStored.length || onlyActual.length) {
        const parts = [];
        if (onlyStored.length) parts.push(`单元多记 ${onlyStored.join('、')}`);
        if (onlyActual.length) parts.push(`单元漏记 ${onlyActual.join('、')}`);
        bad.push(`单元 ${u.id}（${who}）与实际任务不一致：${parts.join('；')}`);
      }
    }
  }

  /* --- 四、同一台设备不得被多个单元引用 --- */
  const devOwner = new Map();
  for (const u of units) {
    const d = u.terminalId;
    if (!d) continue;
    if (devOwner.has(d)) {
      bad.push(`设备 ${d} 同时属两个单元：${devOwner.get(d)} 与 ${u.id}`);
    } else {
      devOwner.set(d, u.id);
    }
  }

  /* --- 五、同一人不得在多个单元同时登录 --- */
  const opOwner = new Map();
  for (const u of units) {
    const who = u.currentOperatorId;
    if (!who) continue;
    if (opOwner.has(who)) {
      bad.push(`账号 ${who} 同时登录两个单元：${opOwner.get(who)} 与 ${u.id}`);
    } else {
      opOwner.set(who, u.id);
    }
  }


  /* --- 六、单元标了在办就必须真有在办任务（防反向不一致） --- */
  const actualByPerson2 = new Map();
  for (const [, rows] of tables) {
    for (const r of rows) {
      if (!IN_PROGRESS.has(r.status) || !r.operatorName) continue;
      actualByPerson2.set(r.operatorName, true);
    }
  }
  for (const u of units) {
    if (!u.currentOperatorId) continue;
    const hasTask = (u.currentTaskIds || []).length > 0;
    const markedBusy = u.status === 'busy';
    if (markedBusy && !hasTask) {
      bad.push(`单元 ${u.id}（${u.currentOperatorName || ''}）：状态标为在办，但没有在办任务`);
    }
    if (!markedBusy && hasTask) {
      bad.push(`单元 ${u.id}（${u.currentOperatorName || ''}）：有在办任务，状态却不是在办`);
    }
  }

  return bad;
}

/** 供构建日志输出用：返回每人当前在办数，便于核对 */
export function progressSummary({ putaway = [], relocation = [], stocktaking = [] }) {
  const m = new Map();
  for (const rows of [putaway, relocation, stocktaking]) {
    for (const r of rows) {
      if (!IN_PROGRESS.has(r.status) || !r.operatorName) continue;
      m.set(r.operatorName, (m.get(r.operatorName) || 0) + 1);
    }
  }
  return m;
}

/**
 * 数据警告（不阻断构建）。
 *
 * 与 verifyTaskRules 的分工：能明确判定为错的进 bad 并中止构建；
 * 需要业务裁决才能定的，只提示、不拦，避免把选择题当成填空题自动作答。
 *
 * @param {object} o
 * @returns {string[]} 警告描述
 */
export function verifyTaskWarnings({ units = [] } = {}) {
  const warn = [];

  /* 同名不同工号：一人一单元口径下不可能，但合并哪条属业务裁决，故只提示 */
  const idByName = new Map();
  for (const u of units) {
    const nm = u.currentOperatorName;
    const id = u.currentOperatorId;
    if (!nm || !id) continue;
    if (idByName.has(nm) && idByName.get(nm) !== id) {
      warn.push(`姓名「${nm}」对应两个工号：${idByName.get(nm)} 与 ${id}，按人聚合会算成两个人；`
        + '一人一单元口径下需裁决保留哪条');
    } else {
      idByName.set(nm, id);
    }
  }

  return warn;
}
