import type {
  CreateOwnerPayload,
  OwnerDetail,
  OwnerInitialStatus,
  UpdateOwnerPayload
} from '@/api/owner'

/**
 * 货主表单模型：表单值、详情回填、SKU 前缀与邮编判定、请求体构造。
 *
 * 这里不 import 请求层——`http` 依赖 `import.meta.env` 与浏览器环境，一旦引入就
 * 无法用 `node:test` 直接跑。函数只做无副作用的转换。
 */

/**
 * 表单值：全部用字符串，未填是空串而不是 null。
 *
 * 原因在控件：antd 的受控输入把 `undefined`/`null` 当「非受控」，清空后回填会失灵。
 * 转换只发生在提交那一刻（`buildCreatePayload` / `buildUpdatePayload`），
 * 由一处负责把空串转成后端要的 null。
 */
export interface OwnerFormValues {
  name: string
  nameEn: string
  shortName: string
  externalCode: string
  skuPrefix: string
  ownerType: string
  settlementEntity: string
  country: string
  province: string
  city: string
  district: string
  postalCode: string
  addressLine: string
  remark: string
}

/** 新建表单的初始值：只读字段之外全部为空 */
export function emptyOwnerForm(): OwnerFormValues {
  return {
    name: '',
    nameEn: '',
    shortName: '',
    externalCode: '',
    skuPrefix: '',
    ownerType: '',
    settlementEntity: '',
    country: '',
    province: '',
    city: '',
    district: '',
    postalCode: '',
    addressLine: '',
    remark: ''
  }
}

/** 把详情回填成表单值；后端返回的 null 一律显示为空串 */
export function ownerFormFromDetail(detail: OwnerDetail): OwnerFormValues {
  return {
    name: detail.name,
    nameEn: detail.nameEn ?? '',
    shortName: detail.shortName ?? '',
    externalCode: detail.externalCode ?? '',
    skuPrefix: detail.skuPrefix,
    ownerType: detail.ownerType,
    settlementEntity: detail.settlementEntity,
    country: detail.country,
    province: detail.province ?? '',
    city: detail.city,
    district: detail.district ?? '',
    postalCode: detail.postalCode ?? '',
    addressLine: detail.addressLine,
    remark: detail.remark ?? ''
  }
}

/**
 * SKU 前缀格式提示，与后端 `SkuPrefix.FORMAT_HINT` 同文。
 *
 * 规则真源在后端领域类型 `SkuPrefix`，前端只做同样的提示，不另立口径：
 * 正则照抄 `^[A-HJ-NP-Z2-9]{4}$`，也不做大小写转换或去空白——后端明确拒绝
 * 自动替换，前端静默改值会让用户以为库里存的是自己填的那个字符。
 */
export const SKU_PREFIX_HINT = 'SKU 前缀只能是 4 位大写字母或数字，且不能包含 0、O、1、I'

/** 与后端 `SkuPrefix` 的正则一致 */
export const SKU_PREFIX_PATTERN = /^[A-HJ-NP-Z2-9]{4}$/

/** 省州下拉项，带出该地区是否要求邮编 */
export interface OwnerProvinceOption {
  value: string
  label: string
  /** 是否要求邮编；后端缺失 `postalCodeRequired` 时按必填处理，这里已归一化为布尔 */
  postalCodeRequired: boolean
}

/**
 * 字典项转省州下拉项。
 *
 * 邮编是否必填的判据在字典项的 `ext.postalCodeRequired`（港澳台为 false），
 * 缺失即必填——与后端 `DictLookup.isPostalCodeRequired` 同一口径，前端不写死
 * 「港澳台」地区清单，否则新增豁免地区时前端会拦下本该通过的提交。
 */
export function toProvinceOption(item: {
  value: string
  label: string
  ext?: Record<string, unknown> | null
}): OwnerProvinceOption {
  return {
    value: item.value,
    label: item.label,
    postalCodeRequired: item.ext?.postalCodeRequired !== false
  }
}

/**
 * 该省州是否要求邮编。
 *
 * 未选州省、或字典里没有该项时返回 true：后端对空省州同样问 `isPostalCodeRequired`，
 * 而它对空值与未知项都回必填。
 */
export function isPostalCodeRequired(
  province: string | null | undefined,
  provinces: ReadonlyArray<OwnerProvinceOption>
): boolean {
  if (!province) {
    return true
  }
  const matched = provinces.find((item) => item.value === province)
  return matched ? matched.postalCodeRequired : true
}

/**
 * 构造编辑请求体：只有可编辑字段，空串转 null（全量替换语义，null 表示清空）。
 *
 * 不含 `code`、`skuPrefix`、`status`——后端编辑接口不接受这三个字段，多传会被拒绝。
 * 必填字段保留原文（去两端空白），不转 null：空值该由表单校验拦下，而不是发一个
 * 必然被后端判为「必填缺失」的请求。
 */
export function buildUpdatePayload(form: OwnerFormValues): UpdateOwnerPayload {
  return {
    name: form.name.trim(),
    nameEn: optionalText(form.nameEn),
    shortName: optionalText(form.shortName),
    externalCode: optionalText(form.externalCode),
    ownerType: form.ownerType.trim(),
    settlementEntity: form.settlementEntity.trim(),
    country: form.country.trim(),
    province: optionalText(form.province),
    city: form.city.trim(),
    district: optionalText(form.district),
    postalCode: optionalText(form.postalCode),
    addressLine: form.addressLine.trim(),
    remark: optionalText(form.remark)
  }
}

/**
 * 构造新建请求体：可编辑字段加 SKU 前缀与初始状态。
 *
 * SKU 前缀只在建档时提交，编辑接口没有它；初始状态决定这次是保存草稿还是直接提交。
 */
export function buildCreatePayload(
  form: OwnerFormValues,
  initialStatus: OwnerInitialStatus
): CreateOwnerPayload {
  return {
    ...buildUpdatePayload(form),
    skuPrefix: form.skuPrefix.trim(),
    initialStatus
  }
}

/**
 * 可选文本已填则去空白保留，未填或纯空白转 null（后端以 null 表示清空）。
 *
 * 形参允许 undefined：antd 的下拉清空后写入的是 undefined 而不是空串，
 * 这里一并当成「未填」，不必让每个调用点先做一次兜底。
 */
function optionalText(value: string | null | undefined): string | null {
  const text = (value ?? '').trim()
  return text ? text : null
}
