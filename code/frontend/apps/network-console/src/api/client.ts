import { clearToken, createHttp, getToken } from '@yunwms/request'
import { router } from '@/router'
import { notifyError } from './notify'

export const http = createHttp({
  baseURL: import.meta.env.VITE_API_BASE_URL || '/api',
  getToken,
  onError: (content) => notifyError(content),
  onUnauthorized: () => {
    clearToken()
    router.replace({ name: 'login' })
  }
})
