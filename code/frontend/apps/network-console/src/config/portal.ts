/**
 * 前端应用的端标识，取值与后端登录响应的 `accessiblePortals` 一一对应。
 *
 * 类型由 `PORTAL_IDS` 推导，避免常量与联合类型各写一份：后端将来增加第三个端时
 * 只改数组，判断分支出错会在编译期暴露。
 */
export const PORTAL_IDS = ['console', 'portal'] as const

export type PortalId = (typeof PORTAL_IDS)[number]

/** 本应用标识：仓网端 */
export const PORTAL: PortalId = 'console'
