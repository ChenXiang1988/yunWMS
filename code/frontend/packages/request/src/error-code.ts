/**
 * 后端统一返回码到界面文案的映射。
 * 未登记的码回退到后端 message，避免界面出现「未知错误」。
 */
const MESSAGES: Record<number, string> = {
  1001: '参数不合法',
  1002: '缺少必填字段',
  2001: '登录信息已失效，请重新登录',
  2002: '没有访问该资源的权限',
  2003: '账号已被锁定，请稍后再试',
  3001: '数据不存在或已被删除',
  3002: '数据已存在，请勿重复提交',
  4001: '导入文件解析失败',
  4002: '导入行数超出上限',
  5001: '服务暂时不可用'
}

export function resolveErrorMessage(code: number, fallback?: string): string {
  return MESSAGES[code] ?? (fallback || `请求失败（${code}）`)
}
