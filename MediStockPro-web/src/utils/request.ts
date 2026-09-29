import axios, { type AxiosRequestConfig } from 'axios'
import { Toast } from '@kousum/semi-ui-vue'
import { useUserStore } from '@/stores/user'
import router from '@/router'

/**
 * Axios 实例: 统一注入 token / 解包 Result / 401 跳登录
 * 写操作由调用方传入 Idempotency-Key (P0 幂等)
 */
// 浏览器开发态走 vite 代理 (/api/v1); Electron 打包态(file://)无代理, 回退本机后端绝对地址
const fallbackBase = location.protocol === 'file:' ? 'http://localhost:8081/api/v1' : '/api/v1'
const request = axios.create({ baseURL: import.meta.env.VITE_API_BASE || fallbackBase, timeout: 15000 })

request.interceptors.request.use((config) => {
  const userStore = useUserStore()
  if (userStore.token) {
    config.headers.Authorization = `Bearer ${userStore.token}`
  }
  return config
})

request.interceptors.response.use(
  (response) => {
    if (response.config.responseType === 'blob') return response.data
    const body = response.data
    if (body?.code === 'SUCCESS') return body.data
    Toast.error(body?.message || '请求失败')
    return Promise.reject(new Error(body?.message || '请求失败'))
  },
  (error) => {
    if (error.response?.status === 401 || error.response?.data?.code === 'COMMON_401') {
      const userStore = useUserStore()
      userStore.logout()
      router.push({ name: 'login' })
    }
    Toast.error(error.response?.data?.message || error.message || '网络异常')
    return Promise.reject(error)
  }
)

export function newIdempotencyKey(): string {
  return crypto.randomUUID()
}

export default request as {
  get<T = any>(url: string, config?: AxiosRequestConfig): Promise<T>
  post<T = any>(url: string, data?: any, config?: AxiosRequestConfig): Promise<T>
  put<T = any>(url: string, data?: any, config?: AxiosRequestConfig): Promise<T>
  delete<T = any>(url: string, config?: AxiosRequestConfig): Promise<T>
}
