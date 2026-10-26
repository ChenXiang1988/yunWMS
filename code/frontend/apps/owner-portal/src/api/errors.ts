/**
 * 把请求层抛出的错误还原成一句能展示给用户的中文。
 *
 * 拦截器对业务失败 reject 的是 `new Error(后端 message)`，后端 message 本来就是中文，
 * 直接透出即可（例如「密码长度需为 8 至 20 位；密码必须包含数字」，一次列全违规项）。
 * Axios 自己生成的 message 则是英文——「Network Error」「Request failed with status
 * code 500」——这类不能直接摆到界面上，落到调用方给的中文兜底文案。
 *
 * 拦截器已经弹过一次提示，本函数只负责行内文案，不再触发任何提示。
 */
export function resolveFailureMessage(error: unknown, fallback: string): string {
  const bodyMessage = readResponseMessage(error)
  if (bodyMessage) {
    return bodyMessage
  }
  if (isAxiosFailure(error)) {
    return fallback
  }
  return error instanceof Error && error.message ? error.message : fallback
}

/** 后端 HTTP 错误体里的 message；没有响应体时返回 null */
function readResponseMessage(error: unknown): string | null {
  const response = readProperty(error, 'response')
  const data = readProperty(response, 'data')
  const message = readProperty(data, 'message')
  return typeof message === 'string' && message.trim() !== '' ? message : null
}

/**
 * 是否 Axios 自己构造的错误。
 *
 * AxiosError 带实例属性 `isAxiosError`，拦截器改写后的普通 Error 没有这个标记，
 * 两者就此区分开，不必靠「message 里是不是英文」这类猜测。
 */
function isAxiosFailure(error: unknown): boolean {
  return readProperty(error, 'isAxiosError') === true
}

function readProperty(source: unknown, key: string): unknown {
  if (typeof source !== 'object' || source === null) {
    return undefined
  }
  return (source as Record<string, unknown>)[key]
}
