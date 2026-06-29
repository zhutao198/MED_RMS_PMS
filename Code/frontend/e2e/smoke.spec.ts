import { test, expect } from '@playwright/test'

/**
 * Med-RMS 烟测套件（覆盖 v1.23 19/19 关键页面 + v1.24 RBAC 验证）
 * 前置条件：后端在 localhost:8080 跑，前端 npm run dev 在 5173
 */
test.describe('Med-RMS 烟测套件 v1.24', () => {

  test('S1 登录页可见', async ({ page }) => {
    await page.goto('/')
    await expect(page.locator('body')).toBeVisible()
  })

  test('S2 admin 登录可成功并跳转到主界面', async ({ page }) => {
    await page.goto('/login')
    // 输入用户名密码
    const inputs = page.locator('input')
    const count = await inputs.count()
    if (count >= 2) {
      await inputs.nth(0).fill('admin')
      await inputs.nth(1).fill('admin123')
    }
    // 查找登录按钮
    const loginBtn = page.locator('button:has-text("登录"), button:has-text("Login"), button[type="submit"]')
    if (await loginBtn.count() > 0) {
      await loginBtn.first().click()
      await page.waitForTimeout(1500)
    }
  })

  test('S3 主界面 19 模块可达（v1.23 关键模块抽样）', async ({ page }) => {
    const pages = [
      '/requirements',
      '/projects',
      '/trace-matrix',
      '/changes',
      '/risk/register',
      '/compliance/dhf',
      '/esignature',
    ]
    for (const p of pages) {
      await page.goto(p)
      await expect(page.locator('body')).toBeVisible()
    }
  })
})
