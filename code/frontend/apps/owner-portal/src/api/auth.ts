import { http } from './client'
import type { PortalId } from '@/config/portal'

/** 登录主体类型：STAFF 内部员工，OWNER 货主账号 */
export type SubjectType = 'STAFF' | 'OWNER'

/** 登录成功响应，字段与后端 `LoginResponse` 一一对应 */
export interface LoginResult {
  token: string
  subjectType: SubjectType
  /** 所属货主标识；内部员工为 null */
  ownerId: string | null
  displayName: string
  /** 为 true 时只能先进改密页，其他业务接口会返回 40104 */
  mustChangePassword: boolean
  roles: string[]
  /** 可进入的前端应用 */
  accessiblePortals: PortalId[]
}

export interface LoginPayload {
  loginName: string
  password: string
}

export interface ChangePasswordPayload {
  oldPassword: string
  newPassword: string
}

/**
 * 登录名加密码登录。
 *
 * 拦截器已把 `ApiResult` 裁成 `data`，所以返回类型直接是 `LoginResult`（第二个泛型
 * 覆盖了 axios 默认的 `AxiosResponse`）。业务失败（40101 凭据错误、40102 停用、
 * 40103 锁定、40000 参数不合法）由拦截器统一提示并 reject，调用方只需 catch 取文案。
 */
export function login(payload: LoginPayload): Promise<LoginResult> {
  return http.post<LoginResult, LoginResult, LoginPayload>('/auth/login', payload)
}

/**
 * 修改本人密码。改密只失效其他会话，当前令牌继续有效，调用方不必清令牌。
 *
 * 新密码的长度与字符组合由后端判定并一次列全违规项，前端不预判，只用后端 message。
 */
export function changePassword(payload: ChangePasswordPayload): Promise<void> {
  return http.put<void, void, ChangePasswordPayload>('/auth/password', payload)
}
