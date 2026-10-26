import { createHttp, getToken } from '@yunwms/request'
import { router } from '@/router'
import { session } from '@/stores/session'
import { notifyError } from './notify'

export const http = createHttp({
  baseURL: import.meta.env.VITE_API_BASE_URL || '/api',
  getToken,
  onError: (content) => notifyError(content),
  onUnauthorized: () => {
    session.clear()
    router.replace({ name: 'login' })
  },
  onPasswordChangeRequired: () => {
    // 不清令牌：改密接口本身要用它，清了就再也改不了，只能等管理员重置。
    // 已在改密页时不再跳转，否则改密页里每来一次 40104 都会重新导航一次，形成重定向循环。
    if (router.currentRoute.value.name === 'change-password') {
      return
    }
    router.replace({ name: 'change-password' })
  }
})
