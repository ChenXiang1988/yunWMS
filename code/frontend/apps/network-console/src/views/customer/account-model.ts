import type { AuditLogItem, OwnerAccountPayload, OwnerStatus } from '@/api/owner'

/**
 * 货主主账号与操作日志的纯转换：开号表单归一、开号入口判定、审计行的展示归一。
 *
 * 这里不 import 请求层——`http` 依赖 `import.meta.env` 与浏览器环境，一旦引入就
 * 无法用 `node:test` 直接跑；也不 import 本地其他模型文件——单测走
 * `node --experimental-strip-types`，它不支持无扩展名的相对导入，而带 `.ts` 后缀的
 * 导入又会被 `vue-tsc` 拒绝（未开 `allowImportingTsExtensions`）。因此时间格式化仍
 * 沿用 `owner-model.ts` 的 `formatIsoDateTime`（唯一实现），由展示层调用。
 */

/** 主账号姓名的长度上限，与契约列宽一致：64 个字符 */
export const ACCOUNT_DISPLAY_NAME_MAX = 64

/**
 * 操作日志的对象类型编码。
 *
 * 货主档案与它的子表（联系人、税号）都记在 `OWNER` 下，`bizId` 是货主编码，
 * 所以「按货主查操作日志」是一条查询，不必按子表类型各查一次。
 */
export const AUDIT_BIZ_TYPE_OWNER = 'OWNER'

/**
 * 操作日志每页条数。
 *
 * 取 `ui-structure.md`「操作日志抽屉」的「最近 100 条」：首屏就是最近 100 条，更早的
 * 记录靠翻页（后端按操作时间倒序返回，前端不重排）。
 */
export const AUDIT_PAGE_SIZE = 100

/**
 * 审计动作编码 → 中文名，键取自契约 `action` 的七个枚举值。
 *
 * 不认识的编码不回退原文：界面上出现 `PERMISSION_CHANGE` 这种英文枚举名，比显示占位符
 * 更让人困惑，同一行还有后端给出的人读摘要可以说清发生了什么。
 */
const AUDIT_ACTION_TEXT: Readonly<Record<string, string>> = {
  CREATE: '新增',
  UPDATE: '修改',
  DELETE: '删除',
  AUDIT: '审核',
  PERMISSION_CHANGE: '权限变更',
  LOGIN: '登录',
  LABEL_PRINT: '标签打印'
}

/**
 * 开号表单值：两个字段都是字符串，未填是空串而不是 null。
 *
 * 原因在控件：antd 的受控输入把 `undefined`/`null` 当「非受控」，清空后回填会失灵。
 * 归一成请求体只发生在提交那一刻（`buildOpenAccountPayload`）。
 */
export interface AccountFormValues {
  /** 主账号姓名（展示名）；登录货主端后显示在页头，与登录名无关 */
  displayName: string
  /** 初始密码明文；只在填表到提交之间存在，提交结束立即从状态里清掉 */
  initialPassword: string
}

/** 开号表单的初始值：全空 */
export function emptyAccountForm(): AccountFormValues {
  return { displayName: '', initialPassword: '' }
}

/**
 * 构造开号请求体；展示名为纯空白时返回 null，表示不构造请求（不发必然被拒的请求）。
 *
 * - 展示名去两端空白：后端把只填空白按未填处理，表单校验也会先拦下纯空白，
 *   这里只是不给「带着空白提交」留口子；
 * - 初始密码**不去空白**：前后的空格也是密码的一部分，裁掉会让用户设的密码与
 *   之后登录时输入的密码对不上。密码策略（长度与字符组合）由后端判定，前端不复述。
 */
export function buildOpenAccountPayload(form: AccountFormValues): OwnerAccountPayload | null {
  const displayName = form.displayName.trim()
  if (!displayName) {
    return null
  }
  return { displayName, initialPassword: form.initialPassword }
}

/**
 * 开通入口是否可用：只有「合作中」且还没开出主账号的货主可以开。
 *
 * 这是把后端规则提前映射成交互状态，目的是不让人白填一次表单，不构成规则的第二真源——
 * 后端仍做最终判定（非合作中 40900、同一货主重复开号 40900）。
 *
 * 「已开号」只取本次开通成功的响应：台账里没有按货主查询主账号的端点，页面拿不到
 * 历史账号，也就不假装知道。
 */
export function canOpenOwnerAccount(status: OwnerStatus, openedLoginName: string | null): boolean {
  return status === 'ACTIVE' && openedLoginName === null
}

/**
 * 审计行在表格里的展示值。
 *
 * 文本字段用空串表示「无值」，占位符由展示层统一套（`owner-model.ts` 的 `displayText`）：
 * 同一份归一不必在模型里再写一遍占位符字面量。
 */
export interface AuditRowView {
  /** 表格行键，取日志标识 */
  key: string
  /** 操作时间，原样 ISO 8601；展示层用 `formatIsoDateTime` 格式化 */
  createdAt: string
  /** 动作中文名；不认识的编码为空串 */
  action: string
  /** 操作人姓名；系统触发或缺失时为空串 */
  operator: string
  /** 后端给出的变更摘要；没有时为空串 */
  summary: string
  /** 变更前快照的 JSON 文本；无快照时为空串 */
  beforeText: string
  /** 变更后快照的 JSON 文本；无快照时为空串 */
  afterText: string
  /** 是否有可展开的快照：两者都没有时不显示展开箭头 */
  hasSnapshot: boolean
}

/** 把一条审计记录投影成表格行 */
export function auditRowView(record: AuditLogItem): AuditRowView {
  const beforeText = auditSnapshotText(record.beforeValue)
  const afterText = auditSnapshotText(record.afterValue)
  return {
    key: record.id,
    createdAt: record.createdAt,
    action: AUDIT_ACTION_TEXT[record.action] ?? '',
    operator: trimToEmpty(record.operatorName),
    summary: trimToEmpty(record.summary),
    beforeText,
    afterText,
    hasSnapshot: beforeText !== '' || afterText !== ''
  }
}

/** 整批投影：顺序即后端返回的顺序（按操作时间倒序），前端不排序、不重排 */
export function auditRowViews(records: readonly AuditLogItem[]): AuditRowView[] {
  return records.map(auditRowView)
}

/**
 * 快照文本：把原样返回的快照展开成可读的多行 JSON。
 *
 * 子表类日志的快照是完整集合而不是差异，压成一行会看不出改了哪一条。
 */
export function auditSnapshotText(value: Record<string, unknown> | null | undefined): string {
  if (!value) {
    return ''
  }
  return JSON.stringify(value, null, 2)
}

/** 缺失或纯空白归一成空串；判空只在展示层做一次，模板里不再写 `?? ''` */
function trimToEmpty(value: string | null | undefined): string {
  return (value ?? '').trim()
}
