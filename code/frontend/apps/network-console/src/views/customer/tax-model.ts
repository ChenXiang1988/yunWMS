import type { OwnerTax, TaxPayload } from '@/api/owner'

/**
 * 税号的纯转换：类型标签回退、表单回填与归一、默认标记的判定与请求体。
 *
 * 这里不 import 请求层——`http` 依赖 `import.meta.env` 与浏览器环境，一旦引入就
 * 无法用 `node:test` 直接跑。函数只做无副作用的转换，发请求留在 `OwnerTaxTab.vue`。
 *
 * 业务规则一概不写在这份模型里：税号的 18 位形态、类型与国家是否匹配、州销售税号
 * 必须填州省，都由后端判。前端再写一遍就是两个真源，两处一旦漂移，会出现「前端放行、
 * 后端拒绝」或「前端拦住后端本可接受的值」。这里只有契约写死的长度与空值口径。
 */

/** 税号类型字典编码，取自 `GET /api/console/dicts` 的 dictType */
export const TAX_TYPE_DICT = 'TAX_ID_TYPE'

/**
 * 税号长度上限，与后端列宽一致：64 个字符。
 *
 * 国家（2）、州省（64）、类型（20）三项同样有列宽上限，但它们在表单里都是字典驱动的
 * 下拉——取值只可能是字典项的 value，长度由字典项本身保证，写死一个 `max` 校验是
 * 永远走不到的分支，所以只给可自由输入的税号加规则。
 */
export const TAX_NUMBER_MAX = 64

/**
 * 表单值：文本字段一律用字符串，未填是空串而不是 null。
 *
 * 原因在控件：antd 的受控输入与下拉把 `undefined`/`null` 当「非受控」，清空后回填会
 * 失灵。空串转 null 只发生在提交那一刻（`buildTaxPayload`）。
 */
export interface TaxFormValues {
  /** 国家，编辑态只读：后端以记录自身的国家为准，这里带上原值只为凑齐请求体 */
  country: string
  /** 州省，可为空；国家级税号通常留空 */
  province: string
  /** 税号类型，编辑态只读，理由同国家 */
  taxType: string
  taxNumber: string
  /**
   * 是否该国家下的默认税号。形参允许 undefined：Checkbox 未勾选时可能写入 undefined，
   * 一并当成「不是默认」。
   */
  isDefault?: boolean
}

/** 新增税号的初始值：全空，且不是默认——默认标记要用户主动勾选 */
export function emptyTaxForm(): TaxFormValues {
  return {
    country: '',
    province: '',
    taxType: '',
    taxNumber: '',
    isDefault: false
  }
}

/** 把列表行回填成表单值；后端返回的 null 一律显示为空串 */
export function taxFormFrom(tax: OwnerTax): TaxFormValues {
  return {
    country: tax.country,
    province: tax.province ?? '',
    taxType: tax.taxType,
    taxNumber: tax.taxNumber,
    isDefault: tax.isDefault
  }
}

/**
 * 构造请求体：新增与编辑共用，编辑是全量替换，所以四项可编辑字段都要给全。
 *
 * 必填三项去两端空白后保留——只填空白该由表单校验拦下，而不是发一个必然被拒的请求。
 * 州省未填或整串空白转 null（后端以 null 表示清空）。
 *
 * 税号也去两端空白：契约写明后端「按原样保存，不去首尾空格」，也就是清理责任在
 * 调用方。税号是标识，粘进来的前导空格会原样入库并成为唯一索引的一部分，事后只能
 * 删了重登，所以按 `contact-model.ts` 的同一口径在提交前收敛。
 */
export function buildTaxPayload(form: TaxFormValues): TaxPayload {
  const province = form.province.trim()
  return {
    country: form.country.trim(),
    province: province ? province : null,
    taxType: form.taxType.trim(),
    taxNumber: form.taxNumber.trim(),
    isDefault: form.isDefault === true
  }
}

/**
 * 类型标签：优先取字典 label，字典里没有该值时回退原值。
 *
 * 回退而非留空，是因为存量税号可能引用已停用的类型项——停用后下拉不再提供该选项，
 * 但列表仍要能看出这一行登记的是什么。字典整批加载失败时同样回退，不至于整列出空白。
 */
export function taxTypeLabel(taxType: string, labels: ReadonlyMap<string, string>): string {
  return labels.get(taxType) ?? taxType
}

/**
 * 是否可以「设为默认」：已经是该国默认税号的行不再给入口。
 *
 * 默认的唯一性由后端保证（置 true 时先清同国旧默认再写入，同事务），前端不自己算
 * 谁是默认、也不去取消别人——这里只决定这一行要不要露出「设为默认」。
 */
export function canSetDefault(tax: OwnerTax): boolean {
  return !tax.isDefault
}

/**
 * 「设为默认」的请求体：取该行原值，只把默认标记改成 true。
 *
 * 编辑接口是全量替换，只提交 `{ isDefault: true }` 会把州省清空、把税号写成空值，
 * 所以必须带上原值原样提交。国家与类型一并带上：编辑时后端以记录自身的值为准，
 * 带了也不会改掉身份，但全量替换的请求体缺字段更容易在后续改动中被误读。
 */
export function buildSetDefaultPayload(tax: OwnerTax): TaxPayload {
  return {
    country: tax.country,
    province: tax.province,
    taxType: tax.taxType,
    taxNumber: tax.taxNumber,
    isDefault: true
  }
}
