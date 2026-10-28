import { http } from './client'
import type { SubjectType } from './auth'
import type { PageResult } from './owner'

/**
 * 用户账号接口：列表、详情、新建、编辑、启停、重置密码、分配角色与踢下线。
 *
 * 两处复用而不是各写一份：
 *
 * - 分页响应的外壳用 `api/owner.ts` 的 `PageResult`：它是 `ApiResult.data` 里分页体的
 *   唯一声明，这里再写一份就成了同一个契约的两处定义；
 * - 主体类型用 `api/auth.ts` 的 `SubjectType`：登录响应与用户列表说的是同一件事。
 *
 * 角色列表（`GET /console/roles`）也放在本文件：本任务里它只有「分配角色」一个消费者，
 * 等「角色与权限」页签落地时再拆到 `api/role.ts`。
 */

/** 账号状态：与后端枚举一一对应，只列后端声明过的值 */
export type UserStatus = 'ACTIVE' | 'DISABLED'

/** 用户列表行，字段与后端 `UserListItemResponse` 一一对应 */
export interface UserListItem {
  /** 用户标识，字符串形式的十进制整数；详情、编辑、启停、分配角色都用它定位 */
  id: string
  /** 登录名，全局唯一且不区分大小写 */
  loginName: string
  /** 展示名，界面上显示给人看的名字，可与登录名不同 */
  displayName: string
  /** 主体类型：STAFF 内部员工，OWNER 货主用户；创建后不可修改 */
  subjectType: SubjectType
  /** 所属货主标识；内部员工为 null，前端据此显示占位符而不是空白 */
  ownerId: string | null
  /** 所属货主名称；内部员工，或货主已被逻辑删除时为 null */
  ownerName: string | null
  /** 账号状态编码：ACTIVE 启用，DISABLED 停用 */
  status: UserStatus
  /** 已分配的角色编码列表（含停用中的角色）；没有角色时为空数组 */
  roles: string[]
  /** 最后登录时刻，ISO 8601 带时区（UTC）；从未登录过为 null */
  lastLoginAt: string | null
}

/**
 * 用户详情，字段与后端 `UserDetailResponse` 一一对应。
 *
 * 前九个字段与列表行同名同义，故直接继承 `UserListItem`，不重复声明。`dataScopeType` /
 * `ownerIds` / `createdAt` 本页签不消费（数据范围属独立页签），保留是为了让类型完整
 * 对齐响应，避免下游误以为响应里没有这些字段。
 */
export interface UserDetail extends UserListItem {
  /** 邮箱；货主账号可为 null，内部员工必有值（用于自助找回密码） */
  email: string | null
  /** 企业微信 userid，用于应用消息推送；可为 null */
  wecomUserId: string | null
  /** 用户级数据范围覆盖值；null 表示没有覆盖、继承角色默认 */
  dataScopeType: 'ALL' | 'ASSIGNED' | 'CUSTOM' | null
  /** 覆盖范围为 CUSTOM 时已选定的货主标识；其他情况为空数组 */
  ownerIds: string[]
  /** 账号创建时刻，ISO 8601 带时区（UTC） */
  createdAt: string
  /** 已分配的角色标识，用于回填「分配角色」的勾选状态；与 roles 是同一批角色的两种表示 */
  roleIds: string[]
}

/** 用户列表查询参数，与 `GET /api/console/users` 的 query 一一对应 */
export interface UserListQuery {
  /** 页码，从 1 开始 */
  pageNo: number
  /** 每页条数，取值 1 至 200；越界后端返回 40000 */
  pageSize: number
  /** 按登录名或姓名模糊匹配；省略表示不筛选 */
  keyword?: string
  /** 主体类型；省略表示不筛选 */
  subjectType?: SubjectType
  /** 账号状态；省略表示不筛选 */
  status?: UserStatus
}

/**
 * 新建用户请求体，字段与后端 `UserCreateRequest` 一一对应。
 *
 * `ownerId` 只在主体类型为 OWNER 时出现：契约要求货主用户必填、内部员工必须不传，
 * 所以是可选字段而不是 `string | null`——内部员工传 null 也算「传了」。
 */
export interface CreateUserPayload {
  /** 登录名，必填，最长 64 个字符；被占用返回 40901 */
  loginName: string
  /** 展示名，必填，最长 64 个字符 */
  displayName: string
  /** 主体类型，创建后不可修改：它决定 ownerId 与 email 是否必填 */
  subjectType: SubjectType
  /** 所属货主标识，字符串形式的十进制整数；仅 OWNER 传，且必须存在 */
  ownerId?: string
  /** 邮箱，最长 128 个字符；内部员工必填，被其他账号占用返回 40903 */
  email: string | null
  /** 企业微信 userid，最长 64 个字符；可为空 */
  wecomUserId: string | null
  /**
   * 初始密码明文，必填，需满足后端密码策略。
   *
   * 明文只出现在这一次请求体里：服务端只存哈希并把账号标记为「首次登录须改密」，
   * 前端提交后立即从组件状态里清掉，不写存储、不回显、不进日志。
   */
  initialPassword: string
  /** 要分配的角色标识；空数组表示不分配角色 */
  roleIds: string[]
}

/** 新建用户的响应，字段与后端 `CreatedUserResponse` 一致；口令不回显 */
export interface CreatedUser {
  /** 新账号的用户标识，后续详情、编辑、启停、分配角色都用它定位 */
  id: string
  /** 新账号的登录名，按落库值返回 */
  loginName: string
}

