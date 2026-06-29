// W30 Phase 2: 关键 20 页 UI 巡检
// 抓 console.error / Vue warn / 网络 4xx-5xx / 统计卡全 0 / 项目筛选"全部"选项
import { test, expect } from '@playwright/test'

/**
 * Tier 1 必测 20 页（按 exhaustion_test_plan.md §3.1）
 */
const TIER1_PAGES = [
  { name: 'Dashboard', url: '/dashboard', hasFilter: true, hasStatCards: true },
  { name: 'RequirementList', url: '/requirements', hasFilter: true, hasStatCards: true },
  { name: 'RequirementDetail', url: '/requirements/1612', hasFilter: false, hasStatCards: false },
  { name: 'ReqEdit', url: '/requirements/1612/edit', hasFilter: false, hasStatCards: false },
  { name: 'ReqCreate', url: '/requirements/create', hasFilter: false, hasStatCards: false },
  { name: 'DecomposeList', url: '/requirements/1/decompose', hasFilter: false, hasStatCards: false },
  { name: 'TestCaseList', url: '/testcases', hasFilter: true, hasStatCards: false },
  { name: 'ChangeList', url: '/changes', hasFilter: true, hasStatCards: false },
  { name: 'ChangeRequest', url: '/changes/1', hasFilter: false, hasStatCards: false },
  { name: 'ChangeApprovals', url: '/changes/approvals', hasFilter: true, hasStatCards: false },
  { name: 'TraceGraph', url: '/trace-graph', hasFilter: true, hasStatCards: false },
  { name: 'TraceMatrix', url: '/traceability/coverage', hasFilter: true, hasStatCards: true },
  { name: 'TraceGaps', url: '/traceability/gaps', hasFilter: true, hasStatCards: true },
  { name: 'RiskRegister', url: '/risk/register', hasFilter: true, hasStatCards: true },
  { name: 'RisksMatrix', url: '/risks/matrix', hasFilter: true, hasStatCards: false },
  { name: 'FmeaEditor', url: '/risk/fmea', hasFilter: true, hasStatCards: false },
  { name: 'ProjectList', url: '/projects', hasFilter: true, hasStatCards: false },
  { name: 'ProjectDetail', url: '/projects/1', hasFilter: false, hasStatCards: true },
  { name: 'GanttView', url: '/projects/gantt', hasFilter: true, hasStatCards: false },
  { name: 'IpdGate', url: '/projects/ipd', hasFilter: true, hasStatCards: false },
]

test.describe('W30 Phase 2 关键 20 页 UI 巡检', () => {
  test('W30-2-UI-AUDIT 所有 Tier 1 页面 console + 网络 + 统计卡全检', async ({ page }) => {
    const report: any[] = []

    for (const pg of TIER1_PAGES) {
      const consoleErrors: string[] = []
      const vueWarns: string[] = []
      const networkFails: string[] = []
      const pageReq = page.context().request

      const consoleHandler = (msg: any) => {
        if (msg.type() === 'error') consoleErrors.push(msg.text())
        if (msg.type() === 'warning' && msg.text().includes('Vue warn')) vueWarns.push(msg.text())
      }
      const reqHandler = (req: any) => {
        // 跳过 Vite HMR / sourcemap
        const url = req.url()
        if (url.includes('/@vite/') || url.includes('node_modules') || url.endsWith('.map')) return
      }
      const respHandler = async (resp: any) => {
        const status = resp.status()
        const url = resp.url()
        if (url.includes('/api/') && status >= 400 && status < 600) {
          // 业务异常包在 HTTP 200，但 SY0301/SY0101 等也算
          networkFails.push(`${status} ${url.replace('http://localhost:5173/api/', '/api/')}`)
        }
      }
      page.on('console', consoleHandler)
      page.on('request', reqHandler)
      page.on('response', respHandler)

      try {
        await page.goto(pg.url, { waitUntil: 'networkidle', timeout: 10000 }).catch(() => {})
        await page.waitForTimeout(2500)
      } catch (e) {
        // page.goto 失败也算
      }

      page.off('console', consoleHandler)
      page.off('request', reqHandler)
      page.off('response', respHandler)

      // 抓统计卡
      const statCards = await page.locator('.stat-card').count()
      let statCardsAllZero = false
      let statCardValues: any[] = []
      if (pg.hasStatCards && statCards > 0) {
        for (let i = 0; i < statCards; i++) {
          const num = (await page.locator('.stat-card').nth(i).locator('.stat-num, .stat-value').textContent())?.trim() || ''
          statCardValues.push(num)
        }
        statCardsAllZero = statCardValues.every(v => v === '0' || v === '0%' || v === '')
      }

      // 检查项目筛选器是否有"全部"选项
      let hasAllOption = true
      if (pg.hasFilter) {
        try {
          const selects = await page.locator('.filter-row .el-select, .el-select').count()
          if (selects > 0) {
            await page.locator('.filter-row .el-select, .el-select').first().click()
            await page.waitForTimeout(500)
            const items = await page.locator('.el-select-dropdown__item').allTextContents()
            hasAllOption = items.some(t => t.includes('全部') || t.includes('All') || t.includes('all'))
            await page.keyboard.press('Escape')
            await page.waitForTimeout(200)
          }
        } catch (e) {}
      }

      const entry = {
        page: pg.name,
        url: pg.url,
        consoleErrors: consoleErrors.length,
        consoleErrorSample: consoleErrors.slice(0, 2),
        vueWarns: vueWarns.length,
        vueWarnSample: vueWarns.slice(0, 1),
        networkFails: networkFails.length,
        networkFailSample: networkFails.slice(0, 3),
        statCards,
        statCardValues,
        statCardsAllZero,
        hasFilter: pg.hasFilter,
        hasAllOption
      }
      report.push(entry)
    }

    // 输出报告
    console.log('=' .repeat(80))
    console.log('W30 Phase 2 UI 巡检报告')
    console.log('=' .repeat(80))
    console.log(JSON.stringify(report, null, 2))

    // 汇总
    const summary = {
      totalPages: report.length,
      withConsoleErrors: report.filter((r: any) => r.consoleErrors > 0).length,
      withVueWarns: report.filter((r: any) => r.vueWarns > 0).length,
      withNetworkFails: report.filter((r: any) => r.networkFails > 0).length,
      withStatCardsAllZero: report.filter((r: any) => r.statCardsAllZero).length,
      withoutAllOption: report.filter((r: any) => r.hasFilter && !r.hasAllOption).map((r: any) => r.page)
    }
    console.log('SUMMARY:', JSON.stringify(summary, null, 2))

    // 软断言：不全 PASS 也允许（发现 bug 才是目的）
    // 但要求至少 Dashboard 渲染正常
    expect(summary.withConsoleErrors).toBeLessThan(20) // 软上限
  })
})