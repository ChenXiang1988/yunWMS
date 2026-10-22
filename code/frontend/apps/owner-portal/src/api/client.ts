import { clearToken, createHttp, getToken } from '@yunwms/request'
import { router } from '@/router'

type HttpErrorHandler = (content: string) => void

let notifyError: HttpErrorHandler = (content) => {
  // 注入前（例如应用尚未挂载）降级到 console，避免错误被静默吞掉
  console.error('[http]', content)
}

/**
 * 由 AppBootstrap 在 <App> 子树内注入 antd 的 message 实例。
 * 不用静态 message.error：它的输出不消费 ConfigProvider 的主题。
 */
export function setHttpErrorHandler(handler: HttpErrorHandler): void {
  notifyError = handler
}

export const http = createHttp({
  baseURL: import.meta.env.VITE_API_BASE_URL || '/api',
  getToken,
  onError: (content) => notifyError(content),
  onUnauthorized: () => {
    clearToken()
    router.replace({ name: 'login' })
  }
})
