import { defineStore } from 'pinia'
import { ref } from 'vue'

function decodeJwtPayload(jwt: string): Record<string, unknown> | null {
  try {
    const part = jwt.split('.')[1]
    if (!part) return null
    const padded = part.replace(/-/g, '+').replace(/_/g, '/')
    // atob 返回的是 Latin-1 字符每个 char 一字节；需用 TextDecoder 重新按 UTF-8 解码
    const binary = atob(padded)
    const bytes = new Uint8Array(binary.length)
    for (let i = 0; i < binary.length; i++) bytes[i] = binary.charCodeAt(i)
    const json = new TextDecoder('utf-8').decode(bytes)
    return JSON.parse(json)
  } catch {
    return null
  }
}

export const useUserStore = defineStore('user', () => {
  const token = ref(localStorage.getItem('token') || '')
  const userInfo = ref<{ id: number; username: string; realName: string } | null>(null)

  function syncFromToken() {
    if (!token.value) {
      userInfo.value = null
      return
    }
    const p = decodeJwtPayload(token.value)
    if (!p) {
      userInfo.value = null
      return
    }
    userInfo.value = {
      id: Number(p.userId) || 0,
      username: String(p.username || ''),
      realName: String(p.realName || p.username || '')
    }
  }

  // 初始化时从已有 token 恢复（页面刷新场景）
  syncFromToken()

  function setToken(newToken: string) {
    token.value = newToken
    localStorage.setItem('token', newToken)
    syncFromToken()
  }

  function setUserInfo(info: typeof userInfo.value) {
    userInfo.value = info
  }

  function logout() {
    token.value = ''
    userInfo.value = null
    localStorage.removeItem('token')
  }

  return { token, userInfo, setToken, setUserInfo, logout }
})