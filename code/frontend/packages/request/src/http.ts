import axios, { AxiosError, type AxiosInstance, type InternalAxiosRequestConfig } from 'axios'
import { MUST_CHANGE_PASSWORD, resolveErrorMessage } from './error-code'

export interface ApiResult<T = unknown> {
  code: number
  message: string
  data: T
}

export interface HttpOptions {
  baseURL: string
  getToken: () => string | null
  onUnauthorized: () => void
  onForbidden?: () => void
  /**
   * 收到业务码 40104（需先修改初始密码）时调用。
   *
   * <p>**必须在 `onUnauthorized` 之前判定**：40104 与 40100 共用 HTTP 401，
   * 只看状态码会把「待改密」当成「登录失效」踢回登录页，用户登录成功后又被打回，
   * 形成死循环。
   *
   * <p>未注入时降级为按 `onUnauthorized` 处理——那会退回旧行为，但至少不会静默。
   */
  onPasswordChangeRequired?: () => void
  /**
   * 错误提示由调用方注入（ElMessage、antd 的 message 等），
   * 本包因此不依赖任何 UI 库，也不会产生全局副作用。
   * 未注入时降级到 console.error，避免错误被静默吞掉。
   */
  onError?: (content: string) => void
  timeout?: number
}

export function createHttp(options: HttpOptions): AxiosInstance {
  const notifyError = (content: string): void => {
    if (options.onError) {
      options.onError(content)
      return
    }
    console.error('[request]', content)
  }

  const instance = axios.create({
    baseURL: options.baseURL,
    timeout: options.timeout ?? 15000
  })

  instance.interceptors.request.use((config: InternalAxiosRequestConfig) => {
    const token = options.getToken()
    if (token) {
      config.headers.Authorization = `Bearer ${token}`
    }
    config.headers['Accept-Language'] = document.documentElement.lang || 'zh-CN'
    return config
  })

  instance.interceptors.response.use(
    (response) => {
      const result = response.data as ApiResult
      if (result && typeof result === 'object' && typeof result.code === 'number') {
        if (result.code === 0) {
          return result.data as never
        }
        notifyError(resolveErrorMessage(result.code, result.message))
        return Promise.reject(new Error(result.message))
      }
      return response.data as never
    },
    (error: AxiosError<ApiResult>) => {
      const status = error.response?.status
      const businessCode = error.response?.data?.code

      // 业务码先于状态码：40104 与 40100 都是 HTTP 401，只判状态码会把待改密
      // 当成掉线踢回登录页。这里不提示——调用方会跳改密页，弹一句「请先改密」
      // 只是噪音。
      if (businessCode === MUST_CHANGE_PASSWORD) {
        if (options.onPasswordChangeRequired) {
          options.onPasswordChangeRequired()
        } else {
          options.onUnauthorized()
        }
        return Promise.reject(error)
      }

      if (status === 401) {
        options.onUnauthorized()
        notifyError(resolveErrorMessage(40100, '登录已失效，请重新登录'))
      } else if (status === 403) {
        options.onForbidden?.()
        notifyError(resolveErrorMessage(40300, '没有访问该资源的权限'))
      } else {
        notifyError(error.response?.data?.message || error.message || '网络异常，请稍后重试')
      }
      return Promise.reject(error)
    }
  )

  return instance
}
