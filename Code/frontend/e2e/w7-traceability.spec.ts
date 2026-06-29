import { test, expect } from '@playwright/test'

/**
 * 追溯管理 e2e（W7-D5）
 * 覆盖：追溯矩阵 / Gap 分析 / 追溯图谱
 */
test.describe('追溯管理 e2e（W7-D5）', () => {

  test('W7-TR-1 追溯矩阵页面可达', async ({ page }) => {
    await page.goto('/traceability')
    await expect(page.locator('body')).toBeVisible()
    await page.waitForTimeout(2000)
  })

  test('W7-TR-2 追溯 Gap 列表可达', async ({ page }) => {
    await page.goto('/traceability/gaps')
    await expect(page.locator('body')).toBeVisible()
    await page.waitForTimeout(2000)
  })

  test('W7-TR-3 追溯图谱页面可达', async ({ page }) => {
    await page.goto('/trace-graph')
    await expect(page.locator('body')).toBeVisible()
    await page.waitForTimeout(2000)
  })
})
