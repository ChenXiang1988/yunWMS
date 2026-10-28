import type { ContactPayload, OwnerContact } from '@/api/owner'

/**
 * 联系人的纯转换：角色标签回退、表单回填与归一、主联系人相关的判定与请求体。
 *
 * 这里不 import 请求层——`http` 依赖 `import.meta.env` 与浏览器环境，一旦引入就
 * 无法用 `node:test` 直接跑。函数只做无副作用的转换，发请求留在 `OwnerContactTab.vue`。
 */

/** 角色字典编码，取自 `GET /api/console/dicts` 的 dictType */
export const CONTACT_ROLE_DICT = 'CONTACT_ROLE'

/**
 * 字段长度上限，与后端列宽一致：姓名 64、电话 32、邮箱 128、备注 255 个字符。
 *
 * 角色没有长度规则：它的合法取值来自字典，前端不写「值是否属于字典」的校验，
 * 下拉选项本身就是字典项。
 */
export const CONTACT_NAME_MAX = 64
export const CONTACT_PHONE_MAX = 32
export const CONTACT_EMAIL_MAX = 128
export const CONTACT_REMARK_MAX = 255

/**
 * 表单值：文本字段一律用字符串，未填是空串而不是 null。
 *
 * 原因在控件：antd 的受控输入把 `undefined`/`null` 当「非受控」，清空后回填会失灵。
 * 空串转 null 只发生在提交那一刻（`buildContactPayload`）。
 */
export interface ContactFormValues {
  name: string
  roleType: string
  phone: string
  email: string
  /**
   * 是否主联系人。形参允许 undefined：Checkbox 未勾选时可能写入 undefined，
   * 一并当成「不是主联系人」。
   */
  isPrimary?: boolean
  remark: string
}

/** 新增联系人的初始值：全空，且不是主联系人——主标记要用户主动勾选 */
export function emptyContactForm(): ContactFormValues {
  return {
    name: '',
    roleType: '',
    phone: '',
    email: '',
    isPrimary: false,
    remark: ''
  }
}

/** 把列表行回填成表单值；后端返回的 null 一律显示为空串 */
export function contactFormFrom(contact: OwnerContact): ContactFormValues {
  return {
    name: contact.name,
    roleType: contact.roleType,
    phone: contact.phone,
    email: contact.email ?? '',
    isPrimary: contact.isPrimary,
    remark: contact.remark ?? ''
  }
}

/**
 * 构造请求体：新增与编辑共用，编辑是全量替换，所以五个可编辑字段都要给全。
 *
 * 必填三项去两端空白后保留——后端「只填空白按未填处理」，空白该由表单校验拦下，
 * 而不是发一个必然被拒的请求。邮箱与备注未填或纯空白转 null（后端以 null 表示清空）。
 */
export function buildContactPayload(form: ContactFormValues): ContactPayload {
  return {
    name: form.name.trim(),
    roleType: form.roleType.trim(),
    phone: form.phone.trim(),
    email: blankToNull(form.email),
    isPrimary: form.isPrimary === true,
    remark: blankToNull(form.remark)
  }
}

/**
 * 角色标签：优先取字典 label，字典里没有该值时回退原值。
 *
 * 回退而非留空，是因为存量联系人可能引用已停用的角色项——停用后下拉不再提供该选项，
 * 但列表仍要能看出这一行是什么角色。整串为空白时由调用方套 `displayText` 显示占位符。
 *
 * 列名用「角色」不用 `ui-structure.md` 写的「职务」：接口字段是 `roleType`，
 * 字典是 CONTACT_ROLE，「职务」在该契约里没有对应字段。
 */
export function contactRoleLabel(roleType: string, labels: ReadonlyMap<string, string>): string {
  return labels.get(roleType) ?? roleType
}

/**
 * 是否可以「设为主」：已经是主联系人的行不再给入口。
 *
 * 主标记的唯一性由后端保证（置 true 时先清旧主再写入，同事务），前端不自己算谁是主、
 * 也不去取消别人——这里只决定这一行要不要露出「设为主」。
 */
export function canPromoteToPrimary(contact: OwnerContact): boolean {
  return !contact.isPrimary
}

/**
 * 「设为主」的请求体：取该行原值，只把主标记改成 true。
 *
 * 编辑接口是全量替换，只提交 `{ isPrimary: true }` 会把邮箱与备注清空，所以必须带上
 * 原值原样提交。主标记不在这里清掉别人：置 true 时后端自己先清旧主。
 */
export function buildPrimaryPayload(contact: OwnerContact): ContactPayload {
  return {
    name: contact.name,
    roleType: contact.roleType,
    phone: contact.phone,
    email: contact.email,
    isPrimary: true,
    remark: contact.remark
  }
}

/**
 * 可选文本已填则去空白保留，未填或纯空白转 null。
 *
 * 与 `owner-form.ts` 的 `optionalText` 同一口径，但两边都不导出私有函数，故各自留一份；
 * 出现第三处时提到共享模块。
 */
function blankToNull(value: string | null | undefined): string | null {
  const text = (value ?? '').trim()
  return text ? text : null
}
