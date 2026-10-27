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

/**
 * 货主详情，字段与后端 `OwnerDetailResponse` 一一对应。
 *
 * 前六个字段与列表行同名同义，故直接继承 `OwnerListItem`，不重复声明：两处各写
 * 一遍时，改一处忘一处就会让列表与详情对不上同一个字段。
 *
 * 不含联系人 / 税号 / 主账号：后端本批不返回，前端也不造占位字段。
 */
export interface OwnerDetail extends OwnerListItem {
  /** 英文名称，可为 null */
  nameEn: string | null
  /** 货主简称，可为 null；为空时展示层取名称前 8 字 */
  shortName: string | null
  /** 外部系统编码，可为 null；未删除的货主之间唯一 */
  externalCode: string | null
  /** SKU 前缀原文，只读：它是已生成 SKUID 的前四位，建档后不可修改 */
  skuPrefix: string
  /** 结算主体，开票与对账的抬头 */
  settlementEntity: string
  /** 注册国家，ISO 3166-1 alpha-2 代码 */
  country: string
  /** 州省，国家为 US 时必有值，其余国家可为 null */
  province: string | null
  city: string
  /** 区县，可为 null */
  district: string | null
  /** 邮政编码，无邮编地区为 null */
  postalCode: string | null
  addressLine: string
  /** 备注，可为 null */
  remark: string | null
  /** 创建时刻，ISO 8601 带时区（UTC） */
  createdAt: string
}

/**
 * 新建时只接受两种初始状态。
 *
 * POTENTIAL / SUSPENDED / TERMINATED 只能由状态流转产生，建档时传入后端会返回 40000，
 * 所以类型上就收窄到这两项，而不是先给全五态再靠注释提醒。
 */
export type OwnerInitialStatus = 'DRAFT' | 'ACTIVE'

/** 新建货主请求体，字段与后端 `OwnerCreateRequest` 一一对应 */
export interface CreateOwnerPayload {
  /** 客户名称，必填，最长 100 字符 */
  name: string
  /** 英文名称，最长 100 字符；传 null 表示不填 */
  nameEn: string | null
  /** 货主简称，最长 20 字符；传 null 表示不填 */
  shortName: string | null
  /** 外部系统编码，最长 64 字符；被占用返回 40906 */
  externalCode: string | null
  /** SKU 前缀：4 位大写字母或数字，不含 0、O、1、I；被占用返回 40905 */
  skuPrefix: string
  /** 客户类型，取自 OWNER_TYPE 字典的启用项 */
  ownerType: string
  /** 结算主体，必填，最长 100 字符 */
  settlementEntity: string
  /** 注册国家，取自 COUNTRY 字典的启用项 */
  country: string
  /** 州省，不为空时必须属于所选国家；国家为 US 时必填 */
  province: string | null
  city: string
  /** 区县，最长 64 字符 */
  district: string | null
  /** 邮政编码；无邮编地区（港澳台）可为 null，其余地区必填 */
  postalCode: string | null
  /** 详细地址，必填，最长 255 字符 */
  addressLine: string
  /** 备注，最长 500 字符 */
  remark: string | null
  /** 初始状态：DRAFT 保存草稿，ACTIVE 直接提交 */
  initialStatus: OwnerInitialStatus
}

/**
 * 编辑货主请求体，字段与后端 `OwnerUpdateRequest` 一一对应。
 *
 * 没有 `code`、`skuPrefix`、`status`：这三项不在可编辑范围内，多传会被后端拒绝，
 * 所以类型里也不出现。全量替换语义——调用方必须提交自己希望保留的全部可编辑字段。
 */
export interface UpdateOwnerPayload {
  name: string
  nameEn: string | null
  shortName: string | null
  externalCode: string | null
  ownerType: string
  settlementEntity: string
  country: string
  province: string | null
  city: string
  district: string | null
  postalCode: string | null
  addressLine: string
  remark: string | null
}

/** 终止影响清单，字段与后端 `OwnerTerminationImpactResponse` 一致 */
export interface TerminationImpact {
  /** 该货主下仍存在的商品数，排除已删除的商品；没有时为 0，不为 null */
  skuCount: number
  /** 该货主下仍存在的账号数，停用的账号也计入；没有时为 0，不为 null */
  accountCount: number
}

/** 状态流转请求体，字段与后端 `OwnerStatusChangeRequest` 一致 */
export interface OwnerStatusChangePayload {
  /** 目标状态；与当前相同的状态按成功处理（幂等） */
  targetStatus: OwnerStatus
  /**
   * 是否已看过终止影响清单并确认。
   *
   * 只在目标状态为 TERMINATED 时生效：未传或 false 时后端返回 40900 并带商品数与
   * 账号数。其余目标状态忽略本字段，故不需要时直接省略。
   */
  acknowledgedImpact?: boolean
}

/** 新建货主的响应，字段与后端 `CreatedOwnerResponse` 一致 */
export interface CreatedOwner {
  /** 新货主标识，后续详情、编辑都用它定位 */
  id: string
  /** 系统编码，由编码规则生成；撞号返回 40904，重新提交即可 */
  code: string
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

/**
 * 新建货主。
 *
 * 系统编码由后端取号引擎生成，请求体里没有它；返回体里才有 `id` 与 `code`。
 * 拦截器已统一提示并 reject，调用方只需 catch。
 */
export function createOwner(payload: CreateOwnerPayload): Promise<CreatedOwner> {
  return http.post<CreatedOwner, CreatedOwner, CreateOwnerPayload>('/console/owners', payload)
}

/** 查询货主详情；不存在或已逻辑删除返回 40400，由拦截器统一提示 */
export function getOwner(id: string): Promise<OwnerDetail> {
  return http.get<OwnerDetail, OwnerDetail>(`/console/owners/${id}`)
}

/**
 * 编辑货主档案。
 *
 * 全量替换可编辑字段：传 null 表示清空。成功不返回详情，需要最新值须重新取——
 * 调用方刷新详情，而不是拿本地表单值当服务端结果。
 */
export function updateOwner(id: string, payload: UpdateOwnerPayload): Promise<void> {
  return http.put<void, void, UpdateOwnerPayload>(`/console/owners/${id}`, payload)
}

/**
 * 流转货主状态。
 *
 * 当前状态由后端读库决定，请求体不带它。目标为 TERMINATED 时必须带
 * `acknowledgedImpact: true`，否则后端返回 40900 并在提示里给出影响数量。
 */
export function changeOwnerStatus(
  id: string,
  payload: OwnerStatusChangePayload
): Promise<void> {
  return http.put<void, void, OwnerStatusChangePayload>(`/console/owners/${id}/status`, payload)
}

/** 取终止影响清单：该货主下仍存在的商品数与账号数，供终止前的确认弹窗使用 */
export function getTerminationImpact(id: string): Promise<TerminationImpact> {
  return http.get<TerminationImpact, TerminationImpact>(`/console/owners/${id}/termination-impact`)
}

/**
 * 删除草稿货主（后端软删除）。
 *
 * 只有 DRAFT 可以删除，其他状态返回 40900；删除不释放系统编码与 SKU 前缀。
 * 幂等：重复删除返回 40400，与查详情同一口径。
 */
export function deleteOwner(id: string): Promise<void> {
  return http.delete<void, void>(`/console/owners/${id}`)
}
