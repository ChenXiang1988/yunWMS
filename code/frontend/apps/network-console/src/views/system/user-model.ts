import type { SubjectType } from '@/api/auth'
import type {
  CreateUserPayload,
  UpdateUserPayload,
  UserDetail,
  UserListQuery,
  UserStatus
} from '@/api/user'

/**
 * 用户页签的纯转换：状态词表、角色标签回退、表单归一与请求体构造、启停方向与可用性。
 *
 * 这里不 import 请求层——`http` 依赖 `import.meta.env` 与浏览器环境，一旦引入就
 * 无法用 `node:test` 直接跑；也不 import 本应用其他模型文件——单测走
 * `node --experimental-strip-types`，它不支持无扩展名的相对导入，而带 `.ts` 后缀的
 * 导入又会被 `vue-tsc` 拒绝（未开 `allowImportingTsExtensions`）。因此占位符与时间
 * 展示在这里各留一份最小实现，与 `views/customer/owner-model.ts` 同一口径；
 * 出现第三个域时再提到共享模块。
 */

/** 空值与纯空白的占位符，与客户页同一口径 */
export const PLACEHOLDER = '—'

/** 字段长度上限，与契约列宽一致：登录名 64、展示名 64、邮箱 128、企业微信 userid 64 个字符 */
export const USER_LOGIN_NAME_MAX = 64
export const USER_DISPLAY_NAME_MAX = 64
export const USER_EMAIL_MAX = 128
export const USER_WECOM_MAX = 64

/** 列表默认每页条数，与后端默认值一致 */
export const USER_DEFAULT_PAGE_SIZE = 20

/**
 * 下拉选项源的每页条数，取后端允许的上限。
 *
 * 角色与货主都只有分页端点，下拉又需要全量选项，页面按这个页长逐页取完；不能只取
 * 第一页——那会让超过一页的选项静默消失。
 */
export const USER_OPTION_PAGE_SIZE = 200

/* -------------------------- 词表 -------------------------- */

/** 账号状态文案：键与后端枚举一一对应 */
export const USER_STATUS_TEXT: Record<UserStatus, string> = {
  ACTIVE: '启用',
  DISABLED: '停用'
}

/**
 * 账号状态文案。
 *
 * 未知状态回退原值：后端将来新增状态时，界面显示原文总好过显示空白或 `undefined`。
 */
export function userStatusText(status: string): string {
  return USER_STATUS_TEXT[status as UserStatus] ?? status
}

/**
 * 状态筛选选项，顺序与启停方向一致（启用 → 停用）。
 *
 * 用可变数组而不是 `ReadonlyArray`：antd 的 `Select` 的 `options` 形参是可变类型
 * `DefaultOptionType[]`，只读数组传不进去。取值本身是固定字面量，调用方不改写。
 */
export const USER_STATUS_OPTIONS: Array<{ value: UserStatus; label: string }> = [
  { value: 'ACTIVE', label: USER_STATUS_TEXT.ACTIVE },
  { value: 'DISABLED', label: USER_STATUS_TEXT.DISABLED }
]

/** 主体类型文案：键与后端枚举一一对应 */
export const SUBJECT_TYPE_TEXT: Record<SubjectType, string> = {
  STAFF: '内部员工',
  OWNER: '货主用户'
}

/** 主体类型文案；未知编码回退原值，理由同上 */
export function subjectTypeText(subjectType: string): string {
  return SUBJECT_TYPE_TEXT[subjectType as SubjectType] ?? subjectType
}

/** 主体类型筛选选项，顺序与后端枚举一致；可变数组的理由同 `USER_STATUS_OPTIONS` */
export const SUBJECT_TYPE_OPTIONS: Array<{ value: SubjectType; label: string }> = [
  { value: 'STAFF', label: SUBJECT_TYPE_TEXT.STAFF },
  { value: 'OWNER', label: SUBJECT_TYPE_TEXT.OWNER }
]

/* -------------------------- 展示 -------------------------- */

/** null、空串与纯空白都显示占位符 */
export function displayText(value: string | null | undefined): string {
  const text = value?.trim()
  return text ? text : PLACEHOLDER
}

