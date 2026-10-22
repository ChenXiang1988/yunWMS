import axios, { AxiosError, type AxiosInstance, type InternalAxiosRequestConfig } from 'axios'
import { resolveErrorMessage } from './error-code'

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
      if (status === 401) {
        options.onUnauthorized()
        notifyError('登录已过期，请重新登录')
      } else if (status === 403) {
        options.onForbidden?.()
        notifyError('没有访问该资源的权限')
      } else {
        notifyError(error.response?.data?.message || error.message || '网络异常，请稍后重试')
      }
      return Promise.reject(error)
    }
  )

  return instance
}