/**
 * 编辑用户请求体，字段与后端 `UserUpdateRequest` 一一对应。
 *
 * 全量替换：传 null 表示清空，不传同样按 null 处理，所以调用方必须提交希望保留的
 * 全部可编辑字段。登录名、主体类型与所属货主不在可编辑范围内，类型里也不出现——
 * 多传会被后端拒绝而不是静默忽略。
 */
export interface UpdateUserPayload {
  /** 展示名，必填，最长 64 个字符 */
  displayName: string
  /** 邮箱，最长 128 个字符；内部员工必须保留非空，清空返回 40000 */
  email: string | null
  /** 企业微信 userid，最长 64 个字符；传 null 表示清空 */
  wecomUserId: string | null
}

/**
 * 分页查询用户账号。
 *
 * 拦截器已把 `ApiResult` 裁成 `data`，业务失败（403、分页越界返回 40000）由拦截器
 * 统一提示并 reject，调用方只需 catch。
 */
export function listUsers(query: UserListQuery): Promise<PageResult<UserListItem>> {
  return http.get<PageResult<UserListItem>, PageResult<UserListItem>>('/console/users', {
    params: query
  })
}

/**
 * 查询用户详情。
 *
 * 列表行不带邮箱、企业微信 userid 与角色标识，编辑与分配角色前必须走这里回填。
 * 账号不存在或已被逻辑删除返回 40400。
 */
export function getUser(id: string): Promise<UserDetail> {
  return http.get<UserDetail, UserDetail>(`/console/users/${id}`)
}

/**
 * 创建用户账号。
 *
 * 新账号一律启用并标记为首次登录须改密。登录名被占用返回 40901、邮箱被占用返回
 * 40903、密码不满足策略返回 40000，均由拦截器把后端提示原样弹出。
 */
export function createUser(payload: CreateUserPayload): Promise<CreatedUser> {
  return http.post<CreatedUser, CreatedUser, CreateUserPayload>('/console/users', payload)
}

/**
 * 编辑用户基本信息（全量替换展示名、邮箱与企业微信 userid）。
 *
 * 成功不回带详情，需要最新值时重新取——调用方刷新列表，而不是拿本地表单值当服务端结果。
 */
export function updateUser(id: string, payload: UpdateUserPayload): Promise<void> {
  return http.put<void, void, UpdateUserPayload>(`/console/users/${id}`, payload)
}

/**
 * 启用或停用账号。
 *
 * 停用立即生效：既有会话随即失效且无法再次登录。三条业务保护（不能停用自己、不能停用
 * 最后一个可用的超级管理员、仍担任货主负责人的员工不能停用）都由后端判定并返回 40900，
 * 前端只把提示原样呈现，不自己算。
 */
export function changeUserStatus(id: string, status: UserStatus): Promise<void> {
  return http.put<void, void, { status: UserStatus }>(`/console/users/${id}/status`, { status })
}

/**
 * 重置他人密码（不校验旧密码）。
 *
 * 不能作用于调用者自己（40900），改自己请走 `PUT /auth/password`。成功后该账号的
 * 全部会话立即失效并重新标记为首次登录须改密；新密码不回显，响应无业务数据。
 */
export function resetUserPassword(id: string, newPassword: string): Promise<void> {
  return http.put<void, void, { newPassword: string }>(`/console/users/${id}/password`, {
    newPassword
  })
}

/**
 * 分配用户角色（整批替换）。
 *
 * `roleIds` 就是该用户此后的全部角色，空数组表示移除全部；省略字段会被后端拒绝，
 * 所以调用方每次都要把选中的集合完整传过来。
 */
export function assignUserRoles(id: string, roleIds: string[]): Promise<void> {
  return http.put<void, void, { roleIds: string[] }>(`/console/users/${id}/roles`, { roleIds })
}

/**
 * 踢下线：让该账号手里的全部令牌立即作废，下一次请求返回 40100。
 *
 * 不改变账号状态，被踢的人重新登录即可继续使用；要禁止登录请停用账号。允许踢自己。
 */
export function kickUserSessions(id: string): Promise<void> {
  return http.delete<void, void>(`/console/users/${id}/sessions`)
}

/** 角色状态编码：ACTIVE 启用，DISABLED 停用（停用后其权限点下一次请求起不再生效） */
export type RoleStatus = 'ACTIVE' | 'DISABLED'

/** 角色列表行，字段与后端 `RoleListItemResponse` 一一对应 */
export interface RoleBrief {
  /** 角色标识，字符串形式的十进制整数；分配角色时提交的是它 */
  id: string
  /** 角色编码，程序按它引用角色，创建后不可修改 */
  code: string
  /** 角色名称，界面展示用，可重名 */
  name: string
  /** 是否内置角色：内置角色不可编辑、不可停用、不可删除 */
  builtin: boolean
  status: RoleStatus
  /** 已分配该角色的用户数 */
  userCount: number
  /** 该角色已勾选的菜单与权限点数量 */
  permissionCount: number
  /** 备注，可为 null */
  remark: string | null
}

/** 角色列表查询参数，与 `GET /api/console/roles` 的 query 对应；本页只取全部角色，不筛选 */
export interface RoleListQuery {
  pageNo: number
  /** 每页条数取后端上限 200：下拉要把角色取全，不能只取第一页 */
  pageSize: number
}

/**
 * 分页查询角色，供「分配角色」的下拉取全量选项。
 *
 * 台账只提供分页端点，页面按最大页长逐页取完（见 `UserTab.vue` 的 `collectAllPages`），
 * 避免超过一页时下拉静默少几个选项。
 */
export function listRoles(query: RoleListQuery): Promise<PageResult<RoleBrief>> {
  return http.get<PageResult<RoleBrief>, PageResult<RoleBrief>>('/console/roles', {
    params: query
  })
}
