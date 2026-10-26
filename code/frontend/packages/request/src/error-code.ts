/**
 * 后端统一返回码到界面文案的映射。
 *
 * <p>**只登记「需要前端另算措辞」的码**，其余一律回退到后端 message。
 * 后端的 message 对多数情况已经是给人看的完整句子，覆盖它反而会丢信息：
 *
 * - `40000` 的 message 是具体校验结果（「密码长度需为 8 至 20 位；密码必须包含数字」），
 *   映射成「参数不合法」会让用户不知道该改什么
 * - `40101` / `40102` / `40103` 的 message 是登录失败的具体原因，登录页要照着显示
 * - `40901` / `40902` / `40903` 的 message 指明了是哪个字段冲突
 *
 * 因此本表刻意保持很短。新增条目之前先确认该码的 message 不携带细节。
 */
const MESSAGES: Record<number, string> = {
  40100: '登录已失效，请重新登录',
  40300: '没有访问该资源的权限',
  50000: '服务暂时不可用'
}

/**
 * 业务码：需先修改初始密码。
 *
 * <p>与 {@link} 40100（未登录）共用 HTTP 401，仅靠状态码无法区分。
 * 调用方在拦截器里必须优先判定本码，否则待改密用户会被当作掉线踢回登录页。
 */
export const MUST_CHANGE_PASSWORD = 40104

export function resolveErrorMessage(code: number, fallback?: string): string {
  return MESSAGES[code] ?? (fallback || `请求失败（${code}）`)
}
