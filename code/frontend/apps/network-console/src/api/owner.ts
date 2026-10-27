import { http } from './client'

/**
 * 货主状态：与后端 `OwnerStatus` 五态一一对应。
 *
 * 只列后端声明过的值——后端对未知状态返回 40000，前端不自行扩展。
 */
export type OwnerStatus = 'DRAFT' | 'POTENTIAL' | 'ACTIVE' | 'SUSPENDED' | 'TERMINATED'

/**
 * 货主列表行，字段与后端 `OwnerListItemResponse` 一一对应。
 *
 * 不含联系人 / 手机号：联系人由独立子表维护，列表接口不返回，也不允许前端补。
 */
export interface OwnerListItem {
  /** 货主标识，详情、编辑都用它定位 */
  id: string
  /** 系统编码，形如 OWNER0001，由编码规则生成且不可修改 */
  code: string
  /** 客户名称，可与其他货主重名 */
  name: string
  /** 客户类型，取自 OWNER_TYPE 字典的 value */
  ownerType: string
  status: OwnerStatus
  /** 最后修改时刻，ISO 8601 带时区（UTC） */
  updatedAt: string
}

/** 分页响应，字段与后端 `PageResult` 一致 */
export interface PageResult<T> {
  /** 页码，从 1 开始 */
  pageNo: number
  /** 每页条数，取值 1 至 200 */
  pageSize: number
  /** 满足筛选条件的总条数，不是本页条数 */
  total: number
  /** 本页数据，无数据时为空数组 */
  records: T[]
}

/** 货主列表查询参数，与 `GET /api/console/owners` 的 query 一一对应 */
export interface OwnerListQuery {
  pageNo: number
  pageSize: number
  /** 按系统编码或客户名称模糊匹配；省略表示不筛选 */
  keyword?: string
  /** 客户类型，按字典 value 精确匹配；省略表示不筛选 */
  ownerType?: string
  status?: OwnerStatus
}

/**
 * 分页查询货主。
 *
 * 拦截器已把 `ApiResult` 裁成 `data`，所以返回类型直接是 `PageResult`（第二个泛型
 * 覆盖了 axios 默认的 `AxiosResponse`）。业务失败（401 / 403、状态非法、分页越界
 * 返回 40000）由拦截器统一提示并 reject，调用方只需 catch。
 */
export function listOwners(query: OwnerListQuery): Promise<PageResult<OwnerListItem>> {
  return http.get<PageResult<OwnerListItem>, PageResult<OwnerListItem>>('/console/owners', {
    params: query
  })
}
