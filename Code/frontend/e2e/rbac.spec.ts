import { test, expect } from '@playwright/test'

/**
 * RBAC e2e 测试（v1.27 R28）
 * 验证 8 角色 × 关键端点的权限强制
 * 前置：后端 localhost:8080 + 前端 5173
 */

const API = 'http://localhost:8080/api'
const ACCOUNTS = {
  admin:      { user: 'admin',      pwd: 'admin123', expectAllPass: true },
  qa_mgr:     { user: 'qa_mgr',     pwd: 'admin123', expectAllPass: false },
  pm:         { user: 'pm',         pwd: 'admin123', expectAllPass: false },
  re:         { user: 're',         pwd: 'admin123', expectAllPass: false },
  reviewer:   { user: 'reviewer',   pwd: 'admin123', expectAllPass: false },
  risk_mgr:   { user: 'risk_mgr',   pwd: 'admin123', expectAllPass: false },
  compliance: { user: 'compliance', pwd: 'admin123', expectAllPass: false },
  viewer:     { user: 'viewer',     pwd: 'admin123', expectAllPass: false },
}

interface TestCase {
  name: string
  method: 'GET' | 'POST' | 'PUT' | 'DELETE'
  path: string
  body?: object
  // 哪些角色应该成功（200/201）
  allowRoles: string[]
  // 哪些角色应该被拒（403）
  denyRoles: string[]
}

const cases: TestCase[] = [
  {
    name: 'GET /requirements 列表',
    method: 'GET',
    path: '/requirements',
    allowRoles: ['admin', 'qa_mgr', 'pm', 're', 'reviewer', 'risk_mgr', 'compliance', 'viewer'],
    denyRoles: [],
  },
  {
    name: 'GET /system/users（仅 admin/qa_mgr）',
    method: 'GET',
    path: '/system/users',
    allowRoles: ['admin'],  // rbac seed: ADMIN 全有, QA_MGR 没有 sys:user:list
    denyRoles: ['qa_mgr', 'pm', 're', 'reviewer', 'risk_mgr', 'compliance', 'viewer'],
  },
  {
    name: 'POST /requirements 创建（admin/PM/RE/QA_MGR）',
    method: 'POST',
    path: '/requirements',
    body: { title: 'e2e-test', projectId: 1, requirementType: 'URS' },
    allowRoles: ['admin', 'pm', 're', 'qa_mgr'],
    denyRoles: ['reviewer', 'risk_mgr', 'compliance', 'viewer'],
  },
  {
    name: 'POST /changes 创建（admin/PM/RE/QA_MGR）',
    method: 'POST',
    path: '/changes',
    body: { title: 'e2e-test', projectId: 1 },
    allowRoles: ['admin', 'pm', 're', 'qa_mgr'],
    denyRoles: ['reviewer', 'risk_mgr', 'compliance', 'viewer'],
  },
  {
    name: 'GET /compliance/audit-logs 审计日志（admin/QA_MGR/COMPLIANCE）',
    method: 'GET',
    path: '/compliance/audit-logs',
    allowRoles: ['admin', 'compliance', 'qa_mgr'],
    denyRoles: ['pm', 're', 'reviewer', 'risk_mgr', 'viewer'],
  },
  {
    name: 'GET /requirement/soup-components（all 含 viewer）',
    method: 'GET',
    path: '/requirement/soup-components',
    allowRoles: ['admin', 'qa_mgr', 'pm', 're', 'risk_mgr', 'compliance', 'viewer'],
    denyRoles: ['reviewer'],
  },
  {
    name: 'GET /esignature/signatures（几乎所有角色）',
    method: 'GET',
    path: '/esignature/signatures',
    allowRoles: ['admin', 'qa_mgr', 'pm', 're', 'reviewer', 'risk_mgr', 'compliance', 'viewer'],
    denyRoles: [],
  },
  {
    name: 'GET /traceability/matrix 追溯矩阵',
    method: 'GET',
    path: '/traceability/matrix',
    allowRoles: ['admin', 'qa_mgr', 'pm', 're', 'reviewer', 'risk_mgr', 'compliance', 'viewer'],
    denyRoles: [],
  },
  {
    name: 'POST /changes/{id}/approve（admin/PM/QA_MGR）',
    method: 'POST',
    path: '/changes/1/approve',
    body: { comment: 'test' },
    allowRoles: ['admin', 'pm', 'qa_mgr'],
    denyRoles: ['re', 'reviewer', 'risk_mgr', 'compliance', 'viewer'],
  },
  {
    name: 'POST /compliance/audit-logs/verify 哈希链校验（admin/QA_MGR/COMPLIANCE）',
    method: 'POST',
    path: '/compliance/audit-logs/verify',
    body: { range: 'all' },
    allowRoles: ['admin', 'qa_mgr', 'compliance'],
    denyRoles: ['pm', 're', 'reviewer', 'risk_mgr', 'viewer'],
  },
]

async function login(user: string, pwd: string): Promise<string> {
  const res = await fetch(`${API}/auth/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username: user, password: pwd }),
  })
  if (!res.ok) throw new Error(`Login failed for ${user}: ${res.status}`)
  const json = await res.json()
  return json.data.token
}

async function callApi(token: string, method: string, path: string, body?: object): Promise<number> {
  const init: RequestInit = {
    method,
    headers: { Authorization: `Bearer ${token}` },
  }
  if (body && (method === 'POST' || method === 'PUT')) {
    init.headers = { ...init.headers, 'Content-Type': 'application/json' }
    init.body = JSON.stringify(body)
  }
  const res = await fetch(`${API}${path}`, init)
  return res.status
}

test.describe('RBAC 端点强制 e2e（v1.27）', () => {

  // 为每个角色登录并保存 token
  const tokens: Record<string, string> = {}
  test.beforeAll(async () => {
    for (const [key, acc] of Object.entries(ACCOUNTS)) {
      tokens[key] = await login(acc.user, acc.pwd)
    }
  })

  for (const tc of cases) {
    test(`${tc.name}`, async () => {
      for (const role of tc.allowRoles) {
        const status = await callApi(tokens[role], tc.method, tc.path, tc.body)
        // 200/201 都算成功
        if (status !== 200 && status !== 201) {
          throw new Error(`角色 ${role} 应通过 ${tc.method} ${tc.path}，但实际状态码 ${status}`)
        }
      }
      for (const role of tc.denyRoles) {
        const status = await callApi(tokens[role], tc.method, tc.path, tc.body)
        if (status !== 403) {
          throw new Error(`角色 ${role} 应被拒 ${tc.method} ${tc.path}，但实际状态码 ${status}`)
        }
      }
    })
  }

  test('未登录访问受保护端点 → 403', async () => {
    const status = await callApi('', 'POST', '/requirements', { title: 't', projectId: 1, requirementType: 'URS' })
    if (status !== 403) throw new Error(`未登录应返回 403，实际 ${status}`)
  })
})
