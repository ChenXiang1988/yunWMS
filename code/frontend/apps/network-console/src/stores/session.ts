import { reactive } from 'vue'
import { clearToken, getToken, setToken } from '@yunwms/request'
import type { LoginResult } from '@/api/auth'
import { PORTAL_IDS, type PortalId } from '@/config/portal'

/**
 * 会话态：登录后各页面读它，路由守卫也读它。
 *
 * 令牌只有一处权威来源，就是 `@yunwms/request` 的 getToken / setToken / clearToken。
 * 这里不再另存一份：拦截器遇到 401 会直接清掉请求层的令牌，如果会话里还留着一个
 * 旧副本，刷新前看上去像「没掉线」，请求却全会失败。因此 `token` 是只读转发。
 *
 * 其余字段写进 sessionStorage：刷新不能让守卫忘记「待改密」，否则刷新即绕过强制改密；
 * 但也不必跨标签页长期留存，所以不用 localStorage。
 */
const STORAGE_KEY = 'yunwms.console.session'

interface PersistedSession {
  displayName: string
  mustChangePassword: boolean
  portals: PortalId[]
}

const state = reactive<PersistedSession>(read())

export const session = {
  get token(): string | null {
    return getToken()
  },

  get displayName(): string {
    return state.displayName
  },

  get mustChangePassword(): boolean {
    return state.mustChangePassword
  },

  get portals(): readonly PortalId[] {
    return state.portals
  },

  /** 登录成功后保存会话；端校验由调用方在调用本方法之前完成 */
  save(result: LoginResult): void {
    setToken(result.token)
    state.displayName = result.displayName
    state.mustChangePassword = result.mustChangePassword
    state.portals = [...result.accessiblePortals]
    persist()
  },

  /** 退出登录或令牌失效时清空；令牌与本地会话必须一起消失 */
  clear(): void {
    clearToken()
    state.displayName = ''
    state.mustChangePassword = false
    state.portals = []
    drop()
  },

  /** 改密成功：只解除待改密标记，令牌仍然有效，不跳登录页 */
  markPasswordChanged(): void {
    state.mustChangePassword = false
    persist()
  }
}

function read(): PersistedSession {
  const empty: PersistedSession = { displayName: '', mustChangePassword: false, portals: [] }
  let raw: string | null
  try {
    raw = sessionStorage.getItem(STORAGE_KEY)
  } catch (error) {
    // 部分浏览器的隐私模式禁止读存储；退化为纯内存会话，功能可用，只是刷新后要重新登录
    console.warn('[session] 读取会话缓存失败，本次会话仅存于内存', error)
    return empty
  }
  if (!raw) {
    return empty
  }
  try {
    const parsed = JSON.parse(raw) as Partial<PersistedSession>
    return {
      displayName: typeof parsed.displayName === 'string' ? parsed.displayName : '',
      mustChangePassword: parsed.mustChangePassword === true,
      portals: Array.isArray(parsed.portals) ? parsed.portals.filter(isPortalId) : []
    }
  } catch (error) {
    // 缓存坏了不该让应用起不来：丢掉重建，但要留痕，否则下次排查无据可查
    console.warn('[session] 会话缓存无法解析，已重置', error)
    drop()
    return empty
  }
}

function persist(): void {
  const snapshot: PersistedSession = {
    displayName: state.displayName,
    mustChangePassword: state.mustChangePassword,
    portals: [...state.portals]
  }
  try {
    sessionStorage.setItem(STORAGE_KEY, JSON.stringify(snapshot))
  } catch (error) {
    // 写失败只影响刷新后的体验：待改密标记会丢，用户下一次业务请求仍会被 40104 送回改密页
    console.warn('[session] 写入会话缓存失败，刷新后需重新登录', error)
  }
}

function drop(): void {
  try {
    sessionStorage.removeItem(STORAGE_KEY)
  } catch (error) {
    console.warn('[session] 清除会话缓存失败', error)
  }
}

function isPortalId(value: unknown): value is PortalId {
  return typeof value === 'string' && (PORTAL_IDS as readonly string[]).includes(value)
}
