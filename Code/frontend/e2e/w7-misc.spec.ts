import { test, expect } from '@playwright/test'

/**
 * 其他模块 e2e（W7-D5）
 * 覆盖：电子签名 / 风险 / 项目 / 系统 / 报表
 */
test.describe('其他模块 e2e（W7-D5）', () => {

  test('W7-OT-1 电子签名设置可达', async ({ page }) => {
    await page.goto('/esignature/settings')
    await expect(page.locator('body')).toBeVisible()
    await page.waitForTimeout(2000)
  })

  test('W7-OT-2 电子签名记录可达', async ({ page }) => {
    await page.goto('/esignature')
    await expect(page.locator('body')).toBeVisible()
    await page.waitForTimeout(2000)
  })

  test('W7-OT-3 风险登记页面可达', async ({ page }) => {
    await page.goto('/risk')
    await expect(page.locator('body')).toBeVisible()
    await page.waitForTimeout(2000)
  })

  test('W7-OT-4 项目管理列表可达', async ({ page }) => {
    await page.goto('/projects')
    await expect(page.locator('body')).toBeVisible()
    await page.waitForTimeout(2000)
  })

  test('W7-OT-5 系统管理-用户管理可达', async ({ page }) => {
    await page.goto('/system')
    await expect(page.locator('body')).toBeVisible()
    await page.waitForTimeout(2000)
  })

  test('W7-OT-6 报表中心可达', async ({ page }) => {
    await page.goto('/reports')
    await expect(page.locator('body')).toBeVisible()
    await page.waitForTimeout(2000)
  })
})
