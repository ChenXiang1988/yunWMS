import { http } from './client'

/** 客户类型字典编码，取自 `GET /api/console/dicts` 的 dictType */
const OWNER_TYPE_DICT = 'OWNER_TYPE'

/** 国家字典编码；国家取值是 ISO 3166-1 alpha-2，如 CN、US */
const COUNTRY_DICT = 'COUNTRY'

/** 行政区划字典编码；项的 `ext.parent` 指向所属国家的 value */
const PROVINCE_DICT = 'PROVINCE'

/**
 * 字典项，字段与后端 `DictItemResponse` 对应。
 *
 * `ext` 原样透传：省州项用它表达层级（`parent`）与是否要求邮编
 * （`postalCodeRequired`，缺失即必填，见后端 `DictLookup`），表单要用到后者，
 * 所以这里声明出来而不是丢掉。
 */
export interface DictItem {
  /** 字典项标识，字符串形式的十进制整数 */
  id: string
  /** 中文名，如「品牌方」 */
  label: string
  /** 英文名，可为 null；后端不做语言回退，界面按当前语言取值 */
  labelEn: string | null
  /** 取值，业务数据里存的就是这个值 */
  value: string
  /** 排序号，越小越靠前 */
  sortNo: number
  /** 项状态编码：ACTIVE 启用，DISABLED 停用 */
  status: string
  /** 扩展属性，没有时为 null 或省略；层级字典用 `parent` 指向父项 value */
  ext?: Record<string, unknown> | null
}

/**
 * 取某类字典的启用项。
 *
 * `enabledOnly=true` 对应「停用后不可选」：停用的项不应再出现在下拉里。
 * 存量数据仍可能引用停用项，列表按原值回退展示，见 `owner-model.ts` 的
 * `ownerTypeLabel`。
 *
 * `parentValue` 按扩展属性里的 `parent` 过滤层级（如取国家 US 的州省），
 * 省略表示不按层级筛选。
 */
export function listDictItems(dictType: string, parentValue?: string): Promise<DictItem[]> {
  return http.get<DictItem[], DictItem[]>(`/console/dicts/${dictType}/items`, {
    params: parentValue ? { enabledOnly: true, parentValue } : { enabledOnly: true }
  })
}

/** 取客户类型字典的启用项，供筛选下拉与新建/编辑表单使用 */
export function listOwnerTypes(): Promise<DictItem[]> {
  return listDictItems(OWNER_TYPE_DICT)
}

/** 取国家字典的启用项，供注册地址的国家下拉使用 */
export function listCountries(): Promise<DictItem[]> {
  return listDictItems(COUNTRY_DICT)
}

/**
 * 取某个国家的州省启用项。
 *
 * 国家换成别的时必须重新取——州省属于国家，沿用上一次的列表会让用户选到
 * 不属于该国的州省，提交后被后端以 40000 拒绝。
 */
export function listProvinces(country: string): Promise<DictItem[]> {
  return listDictItems(PROVINCE_DICT, country)
}
