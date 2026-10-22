import { ElMessage } from 'element-plus'
import { clearToken, createHttp, getToken } from '@yunwms/request'
import { router } from '@/router'

export const http = createHttp({
  baseURL: import.meta.env.VITE_API_BASE_URL || '/api',
  getToken,
  // UI 库耦合收敛在应用层，迁移 antd 时只改这一行
  onError: (content) => ElMessage.error(content),
  onUnauthorized: () => {
    clearToken()
    router.replace({ name: 'login' })
  }
})
