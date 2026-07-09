const BACKEND_URL = 'http://localhost:8080'

export async function loginAsAdmin(page: any): Promise<string> {
  const response = await page.request.post(`${BACKEND_URL}/api/auth/login`, {
    data: { username: 'admin', password: 'admin123' },
    headers: { 'Content-Type': 'application/json' }
  })
  if (!response.ok()) throw new Error(`Login failed: ${response.status()}`)
  const body = await response.json()
  const token = body?.data?.token || body?.token
  if (!token) throw new Error('No token in login response')
  return token
}

export async function setupAuthForPage(page: any): Promise<void> {
  const token = await loginAsAdmin(page)
  await page.addInitScript((t: string) => {
    localStorage.setItem('token', t)
    localStorage.setItem('accessToken', t)
    localStorage.setItem('currentUser', JSON.stringify({ id: 1, username: 'admin', role: 'ADMIN', roles: ['ADMIN'] }))
  }, token)
}
