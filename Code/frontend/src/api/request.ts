import axios from 'axios'
import type { AxiosResponse, InternalAxiosRequestConfig } from 'axios'
import { ElMessage } from 'element-plus'

const api = axios.create({
  baseURL: '/api',
  timeout: 30000,
  headers: {
    'Content-Type': 'application/json'
  }
})

// === P1 统一 refresh：抽公共方法供 fetch 场景复用 ===
export async function doRefresh(): Promise<string | null> {
  const refreshToken = localStorage.getItem('refreshToken')
  if (!refreshToken) return null
  try {
    const res = await axios.post<any>('/api/auth/refresh', { refreshToken })
    const data = res.data?.data
    if (data?.accessToken) {
      localStorage.setItem('accessToken', data.accessToken)
      localStorage.setItem('token', data.accessToken)
      return data.accessToken
    }
  } catch (e) {
    return null
  }
  return null
}

export function clearAuthAndRedirect() {
  localStorage.removeItem('accessToken')
  localStorage.removeItem('token')
  localStorage.removeItem('refreshToken')
  if (!window.location.pathname.startsWith('/login')) {
    ElMessage.error('登录已过期，请重新登录')
    window.location.href = '/login'
  }
}

/**
 * P1 统一 fetch 封装：用于非 axios 场景（如 App.vue 顶层加载）
 * 自动注入 token + 401/403 时刷新一次重试 + 失败跳登录
 */
export async function requestFetch(
  url: string,
  options: RequestInit = {}
): Promise<Response | null> {
  const fullUrl = url.startsWith('/api') || url.startsWith('http') ? url : `/api${url}`
  const doFetch = async (token: string | null) =>
    fetch(fullUrl, {
      ...options,
      headers: {
        'Content-Type': 'application/json',
        ...(options.headers || {}),
        ...(token ? { Authorization: `Bearer ${token}` } : {})
      }
    })

  const token = localStorage.getItem('accessToken') || localStorage.getItem('token')
  let resp = await doFetch(token)
  if (resp.status === 401 || resp.status === 403) {
    const newToken = await doRefresh()
    if (newToken) {
      resp = await doFetch(newToken)
    } else {
      clearAuthAndRedirect()
      return null
    }
  }
  return resp
}

// 请求拦截器：注入 access token
api.interceptors.request.use(
  (config) => {
    const token = localStorage.getItem('accessToken') || localStorage.getItem('token')
    if (token) {
      config.headers.Authorization = `Bearer ${token}`
    }
    return config
  },
  (error) => {
    return Promise.reject(error)
  }
)

// 响应拦截器：自动 refresh + 401/403 跳登录 + 错误友好提示
let refreshing: Promise<string | null> | null = null

async function tryRefresh(): Promise<string | null> {
  // P1 统一：委托给导出的 doRefresh（保持向后兼容）
  return doRefresh()
}

api.interceptors.response.use(
  (response: AxiosResponse) => {
    // R106 E1 修复：业务码检查（识别业务异常 → reject 让 catch 块统一处理）
    // R107 F1 修复：业务异常时拦截器**不再自动弹错**——避免与 173 处 catch 块的 ElMessage.error 重复
    // 行为：业务异常 → 仅 reject（带 isBusinessError 标记），调用方 catch 块仍弹错；现有行为不变。
    // 例外：/auth/login 和 /auth/refresh 静默（isSilent），不抢弹让调用方自行判断。
    const body: any = response.data
    if (
      body && typeof body === 'object' &&
      'code' in body && 'message' in body && 'data' in body &&
      body.code !== 200
    ) {
      const message = String(body.message || '业务异常')
      // 包装 reject 错误：保留原始 body + 标记 isBusinessError，让调用方 catch 块按需弹错
      return Promise.reject({
        isBusinessError: true,
        code: body.code,
        message,
        data: body.data,
        status: response.status,
        config: response.config
      })
    }
    return response
  },
  async (error) => {
    const status = error.response?.status
    const originalConfig = error.config as InternalAxiosRequestConfig & { _retried?: boolean }

    // 401 或 403：尝试自动 refresh 一次
    if ((status === 401 || status === 403) && originalConfig && !originalConfig._retried) {
      originalConfig._retried = true

      if (!refreshing) {
        refreshing = tryRefresh().finally(() => { refreshing = null })
      }
      const newToken = await refreshing
      if (newToken) {
        originalConfig.headers = originalConfig.headers || {}
        originalConfig.headers.Authorization = `Bearer ${newToken}`
        return api.request(originalConfig)
      }

      // refresh 失败 → 清空 + 跳登录（P1 统一委托给 clearAuthAndRedirect）
      clearAuthAndRedirect()
    }

    // 5xx 等其他错误：友好提示（避免在登录页 / 静默请求上打扰）
    if (status && status >= 500) {
      ElMessage.error(`服务异常 (${status})：${error.response?.data?.message || '请稍后重试'}`)
    } else if (status === 400) {
      // 业务校验错误由调用方自己展示 message，这里不重复弹
    }

    return Promise.reject(error)
  }
)

export { api as request }
export default api
