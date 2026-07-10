const ROLE_LABELS: Record<string, string> = {
  admin: '系统管理员',
  pm: '项目经理',
  re: '需求工程师',
  reviewer: '评审员',
  risk_mgr: '风险管理员',
  qa_mgr: 'QA 主管',
  compliance: '合规专员',
  viewer: '只读用户',
  pd: '产品经理'
}

export function decodeJwtPayload(jwt: string): Record<string, unknown> | null {
  try {
    const part = jwt.split('.')[1]
    if (!part) return null
    const padded = part.replace(/-/g, '+').replace(/_/g, '/')
    const binary = atob(padded)
    const bytes = new Uint8Array(binary.length)
    for (let i = 0; i < binary.length; i++) bytes[i] = binary.charCodeAt(i)
    const json = new TextDecoder('utf-8').decode(bytes)
    return JSON.parse(json)
  } catch {
    return null
  }
}

export function parseToken(token?: string): Record<string, unknown> | null {
  const t = token || localStorage.getItem('token')
  if (!t) return null
  return decodeJwtPayload(t)
}

export function getRoles(token?: string): string[] {
  const p = parseToken(token)
  if (!p) return []
  const roles = p['roles']
  if (Array.isArray(roles)) return roles.map(String)
  const role = p['role']
  if (role) return [String(role)]
  return []
}

export function getRoleLabel(role: string): string {
  return ROLE_LABELS[role.toLowerCase()] || role
}

export function hasRole(required: string[], token?: string): boolean {
  const userRoles = getRoles(token).map(r => r.toLowerCase())
  return required.some(r => userRoles.includes(r.toLowerCase()))
}

export function isLoggedIn(): boolean {
  return !!localStorage.getItem('token')
}
