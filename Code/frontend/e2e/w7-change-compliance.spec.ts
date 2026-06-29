import { test, expect } from '@playwright/test'

/**
 * 变更 + 合规 e2e（W7-D5）
 * 覆盖：变更列表 / 评审 / 合规检查 / SOUP
 */
test.describe('变更 + 合规 e2e（W7-D5）', () => {

  test('W7-CH-1 变更列表可达', async ({ page }) => {
    await page.goto('/changes')
    await expect(page.locator('body')).toBeVisible()
    await page.waitForTimeout(2000)
  })

  test('W7-CH-2 评审管理页面可达', async ({ page }) => {
    await page.goto('/reviews')
    await expect(page.locator('body')).toBeVisible()
    await page.waitForTimeout(2000)
  })

  test('W7-CH-3 合规检查页面可达', async ({ page }) => {
    await page.goto('/compliance')
    await expect(page.locator('body')).toBeVisible()
    await page.waitForTimeout(2000)
  })

  test('W7-CH-4 SOUP 管理页面可达', async ({ page }) => {
    await page.goto('/compliance/soup')
    await expect(page.locator('body')).toBeVisible()
    await page.waitForTimeout(2000)
  })
})
