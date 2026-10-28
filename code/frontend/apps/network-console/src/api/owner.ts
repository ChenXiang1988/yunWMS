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

/**
 * 货主联系人，字段与后端 `OwnerContactResponse` 一一对应。
 *
 * 不含 ownerId：它就在请求路径上，响应里再带一份会让前端出现两处可能不一致的来源。
 * `roleType` 是 CONTACT_ROLE 字典的 value，不是枚举：角色通过字典扩展，写成联合类型
 * 会让每加一个角色都得改前端类型。
 */
export interface OwnerContact {
  /** 联系人标识，编辑、删除都用它定位 */
  id: string
  /** 姓名，可以重名——它是称呼，不是标识 */
  name: string
  /** 角色取值，展示时按字典取中文标签，接口只给 value */
  roleType: string
  /** 联系电话，必填字段，不会为 null；原样返回，不做脱敏与格式化 */
  phone: string
  /** 邮箱，可为 null（未填或已清空） */
  email: string | null
  /** 是否主联系人，同一货主至多一条为 true */
  isPrimary: boolean
  /** 备注，可为 null */
  remark: string | null
}

/**
 * 联系人请求体，新增与编辑共用同一个结构。
 *
 * 编辑是全量替换：字段传 null 表示清空，省略按 null 处理，所以调用方必须提交希望保留
 * 的全部值——「设为主」只改主标记时也要带上原值，见 `contact-model.ts` 的
 * `buildPrimaryPayload`。
 */
export interface ContactPayload {
  /** 姓名，必填，最长 64 个字符；只填空白按未填处理 */
  name: string
  /** 角色，必填，取 CONTACT_ROLE 字典的启用项 */
  roleType: string
  /** 联系电话，必填，最长 32 个字符；不做格式校验 */
  phone: string
  /** 邮箱，可为空，最长 128 个字符；传 null 表示清空 */
  email: string | null
  /** 是否主联系人，缺省按 false 处理；要保留主标记必须显式传 true */
  isPrimary: boolean
  /** 备注，可为空，最长 255 个字符；传 null 表示清空 */
  remark: string | null
}

/**
 * 查询某货主的全部联系人。
 *
 * 不分页：单个货主的联系人是几条到几十条。货主不存在、已逻辑删除或不在数据范围内
 * 返回 40400，而不是空列表——空列表的意思只是「这个货主没有联系人」。
 */
export function listOwnerContacts(ownerId: string): Promise<OwnerContact[]> {
  return http.get<OwnerContact[], OwnerContact[]>(`/console/owners/${ownerId}/contacts`)
}

/**
 * 新增联系人，返回新联系人（含标识）。
 *
 * 置 `isPrimary` 为 true 时后端先把原主联系人置为非主再写入本条，两步同一事务。
 * 并发抢主可能返回 40900，前端不判错误码、以重新取回的列表为准，见 `OwnerContactTab.vue`。
 */
export function createOwnerContact(ownerId: string, payload: ContactPayload): Promise<OwnerContact> {
  return http.post<OwnerContact, OwnerContact, ContactPayload>(
    `/console/owners/${ownerId}/contacts`,
    payload
  )
}

/**
 * 编辑联系人（全量替换）。
 *
 * 成功不回带更新后的联系人：设为主会连带改掉另一行，回带单行会让人以为其余行没变，
 * 需要最新状态时重新取列表。归属不可改——请求体里没有 ownerId。
 */
export function updateOwnerContact(
  ownerId: string,
  contactId: string,
  payload: ContactPayload
): Promise<void> {
  return http.put<void, void, ContactPayload>(
    `/console/owners/${ownerId}/contacts/${contactId}`,
    payload
  )
}

/**
 * 删除联系人（后端软删除）。
 *
 * 删除会释放该货主的主联系人名额，之后可以直接把另一条设为主。重复删除返回 40400，
 * 与读取侧同一口径：已删除与不存在是同一件事。
 */
export function deleteOwnerContact(ownerId: string, contactId: string): Promise<void> {
  return http.delete<void, void>(`/console/owners/${ownerId}/contacts/${contactId}`)
}

/**
 * 货主税号，字段与后端 `OwnerTaxResponse` 一一对应。
 *
 * 不含 ownerId：它就在请求路径上。`taxType` 是 TAX_ID_TYPE 字典的 value，不是枚举：
 * 类型通过字典扩展，写成联合类型会让每加一种税制都得改前端类型。
 */
