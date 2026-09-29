import { defineStore } from 'pinia'

export interface UserInfo {
  id: number
  username: string
  name: string
  orgId?: number
  employeeNo?: string
}

export interface Role {
  id: number
  code: string
  name: string
  dataScope?: string
}

/** dataScope 取值: ALL=不限 / WAREHOUSE=限定仓库 */
export type DataScope = 'ALL' | 'WAREHOUSE'

const SESSION_KEY = 'medistock.user.session'

interface PersistedSession {
  user: UserInfo | null
  roles: Role[]
  permissions: string[]
  warehouseIds: number[]
  dataScope: DataScope | string
}

function loadSession(): PersistedSession {
  try {
    const raw = sessionStorage.getItem(SESSION_KEY)
    if (raw) {
      const s = JSON.parse(raw)
      return {
        user: s.user || null,
        roles: s.roles || [],
        permissions: s.permissions || [],
        warehouseIds: s.warehouseIds || [],
        dataScope: s.dataScope || 'ALL',
      }
    }
  } catch { /* ignore parse error */ }
  return { user: null, roles: [], permissions: [], warehouseIds: [], dataScope: 'ALL' }
}

function saveSession(s: PersistedSession) {
  sessionStorage.setItem(SESSION_KEY, JSON.stringify(s))
}

export const useUserStore = defineStore('user', {
  state: () => {
    const s = loadSession()
    return {
      token: localStorage.getItem('token') || '',
      user: s.user as UserInfo | null,
      roles: s.roles as Role[],
      permissions: s.permissions as string[],
      dataScope: s.dataScope as string,
      warehouseIds: s.warehouseIds as number[],
    }
  },
  actions: {
    setLogin(token: string, user: UserInfo) {
      this.token = token
      this.user = user
      localStorage.setItem('token', token)
      // 登录瞬间只知 token + 用户基本信息, 权限信息由后续 setUserInfo 补齐
      saveSession({
        user,
        roles: this.roles,
        permissions: this.permissions,
        warehouseIds: this.warehouseIds,
        dataScope: this.dataScope,
      })
    },
    setUserInfo(payload: {
      user: UserInfo
      roles?: Role[]
      permissions: string[]
      dataScope?: string
      warehouseIds: number[]
    }) {
      this.user = payload.user
      this.roles = payload.roles || []
      this.permissions = payload.permissions
      this.dataScope = payload.dataScope || 'ALL'
      this.warehouseIds = payload.warehouseIds
      saveSession({
        user: this.user!,
        roles: this.roles,
        permissions: this.permissions,
        warehouseIds: this.warehouseIds,
        dataScope: this.dataScope,
      })
    },
    logout() {
      this.token = ''
      this.user = null
      this.roles = []
      this.permissions = []
      this.warehouseIds = []
      this.dataScope = 'ALL'
      localStorage.removeItem('token')
      sessionStorage.removeItem(SESSION_KEY)
    },
    /** 是否拥有指定权限点 */
    hasPermission(code: string) {
      if (this.permissions.includes('*:*:*')) return true
      return this.permissions.includes(code)
    },
    /** 任意一个匹配即通过 */
    hasAnyPermission(codes: string[]) {
      if (!codes.length) return true
      if (this.permissions.includes('*:*:*')) return true
      return codes.some((c) => this.permissions.includes(c))
    },
    /** 全部匹配才通过 */
    hasAllPermissions(codes: string[]) {
      if (!codes.length) return true
      return codes.every((c) => this.permissions.includes(c))
    },
    /** 是否拥有指定角色码 */
    hasRole(code: string) {
      return this.roles.some((r) => r.code === code)
    },
    /** 是否超管: ADMIN 角色或通配权限 */
    isSuperAdmin() {
      return this.hasRole('ADMIN') || this.permissions.includes('*:*:*')
    },
  },
})