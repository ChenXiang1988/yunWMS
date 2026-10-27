import type { OwnerInitialStatus, OwnerStatus } from '@/api/owner'

/**
 * 货主生命周期：五态文案、状态流转表、可写与可删除判定。
 *
 * 规则真源在后端 `OwnerStatus`：本文件只让用户看不到注定被拒绝的入口，
 * 不构成权限或规则的第二真源。
 */

/** 五态文案：键与后端 `OwnerStatus` 一一对应，值为界面展示词 */
export const OWNER_STATUS_TEXT: Record<OwnerStatus, string> = {
  DRAFT: '草稿',
  POTENTIAL: '潜在',
  ACTIVE: '合作中',
  SUSPENDED: '暂停',
  TERMINATED: '终止'
}

/** 状态筛选选项，顺序与状态流转方向一致（草稿 → 潜在 → 合作中 → 暂停 → 终止） */
export const OWNER_STATUS_OPTIONS: ReadonlyArray<{ value: OwnerStatus; label: string }> = [
  { value: 'DRAFT', label: OWNER_STATUS_TEXT.DRAFT },
  { value: 'POTENTIAL', label: OWNER_STATUS_TEXT.POTENTIAL },
  { value: 'ACTIVE', label: OWNER_STATUS_TEXT.ACTIVE },
  { value: 'SUSPENDED', label: OWNER_STATUS_TEXT.SUSPENDED },
  { value: 'TERMINATED', label: OWNER_STATUS_TEXT.TERMINATED }
]

/**
 * 货主状态文案。
 *
 * 未知状态回退原值：后端将来新增状态时，界面显示原文总好过显示空白或
 * `undefined`——原文至少能和接口日志对上。
 */
export function ownerStatusText(status: string): string {
  return OWNER_STATUS_TEXT[status as OwnerStatus] ?? status
}

/** 草稿态：唯一允许删除的状态 */
export const DRAFT_STATUS: OwnerStatus = 'DRAFT'

/** 终止态：终态，写操作一律置灰 */
export const TERMINATED_STATUS: OwnerStatus = 'TERMINATED'

/**
 * 状态流转表，与后端 `OwnerStatus` 的允许流向一一对应。
 *
 * 终止为终态，故目标为空数组。前端据此决定弹出哪些目标，后端仍做最终校验——
 * 表只是让用户看不到注定被 40900 拒绝的选项，不是权限或规则的第二真源。
 */
export const OWNER_STATUS_TRANSITIONS: Readonly<Record<OwnerStatus, readonly OwnerStatus[]>> = {
  DRAFT: ['POTENTIAL', 'ACTIVE'],
  POTENTIAL: ['ACTIVE', 'TERMINATED'],
  ACTIVE: ['SUSPENDED', 'TERMINATED'],
  SUSPENDED: ['ACTIVE', 'TERMINATED'],
  TERMINATED: []
}

/**
 * 当前状态允许流转到的目标状态。
 *
 * 同状态不列出：后端对「提交与当前相同的状态」按幂等成功处理，但那只是一个
 * 安全网，不该出现在选项里让用户点。
 */
export function allowedStatusTargets(status: OwnerStatus): OwnerStatus[] {
  return [...(OWNER_STATUS_TRANSITIONS[status] ?? [])]
}

/**
 * 是否可删除：只有草稿。
 *
 * 已提交的货主应先走状态流转（终止），删除不是「停止合作」的替代品——后端对
 * 非草稿返回 40900，这里提前把入口藏掉。
 */
export function canDeleteOwner(status: string): boolean {
  return status === DRAFT_STATUS
}

/** 是否可写（编辑、状态流转、删除）：终止为终态，终止态货主仍出现在列表里但按钮置灰 */
export function isOwnerWritable(status: string): boolean {
  return status !== TERMINATED_STATUS
}

/** 新建时可选的初始状态，与后端 `initialStatus` 接受的取值一致 */
export const OWNER_INITIAL_STATUS_OPTIONS: ReadonlyArray<{
  value: OwnerInitialStatus
  label: string
}> = [
  { value: 'DRAFT', label: '保存草稿' },
  { value: 'ACTIVE', label: '直接提交' }
]