export interface OwnerTax {
  /** 税号标识，编辑、删除都用它定位 */
  id: string
  /** 国家，ISO 3166-1 alpha-2 代码，取自 COUNTRY 字典 */
  country: string
  /** 州省，取自 PROVINCE 字典；国家级税号（统一社会信用代码、VAT）未填时为 null */
  province: string | null
  /** 税号类型取值，展示时按 TAX_ID_TYPE 字典取中文标签 */
  taxType: string
  /** 税号，必填字段，不会为 null；原样返回，不做脱敏与格式化 */
  taxNumber: string
  /** 是否该货主在该国家下的默认税号，同一货主同一国家至多一条为 true */
  isDefault: boolean
}

/**
 * 税号请求体，新增与编辑共用同一个结构。
 *
 * 编辑是全量替换：`province` 传 null 表示清空，`isDefault` 省略等于提交 false，
 * 所以调用方必须提交希望保留的全部值——「设为默认」只改默认标记时也要带上原值，
 * 见 `tax-model.ts` 的 `buildSetDefaultPayload`。
 *
 * 编辑接口虽然接收 `country` 与 `taxType`，但以记录自身的值为准，传别的值既不生效
 * 也不报错；前端在编辑态把这两项置为只读，见 `OwnerTaxTab.vue`。
 */
export interface TaxPayload {
  /** 国家，必填，2 位大写字母的 ISO 3166-1 alpha-2 代码，取 COUNTRY 字典的 value */
  country: string
  /** 州省，可为空，最长 64 个字符；传 null 或整串空白表示清空 */
  province: string | null
  /** 税号类型，必填，取 TAX_ID_TYPE 字典的启用项 */
  taxType: string
  /** 税号，必填，最长 64 个字符；后端原样保存，不做格式化 */
  taxNumber: string
  /** 是否设为该国家下的默认税号，缺省按 false 处理 */
  isDefault: boolean
}

/**
 * 查询某货主的全部税号。
 *
 * 不分页：单个货主的税务登记是个位数量级。后端按国家、州省、类型升序返回，空州省的
 * 国家级税号排在每个国家末尾，前端不重排。货主不存在、已逻辑删除或不在数据范围内
 * 返回 40400，而不是空列表——空列表的意思只是「这个货主没有登记税号」。
 */
export function listOwnerTaxes(ownerId: string): Promise<OwnerTax[]> {
  return http.get<OwnerTax[], OwnerTax[]>(`/console/owners/${ownerId}/taxes`)
}

/**
 * 新增税号，返回新税号（含标识）。
 *
 * 置 `isDefault` 为 true 时后端先把该货主同国家的原默认税号置为非默认再写入本条，
 * 两步同一事务。并发抢默认可能返回 40900，前端不判错误码、以重新取回的列表为准，
 * 见 `OwnerTaxTab.vue`。返回的新行不直接并进列表：同一次调用可能改掉了另一行。
 */
export function createOwnerTax(ownerId: string, payload: TaxPayload): Promise<OwnerTax> {
  return http.post<OwnerTax, OwnerTax, TaxPayload>(`/console/owners/${ownerId}/taxes`, payload)
}

/**
 * 编辑税号（全量替换州省、税号与默认标记）。
 *
 * 国家与类型不可改：它们与归属一起构成这一行的身份，换国家或换类型应删旧登新。
 * 成功不回带更新后的税号——设默认会连带把另一行改成非默认，回带单行会让人以为
 * 其余行没变；需要最新状态时重新取列表。
 */
export function updateOwnerTax(
  ownerId: string,
  taxId: string,
  payload: TaxPayload
): Promise<void> {
  return http.put<void, void, TaxPayload>(`/console/owners/${ownerId}/taxes/${taxId}`, payload)
}

/**
 * 删除税号（后端软删除）。
 *
 * 删除会释放该货主在该国家下的默认名额，之后可以直接把另一条设为默认。重复删除
 * 返回 40400，与读取侧同一口径：已删除与不存在是同一件事。
 */
export function deleteOwnerTax(ownerId: string, taxId: string): Promise<void> {
  return http.delete<void, void>(`/console/owners/${ownerId}/taxes/${taxId}`)
}