/**
 * 最后登录时间的人读文本。
 *
 * 从未登录过显示「从未登录」而不是占位符：这是契约里 `lastLoginAt` 为 null 的唯一含义，
 * 空白会让人以为是取数失败。格式与客户页的 `formatIsoDateTime` 同一口径（本地时区
 * YYYY-MM-DD HH:mm），不用日期组件是为了能在 `node:test` 里直接断言。
 */
export function lastLoginText(value: string | null): string {
  if (!value) {
    return '从未登录'
  }
  const time = new Date(value)
  if (Number.isNaN(time.getTime())) {
    return PLACEHOLDER
  }
  const month = pad2(time.getMonth() + 1)
  const day = pad2(time.getDate())
  return `${time.getFullYear()}-${month}-${day} ${pad2(time.getHours())}:${pad2(time.getMinutes())}`
}

/**
 * 角色列的展示文本。
 *
 * 列表返回的是角色编码，人能读的名称来自 `GET /console/roles`。编码在角色列表里找不到
 * 时回退原编码而不是留空：角色可能已被删除（历史用户仍挂着），也可能角色列表加载失败，
 * 两种情形下显示编码至少能与接口日志对上。整串为空时由展示层套 `displayText`。
 */
export function roleLabelText(
  codes: readonly string[],
  names: ReadonlyMap<string, string>
): string {
  return codes.map((code) => names.get(code) ?? code).join('、')
}

/* -------------------------- 列表查询 -------------------------- */

/** 筛选栏字段，与页面顶部筛选栏的输入一一对应 */
export interface UserFilterValues {
  keyword?: string
  subjectType?: SubjectType
  status?: UserStatus
}

/**
 * 归一化列表查询参数。
 *
 * 空字符串与纯空白的关键词一律去掉：后端把全空白 keyword 按不筛选处理，前端不必发。
 * 页码与每页条数由分页组件给出合法值（它只提供 1 与 10/20/50/100），不在这里再做
 * 越界收敛——那会变成后端分页规则的又一份副本。
 */
export function buildUserListQuery(
  filters: UserFilterValues,
  pageNo: number,
  pageSize: number
): UserListQuery {
  const query: UserListQuery = { pageNo, pageSize }

  const keyword = filters.keyword?.trim()
  if (keyword) {
    query.keyword = keyword
  }
  if (filters.subjectType) {
    query.subjectType = filters.subjectType
  }
  if (filters.status) {
    query.status = filters.status
  }

  return query
}

/* -------------------------- 启停 -------------------------- */

/**
 * 启停按钮的目标状态：启用中的账号下一步是停用，反之亦然。
 *
 * 目标状态与当前状态相同时后端按成功处理（幂等），但那不是可以点的选项，这里直接给出
 * 相反的那一个。
 */
export function nextUserStatus(status: UserStatus): UserStatus {
  return status === 'ACTIVE' ? 'DISABLED' : 'ACTIVE'
}

/**
 * 启停入口是否可用。
 *
 * 前端能自己判断的只有一条：目标行不是当前登录账号——自己的账号不能在这里停用，因为
 * 停用会把操作人自己锁在门外（改自己请走「修改密码」），后端也会以 40900 拒绝。另外
 * 两条保护（不能停用最后一个可用的超级管理员、仍担任货主负责人的员工不能停用）需要
 * 全库视角，前端算不出来，只把后端的 40900 提示原样呈现。
 *
 * `selfUserId` 由调用方给：台账里没有「当前登录用户」的读端点，登录响应也不回带用户
 * 标识，因此本页目前传 null，函数此时不拦任何行；等身份接口落地后填入即可生效。
 */
export function canToggleUserStatus(userId: string, selfUserId: string | null): boolean {
  return selfUserId === null || userId !== selfUserId
}

/* -------------------------- 表单 -------------------------- */

/**
 * 用户表单值：新建与编辑共用一个结构，两边只渲染各自需要的字段。
 *
 * 文本字段一律用字符串，未填是空串而不是 null——antd 的受控输入把 `undefined`/`null`
 * 当「非受控」，清空后回填会失灵。空串转 null 只发生在提交那一刻
 * （`buildCreateUserPayload` / `buildUpdateUserPayload`）。
 */
