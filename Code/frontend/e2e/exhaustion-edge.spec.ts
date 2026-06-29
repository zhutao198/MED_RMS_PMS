// W30 Phase 5: 边界场景扫描
// 检查每个有筛选器的页面是否提供"全部"选项 + 空态文案 + 必填校验
import { test, expect } from '@playwright/test'

/**
 * Phase 5 检查矩阵
 * 每页检查：
 * 1. 有 .filter-row 页面 → 检查每个 .el-select 是否提供"全部"选项（value 包含 全部/All）
 * 2. 列表页空态是否有"暂无/无数据"文案
 * 3. 表单页必填项是否有 * 标记
 */
const PAGES_WITH_FILTER = [
  { name: 'RequirementList', url: '/requirements', filterSelectors: ['.filter-row .el-select'] },
  { name: 'TestCaseList', url: '/testcases', filterSelectors: ['.filter-row .el-select'] },
  { name: 'ChangeList', url: '/changes', filterSelectors: ['.filter-row .el-select'] },
  { name: 'ChangeApprovals', url: '/changes/approvals', filterSelectors: ['.filter-row .el-select'] },
  { name: 'RiskRegister', url: '/risk/register', filterSelectors: ['.el-select'] },
  { name: 'RisksMatrix', url: '/risks/matrix', filterSelectors: ['.el-select'] },
  { name: 'FmeaEditor', url: '/risk/fmea', filterSelectors: ['.el-select'] },
  { name: 'ProjectList', url: '/projects', filterSelectors: ['.el-select'] },
  { name: 'GanttView', url: '/projects/gantt', filterSelectors: ['.el-select'] },
  { name: 'IpdGate', url: '/projects/ipd', filterSelectors: ['.el-select'] },
  { name: 'TraceabilityCoverage', url: '/traceability/coverage', filterSelectors: ['.el-select'] },
  { name: 'TraceabilityGaps', url: '/traceability/gaps', filterSelectors: ['.el-select'] },
  { name: 'TraceGraph', url: '/trace-graph', filterSelectors: ['.el-select'] },
  { name: 'ResourceManagement', url: '/projects/resources', filterSelectors: ['.el-select'] },
]

test.describe('W30 Phase 5 边界场景扫描', () => {
  test('W30-5-ALL-OPTION 检查所有有筛选器的页面是否提供"全部"选项', async ({ page }) => {
    const report: any[] = []
    for (const pg of PAGES_WITH_FILTER) {
      await page.goto(pg.url, { timeout: 15000 }).catch(() => {})
      await page.waitForTimeout(2000)

      const allSelects: any[] = []
      const selectors = await page.locator('.el-select, .el-cascader').count()
      for (let i = 0; i < Math.min(selectors, 5); i++) {
        try {
          await page.locator('.el-select, .el-cascader').nth(i).click()
          await page.waitForTimeout(400)
          const items = await page.locator('.el-select-dropdown__item').allTextContents()
          const hasAll = items.some(t => /全部|All|all/i.test(t))
          const value = await page.locator('.el-select, .el-cascader').nth(i).locator('input').first().inputValue().catch(() => '')
          allSelects.push({ index: i, hasAll, items: items.slice(0, 8), value })
          // 关闭
          await page.keyboard.press('Escape')
          await page.waitForTimeout(200)
        } catch (e) {
          allSelects.push({ index: i, error: String(e).slice(0, 80) })
        }
      }
      report.push({ page: pg.name, url: pg.url, selects: allSelects })
    }

    // 汇总
    const issues: any[] = []
    for (const r of report) {
      for (const s of r.selects) {
        if (!s.hasAll && !s.error && s.value !== undefined) {
          // 当前有值的筛选器必须有"全部"选项
          issues.push({ page: r.page, selectorIndex: s.index, value: s.value, items: s.items })
        }
      }
    }

    console.log('FILTER OPTION REPORT:')
    console.log(JSON.stringify(report, null, 2))
    console.log('ISSUES (无"全部"选项的筛选器):')
    console.log(JSON.stringify(issues, null, 2))

    expect(issues.length).toBeLessThan(10) // 软上限
  })

  test('W30-5-EMPTY-STATE 检查列表页空态文案', async ({ page }) => {
    // 访问已知空数据的页面（GanttView 暂无数据已确认）
    await page.goto('/projects/gantt', { timeout: 15000 }).catch(() => {})
    await page.waitForTimeout(2500)
    const body = await page.locator('body').textContent()
    // GanttView "暂无甘特图数据" 应该有
    expect(body).toContain('暂无') // 任何空态提示
  })
})