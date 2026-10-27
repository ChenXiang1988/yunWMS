import { http } from './client'

/** 客户类型字典编码，取自 `GET /api/console/dicts` 的 dictType */
const OWNER_TYPE_DICT = 'OWNER_TYPE'

/**
 * 字典项，字段与后端 `DictItemResponse` 对应。
 *
 * `ext` 原样透传且本页不用，故不声明——不为没有消费者的字段占位。
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
}

/**
 * 取客户类型字典的启用项。
 *
 * `enabledOnly=true` 对应「停用后不可选」：停用的类型不应再出现在筛选下拉里。
 * 存量货主仍可能引用停用项，列表按原值回退展示，见 `owner-model.ts` 的
 * `ownerTypeLabel`。
 */
export function listOwnerTypes(): Promise<DictItem[]> {
  return http.get<DictItem[], DictItem[]>(`/console/dicts/${OWNER_TYPE_DICT}/items`, {
    params: { enabledOnly: true }
  })
}
