import type { OwnerListQuery, OwnerStatus } from '@/api/owner'

/**
 * 纯展示与查询模型：占位符、类型标签、时间格式与查询参数归一化。
 *
 * 状态文案与流转见 `owner-status.ts`，表单与请求体见 `owner-form.ts`。
 *
 * 这里不 import 请求层——`http` 依赖 `import.meta.env` 与浏览器环境，一旦引入就
 * 无法用 `node:test` 直接跑。数据由页面注入，函数只做无副作用的转换。
 */

/** 空值与无效值的占位符 */
export const PLACEHOLDER = '—'

/** 列表默认每页条数，取后端默认值 */
export const DEFAULT_PAGE_SIZE = 20

/** 每页条数上限，与后端一致：越界时后端直接返回 40000，前端不发越界值 */
export const MAX_PAGE_SIZE = 200

/** 筛选栏字段，与 `OwnerFilters` 的输入一一对应 */
export interface OwnerFilterValues {
  keyword?: string
  ownerType?: string
  status?: OwnerStatus
}

/** 查询输入：筛选项加可选分页，缺省由归一化补齐 */
export interface OwnerQueryInput extends OwnerFilterValues {
  pageNo?: number
  pageSize?: number
}

/**
 * 客户类型标签：优先取字典 label，字典里没有该值时回退原值。
 *
 * 回退而非留空，是因为存量货主可能引用已停用的字典项——筛选下拉不再提供该
 * 选项，但列表仍要能显示出它是什么。空值才显示占位符。
 */
export function ownerTypeLabel(ownerType: string, labels: ReadonlyMap<string, string>): string {
  if (!ownerType) {
    return PLACEHOLDER
  }
  return labels.get(ownerType) ?? ownerType
}

/**
 * 把 ISO 8601 时间格式化为本地 `YYYY-MM-DD HH:mm`。
 *
 * 无效值或缺失返回占位符——列表里出现 `Invalid Date` 或空单元格都会被当成
 * 数据问题。不用日期组件是为了让本函数能在 `node:test` 里直接断言。
 */
export function formatIsoDateTime(value: string | null | undefined): string {
  if (!value) {
    return PLACEHOLDER
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
 * 归一化查询参数。
 *
 * - 空字符串与纯空白一律去掉：后端把全空白 keyword 按不筛选处理，前端不必发；
 * - 页码至少为 1，每页条数落在 1–200 内：越界值后端会直接返回 40000，与其让
 *   用户看到报错，不如在发请求前收敛到合法区间。
 */
export function normalizeOwnerQuery(input: OwnerQueryInput = {}): OwnerListQuery {
  const query: OwnerListQuery = {
    pageNo: normalizeAtLeastOne(input.pageNo, 1),
    pageSize: Math.min(normalizeAtLeastOne(input.pageSize, DEFAULT_PAGE_SIZE), MAX_PAGE_SIZE)
  }

  const keyword = input.keyword?.trim()
  if (keyword) {
    query.keyword = keyword
  }

  const ownerType = input.ownerType?.trim()
  if (ownerType) {
    query.ownerType = ownerType
  }

  if (input.status) {
    query.status = input.status
  }

  return query
}

/** 下拉选项：字典项的最小投影，只保留渲染所需的两列。页面与表单抽屉共用，避免各写一份 */
export interface OwnerSelectOption {
  value: string
  label: string
}

/** 详情展示用：null、空串与纯空白都显示占位符 */
export function displayText(value: string | null | undefined): string {
  const text = value?.trim()
  return text ? text : PLACEHOLDER
}

/** 取整并保证不小于 1；非有限数（NaN、Infinity）回退默认值 */
function normalizeAtLeastOne(value: number | undefined, fallback: number): number {
  if (value === undefined || !Number.isFinite(value)) {
    return fallback
  }
  const integer = Math.trunc(value)
  return integer >= 1 ? integer : fallback
}

function pad2(value: number): string {
  return String(value).padStart(2, '0')
}