export interface UserFormValues {
  /** 登录名；编辑态只读，取自详情 */
  loginName: string
  displayName: string
  /** 主体类型；编辑态只读，取自详情 */
  subjectType: SubjectType
  /** 所属货主标识；仅货主用户需要，内部员工不提交 */
  ownerId: string
  email: string
  wecomUserId: string
  /** 初始密码明文，仅新建态使用；提交结束立即从状态里清掉 */
  initialPassword: string
  /** 再次输入的初始密码，纯前端交互，不提交 */
  confirmPassword: string
  /** 勾选的角色标识；新建时可一并分配 */
  roleIds: string[]
}

/**
 * 新建表单的初始值。
 *
 * 主体类型默认内部员工：仓网端的主要管理对象是员工，且该字段创建后不可修改，选错只能
 * 另建账号——默认值放在最常用的那一项上，选货主用户是明确的主动动作。
 */
export function emptyUserForm(): UserFormValues {
  return {
    loginName: '',
    displayName: '',
    subjectType: 'STAFF',
    ownerId: '',
    email: '',
    wecomUserId: '',
    initialPassword: '',
    confirmPassword: '',
    roleIds: []
  }
}

/** 把用户详情回填成表单值；后端返回的 null 一律显示为空串 */
export function editUserFormFrom(user: UserDetail): UserFormValues {
  return {
    loginName: user.loginName,
    displayName: user.displayName,
    subjectType: user.subjectType,
    ownerId: user.ownerId ?? '',
    email: user.email ?? '',
    wecomUserId: user.wecomUserId ?? '',
    initialPassword: '',
    confirmPassword: '',
    roleIds: [...user.roleIds]
  }
}

/**
 * 构造新建请求体；登录名、展示名或初始密码为空白时返回 null，表示不构造请求。
 *
 * - 登录名与展示名去两端空白：后端把只填空白按未填处理，表单校验也会先拦下；
 * - 邮箱与企业微信 userid 未填或纯空白转 null（后端以 null 表示未填）；
 * - 初始密码**不去空白**：前后的空格也是密码的一部分，裁掉会让管理员设的密码与本人
 *   之后登录时输入的密码对不上。密码策略（长度与字符组合）由后端判定，前端不复述；
 * - 所属货主只在主体类型为货主用户时出现：内部员工必须不传该字段。
 */
export function buildCreateUserPayload(form: UserFormValues): CreateUserPayload | null {
  const loginName = form.loginName.trim()
  const displayName = form.displayName.trim()
  if (!loginName || !displayName || !form.initialPassword) {
    return null
  }

  const payload: CreateUserPayload = {
    loginName,
    displayName,
    subjectType: form.subjectType,
    email: blankToNull(form.email),
    wecomUserId: blankToNull(form.wecomUserId),
    initialPassword: form.initialPassword,
    roleIds: [...form.roleIds]
  }

  if (form.subjectType === 'OWNER') {
    payload.ownerId = form.ownerId.trim()
  }

  return payload
}

/**
 * 构造编辑请求体：编辑接口全量替换三项可编辑字段，所以每次都要给全。
 *
 * 登录名、主体类型与所属货主不可改，不在这里出现。内部员工的邮箱不能清空——这一条由
 * 表单的必填规则先拦，构造层不重复判断主体类型。
 */
export function buildUpdateUserPayload(form: UserFormValues): UpdateUserPayload {
  return {
    displayName: form.displayName.trim(),
    email: blankToNull(form.email),
    wecomUserId: blankToNull(form.wecomUserId)
  }
}

/**
 * 两次输入的密码是否不一致。
 *
 * 这是纯前端能判的事，也是后端看不到的信息，因此必须在前端判。**不做裁剪**：密码里的
 * 空格是内容的一部分，裁剪会让「输入一致」的判定与实际提交的密码不一致。长度与字符
 * 组合一律交给后端，它一次列全违规项；前端再写一遍就成了两套规则。
 */
export function passwordMismatch(first: string, second: string): boolean {
  return first !== second
}

/** 可选文本已填则去空白保留，未填或纯空白转 null */
function blankToNull(value: string | null | undefined): string | null {
  const text = (value ?? '').trim()
  return text ? text : null
}

function pad2(value: number): string {
  return String(value).padStart(2, '0')
}
