// W30 Phase 2 自动版：批量访问所有核心页面，抓 console.error
import { test, expect } from '@playwright/test'

const ALL_PAGES = [
  '/dashboard',
  '/requirements',
  '/requirements/1612',
  '/requirements/1612/edit',
  '/requirements/create',
  '/decompose',
  '/requirement-pool',
  '/requirement-tasks',
  '/testcases',
  '/changes',
  '/changes/approvals',
  '/changes/create',
  '/traceability',
  '/traceability/coverage',
  '/traceability/gaps',
  '/trace-graph',
  '/traceability/import',
  '/risk',
  '/risk/register',
  '/risks/matrix',
  '/risks/monitoring',
  '/risk/fmea',
  '/compliance',
  '/compliance/dhf',
  '/compliance/erps',
  '/compliance/iec62304',
  '/compliance/reports',
  '/compliance/soup',
  '/compliance/baselines',
  '/compliance/problem-report',
  '/esignature',
  '/audit-logs',
  '/reports',
  '/reports/custom',
  '/reports/export',
  '/projects',
  '/projects/1',
  '/projects/1/deliverables',
  '/projects/templates',
  '/projects/gantt',
  '/projects/ipd',
  '/projects/resources',
  '/projects/worklog',
  '/system',
  '/system/dicts',
  '/system/users',
  '/system/organization',
  '/system/login-logs',
  '/system/operation-logs',
  '/system/profile',
  '/system/migration',
  '/notifications',
  '/milestones',
  '/requirements/kanban',
  '/requirements/quality',
  '/requirements/ai-assist',
]

test('W30-2-UI-AUDIT-ALL 所有核心页面 console.error 检测', async ({ page }) => {
  test.setTimeout(5 * 60 * 1000) // 5 分钟
  const report: any[] = []

  for (const url of ALL_PAGES) {
    const consoleErrors: string[] = []
    const pageErrors: string[] = []
    const handler = (msg: any) => {
      if (msg.type() === 'error') consoleErrors.push(msg.text().slice(0, 200))
    }
    const errHandler = (err: Error) => {
      pageErrors.push(err.message.slice(0, 200))
    }
    page.on('console', handler)
    page.on('pageerror', errHandler)
    try {
      await page.goto(url, { waitUntil: 'domcontentloaded', timeout: 10000 })
      await page.waitForTimeout(1500)
    } catch (e: any) {
      pageErrors.push(e.message?.slice(0, 200) || 'navigation error')
    }
    page.off('console', handler)
    page.off('pageerror', errHandler)
    report.push({ url, consoleErrors: consoleErrors.length, pageErrors: pageErrors.length, sample: [...consoleErrors, ...pageErrors].slice(0, 2) })
  }

  // 汇总
  const hasErrors = report.filter((r: any) => r.consoleErrors > 0 || r.pageErrors > 0)
  console.log(`Total pages: ${report.length}, with errors: ${hasErrors.length}`)
  for (const r of hasErrors.slice(0, 20)) {
    console.log(`  ${r.url}: console=${r.consoleErrors} page=${r.pageErrors} | ${r.sample.join(' | ')}`)
  }

  // 把报告写到本地文件
  const fs = await import('fs')
  fs.writeFileSync('test-results/page-audit-report.json', JSON.stringify(report, null, 2))

  // 不强制失败，只是报告
})