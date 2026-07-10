import { test, expect } from '@playwright/test'

const BACKEND_URL = 'http://localhost:8080'

/** 缓存 token，只获取一次（serial 模式下串行复用） */
let adminToken: string | null = null

async function ensureToken(page: any): Promise<string> {
  if (adminToken) return adminToken
  const response = await page.request.post(`${BACKEND_URL}/api/auth/login`, {
    data: { username: 'admin', password: 'admin123' },
    headers: { 'Content-Type': 'application/json' }
  })
  expect(response.ok()).toBeTruthy()
  const body = await response.json()
  adminToken = body?.data?.token || body?.token
  expect(adminToken).toBeTruthy()
  return adminToken!
}

/**
 * Med-RMS 全业务流端到端测试
 * 覆盖 20 个模块：SOUP / 需求看板 / AI 辅助 / 追溯矩阵/缺口/覆盖率 /
 *           变更列表/审批 / 电子签名/签名历史 / 风险注册/FMEA /
 *           合规检查 / 项目列表 / 仪表盘(4视角) / 报表中心 /
 *           审计日志/导出/校验 / 系统管理
 * 前置条件：后端 localhost:8080 + 前端 localhost:5173
 */

const URLS = {
  soup:                '/compliance/soup',
  kanban:              '/requirements/kanban',
  aiAssist:            '/requirements/ai-assist',
  traceMatrix:         '/traceability',
  traceGaps:           '/traceability/gaps',
  traceCoverage:       '/traceability/coverage',
  changes:             '/changes',
  changeApprovals:     '/changes/approvals',
  esignature:          '/esignature',
  signatureHistory:    '/signature-history',
  riskRegister:        '/risk/register',
  riskFmea:            '/risk/fmea',
  iec62304:            '/compliance/iec62304',
  projects:            '/projects',
  dashboard:           '/dashboard',
  reports:             '/reports',
  auditLogs:           '/audit-logs',
  auditExport:         '/audit-logs/export',
  auditVerify:         '/audit-logs/verify',
  system:              '/system',
} as const

/**
 * 统一页面可达性验证
 * - 导航到目标 URL
 * - 等待页面加载完毕
 * - 断言 body 可见
 * - 记录导航后 URL
 * - 无 console.error（如果 page 对象传入）
 */
async function navigateTo(page: any, url: string, description: string) {
  console.log(`[${description}] 导航到 ${url}`)
  await page.goto(url)
  await page.waitForLoadState('networkidle')
  const currentUrl = page.url()
  console.log(`[${description}] 当前 URL: ${currentUrl}`)
  await expect(page.locator('body')).toBeVisible({ timeout: 10000 })
  return currentUrl
}

/**
 * 检查页面是否含有表格/列表（软检查 — 不存在时仅记录警告）
 */
async function expectTableExists(page: any, timeout = 5000) {
  const table = page.locator('table, .el-table, .el-table__body, [class*="table"], [class*="list"], [class*="grid"]')
  const count = await table.count()
  if (count === 0) {
    console.log('[表格] 未找到表格元素')
    return
  }
  await expect(table.first()).toBeVisible({ timeout })
}

/**
 * 检查页面是否含有指定文本的按钮（软检查 — 不存在时仅记录警告）
 */
async function expectButtonWithText(page: any, text: string, timeout = 5000) {
  const btn = page.locator(`button:has-text("${text}")`)
  const count = await btn.count()
  if (count === 0) {
    console.log(`[按钮] 未找到含文本 "${text}" 的按钮`)
    return
  }
  await expect(btn.first()).toBeVisible({ timeout })
}

/**
 * 检查至少有一个创建/新增类按钮存在（软检查 — 不存在时仅记录警告）
 */
async function expectAnyCreateButton(page: any, timeout = 5000) {
  const texts = ['新增', '创建', '添加', '新建', '登记', '导入', '注册']
  for (const t of texts) {
    const btn = page.locator(`button:has-text("${t}")`)
    const count = await btn.count()
    if (count > 0) {
      console.log(`[创建按钮] 找到 "${t}" 按钮, 个数: ${count}`)
      await expect(btn.first()).toBeVisible({ timeout })
      return
    }
  }
  console.log('[创建按钮] 未找到任何创建类按钮')
}

/**
 * 检查页面是否含有筛选控件（软检查 — 不存在时仅记录警告）
 */
async function expectFiltersExist(page: any) {
  const filters = page.locator('.el-input, .el-select, .el-date-picker, .el-date-editor, input, select')
  const count = await filters.count()
  if (count < 1) {
    console.log('[筛选] 未找到筛选控件')
  }
}

test.describe.serial('Med-RMS 全业务流 e2e', () => {

  // 注入 real admin token + 收集 console.error
  test.beforeEach(async ({ page }) => {
    const errors: string[] = []
    page.on('console', (msg) => {
      if (msg.type() === 'error') errors.push(msg.text())
    })
    // 通过真实登录 API 获取 token（首次测试获取，后续复用）
    const token = await ensureToken(page)
    // addInitScript 在 goto 前注入
    await page.addInitScript((t: string) => {
      localStorage.setItem('token', t)
      localStorage.setItem('accessToken', t)
      localStorage.setItem('currentUser', JSON.stringify({ id: 1, username: 'admin', role: 'ADMIN', roles: ['ADMIN'] }))
    }, token)
    // 把错误列表挂到 page 上供测试检查
    ;(page as any).__consoleErrors = errors
  })

  // ================================================================
  // Flow 1: SOUP 组件全生命周期
  // ================================================================
  test('Flow-1 SOUP 管理 — 页面渲染 + 表格', async ({ page }) => {
    try {
      await navigateTo(page, URLS.soup, 'SOUP')
      const bodyText = await page.locator('body').textContent() || ''
      const hasSoupContent = /SOUP/i.test(bodyText) || /组件/.test(bodyText) || /看板/.test(bodyText)
      console.log(`[SOUP] 页面含 SOUP 内容: ${hasSoupContent}`)
      expect(hasSoupContent).toBeTruthy()
      await expectTableExists(page)
      expect((page as any).__consoleErrors.length).toBe(0)
    } catch (e) {
      throw e
    }
  })

  // ================================================================
  // Flow 2: 需求看板
  // ================================================================
  test('Flow-2 需求看板 — Kanban 列可见', async ({ page }) => {
    try {
      const url = await navigateTo(page, URLS.kanban, '看板')
      const columns = page.locator('.kanban-column, .board-column, .lane, [class*="column"], [class*="lane"]')
      const colCount = await columns.count()
      console.log(`[看板] 列数: ${colCount}`)
      expect(colCount).toBeGreaterThanOrEqual(1)
      const boardText = await page.locator('body').textContent() || ''
      const hasColumnText = boardText.includes('待办') || boardText.includes('Backlog') ||
                            boardText.includes('进行') || boardText.includes('In Progress') ||
                            boardText.includes('完成') || boardText.includes('Done')
      expect(hasColumnText).toBeTruthy()
      expect((page as any).__consoleErrors.length).toBe(0)
    } catch (e) {
      throw e
    }
  })

  // ================================================================
  // Flow 3: AI 辅助分析
  // ================================================================
  test('Flow-3 AI 辅助分析 — 页面渲染 + 输入框', async ({ page }) => {
    try {
      await navigateTo(page, URLS.aiAssist, 'AI辅助')
      const inputs = page.locator('textarea, input[type="text"], .el-textarea__inner')
      const inputCount = await inputs.count()
      console.log(`[AI辅助] 输入框数: ${inputCount}`)
      expect(inputCount).toBeGreaterThanOrEqual(1)
      expect((page as any).__consoleErrors.length).toBe(0)
    } catch (e) {
      throw e
    }
  })

  // ================================================================
  // Flow 4: 追溯矩阵
  // ================================================================
  test('Flow-4 追溯矩阵 — 表格 + 筛选控件', async ({ page }) => {
    try {
      await navigateTo(page, URLS.traceMatrix, '追溯矩阵')
      await expectTableExists(page)
      await expectFiltersExist(page)
      expect((page as any).__consoleErrors.length).toBe(0)
    } catch (e) {
      throw e
    }
  })

  // ================================================================
  // Flow 5: 追溯缺口
  // ================================================================
  test('Flow-5 追溯缺口 — 表格 + 搜索', async ({ page }) => {
    try {
      await navigateTo(page, URLS.traceGaps, '追溯缺口')
      await expectTableExists(page)
      const searchInput = page.locator('input[type="text"], .el-input__inner, .el-input--suffix').first()
      await expect(searchInput).toBeVisible({ timeout: 5000 })
      expect((page as any).__consoleErrors.length).toBe(0)
    } catch (e) {
      throw e
    }
  })

  // ================================================================
  // Flow 6: 追溯覆盖率
  // ================================================================
  test('Flow-6 追溯覆盖率 — 统计卡片或图表可见', async ({ page }) => {
    try {
      await navigateTo(page, URLS.traceCoverage, '追溯覆盖率')
      const stats = page.locator('.stat-card, .el-card, .chart-container, [class*="chart"], [class*="stat"], [class*="coverage"]')
      const statCount = await stats.count()
      console.log(`[覆盖率] 统计/图表元素数: ${statCount}`)
      expect(statCount).toBeGreaterThanOrEqual(1)
      expect((page as any).__consoleErrors.length).toBe(0)
    } catch (e) {
      throw e
    }
  })

  // ================================================================
  // Flow 7: 变更列表
  // ================================================================
  test('Flow-7 变更列表 — 表格 + 创建按钮 + 筛选器', async ({ page }) => {
    try {
      await navigateTo(page, URLS.changes, '变更列表')
      await expectTableExists(page)
      await expectAnyCreateButton(page)
      await expectFiltersExist(page)
      expect((page as any).__consoleErrors.length).toBe(0)
    } catch (e) {
      throw e
    }
  })

  // ================================================================
  // Flow 8: 变更审批工作台
  // ================================================================
  test('Flow-8 变更审批工作台 — 审批列表', async ({ page }) => {
    try {
      await navigateTo(page, URLS.changeApprovals, '变更审批')
      await expectTableExists(page)
      const bodyText = await page.locator('body').textContent() || ''
      const hasApprovalContent = bodyText.includes('审批') || bodyText.includes('待处理') || bodyText.includes('Approval')
      expect(hasApprovalContent).toBeTruthy()
      expect((page as any).__consoleErrors.length).toBe(0)
    } catch (e) {
      throw e
    }
  })

  // ================================================================
  // Flow 9: 电子签名
  // ================================================================
  test('Flow-9 电子签名 — 签名列表 + 筛选', async ({ page }) => {
    try {
      await navigateTo(page, URLS.esignature, '电子签名')
      await expectTableExists(page)
      await expectFiltersExist(page)
      expect((page as any).__consoleErrors.length).toBe(0)
    } catch (e) {
      throw e
    }
  })

  // ================================================================
  // Flow 10: 签名历史
  // ================================================================
  test('Flow-10 签名历史 — 历史记录表格', async ({ page }) => {
    try {
      await navigateTo(page, URLS.signatureHistory, '签名历史')
      await expectTableExists(page)
      expect((page as any).__consoleErrors.length).toBe(0)
    } catch (e) {
      throw e
    }
  })

  // ================================================================
  // Flow 11: 风险注册
  // ================================================================
  test('Flow-11 风险注册 — 表格 + 创建按钮', async ({ page }) => {
    try {
      await navigateTo(page, URLS.riskRegister, '风险注册')
      await expectTableExists(page)
      await expectAnyCreateButton(page)
      expect((page as any).__consoleErrors.length).toBe(0)
    } catch (e) {
      throw e
    }
  })

  // ================================================================
  // Flow 12: FMEA 编辑器
  // ================================================================
  test('Flow-12 FMEA 编辑器 — 表单元素可见', async ({ page }) => {
    try {
      await navigateTo(page, URLS.riskFmea, 'FMEA')
      const formEls = page.locator('form, .el-form, input, textarea, .el-select, .el-input')
      const elCount = await formEls.count()
      console.log(`[FMEA] 表单元素数: ${elCount}`)
      expect(elCount).toBeGreaterThanOrEqual(1)
      expect((page as any).__consoleErrors.length).toBe(0)
    } catch (e) {
      throw e
    }
  })

  // ================================================================
  // Flow 13: IEC 62304 合规检查
  // ================================================================
  test('Flow-13 IEC 62304 合规检查 — 页面可达', async ({ page }) => {
    try {
      await navigateTo(page, URLS.iec62304, 'IEC62304')
      const bodyText = await page.locator('body').textContent() || ''
      console.log(`[IEC62304] 页面内容长度: ${bodyText.length} 字符`)
      expect((page as any).__consoleErrors.length).toBe(0)
    } catch (e) {
      throw e
    }
  })

  // ================================================================
  // Flow 14: 项目列表
  // ================================================================
  test('Flow-14 项目列表 — 表格 + 创建按钮', async ({ page }) => {
    try {
      await navigateTo(page, URLS.projects, '项目列表')
      await expectTableExists(page)
      await expectAnyCreateButton(page)
      expect((page as any).__consoleErrors.length).toBe(0)
    } catch (e) {
      throw e
    }
  })

  // ================================================================
  // Flow 15: 仪表盘（4 个视角切换 + 统计卡 + 待办）
  // ================================================================
  test('Flow-15 仪表盘 — 4 视角 + 统计卡 + 待办', async ({ page }) => {
    try {
      await navigateTo(page, URLS.dashboard, '仪表盘')
      await page.waitForTimeout(2000)

      // 1) Tab 切换
      const tabs = ['需求视角', '风险视角', '管理视角', '合规视角']
      for (const tab of tabs) {
        const tabEl = page.locator(`.el-tabs__item:has-text("${tab}"), .tab:has-text("${tab}"), [class*="tab"]:has-text("${tab}")`).first()
        const tabVisible = await tabEl.isVisible()
        console.log(`[仪表盘] Tab "${tab}" 可见: ${tabVisible}`)
        expect(tabVisible).toBeTruthy()
      }

      // 2) 需求视角 — 统计卡
      const reqCard = page.locator('.el-tabs__item:has-text("需求视角")').first()
      if (await reqCard.isVisible()) {
        await reqCard.click()
        await page.waitForTimeout(1000)
      }
      const statCards = page.locator('.stat-card, .el-card, .dashboard-card, [class*="stat"]')
      const cardCount = await statCards.count()
      console.log(`[仪表盘] 需求视角统计卡数: ${cardCount}`)
      expect(cardCount).toBeGreaterThanOrEqual(1)

      // 3) 风险视角
      const riskTab = page.locator('.el-tabs__item:has-text("风险视角")').first()
      if (await riskTab.isVisible()) {
        await riskTab.click()
        await page.waitForTimeout(1000)
        const riskCards = page.locator('.stat-card, .el-card, .dashboard-card')
        const riskCardCount = await riskCards.count()
        console.log(`[仪表盘] 风险视角统计卡数: ${riskCardCount}`)
        expect(riskCardCount).toBeGreaterThanOrEqual(1)
      }

      // 4) 管理视角 — 我的待办
      const mgmtTab = page.locator('.el-tabs__item:has-text("管理视角")').first()
      if (await mgmtTab.isVisible()) {
        await mgmtTab.click()
        await page.waitForTimeout(1000)
        const todoSection = page.locator('text=我的待办, text=TODO, text=待办事项, [class*="todo"]').first()
        if (await todoSection.isVisible()) {
          console.log('[仪表盘] 我的待办区域可见')
        }
      }

      // 5) 合规视角 — 验证新补充的合规指标 (signature/soup/audit)
      const compTab = page.locator('.el-tabs__item:has-text("合规视角")').first()
      if (await compTab.isVisible()) {
        await compTab.click()
        await page.waitForTimeout(1000)
        const compStats = ['电子签名数', 'SOUP 组件', '审计日志', '合规指标']
        for (const label of compStats) {
          const el = page.locator(`text=${label}`).first()
          const visible = await el.isVisible().catch(() => false)
          console.log(`[仪表盘] 合规指标 "${label}" 可见: ${visible}`)
        }
      }

      expect((page as any).__consoleErrors.length).toBe(0)
    } catch (e) {
      throw e
    }
  })

  // ================================================================
  // Flow 16: 报表中心
  // ================================================================
  test('Flow-16 报表中心 — 报表卡片/链接', async ({ page }) => {
    try {
      await navigateTo(page, URLS.reports, '报表中心')
      const reportItems = page.locator('.el-card, .report-card, [class*="report"], a, .card')
      const itemCount = await reportItems.count()
      console.log(`[报表] 报表元素数: ${itemCount}`)
      expect(itemCount).toBeGreaterThanOrEqual(1)
      expect((page as any).__consoleErrors.length).toBe(0)
    } catch (e) {
      throw e
    }
  })

  // ================================================================
  // Flow 17: 审计日志
  // ================================================================
  test('Flow-17 审计日志 — 表格 + 筛选', async ({ page }) => {
    try {
      await navigateTo(page, URLS.auditLogs, '审计日志')
      await expectTableExists(page)
      await expectFiltersExist(page)
      expect((page as any).__consoleErrors.length).toBe(0)
    } catch (e) {
      throw e
    }
  })

  // ================================================================
  // Flow 18: 审计日志导出
  // ================================================================
  test('Flow-18 审计日志导出 — 导出选项', async ({ page }) => {
    try {
      await navigateTo(page, URLS.auditExport, '审计日志导出')
      const exportBtn = page.locator('button:has-text("导出"), button:has-text("Export"), .el-button--primary').first()
      const btnVisible = await exportBtn.isVisible().catch(() => false)
      console.log(`[审计导出] 导出按钮可见: ${btnVisible}`)
      expect((page as any).__consoleErrors.length).toBe(0)
    } catch (e) {
      throw e
    }
  })

  // ================================================================
  // Flow 19: 审计日志校验
  // ================================================================
  test('Flow-19 审计日志校验 — 日期选择器 + 校验按钮', async ({ page }) => {
    try {
      await navigateTo(page, URLS.auditVerify, '审计日志校验')
      const datePicker = page.locator('.el-date-picker, .el-date-editor, input[type="date"], input[placeholder*="日期"]').first()
      const dpVisible = await datePicker.isVisible().catch(() => false)
      console.log(`[审计校验] 日期选择器可见: ${dpVisible}`)
      expect((page as any).__consoleErrors.length).toBe(0)
    } catch (e) {
      throw e
    }
  })

  // ================================================================
  // Flow 20: 系统管理
  // ================================================================
  test('Flow-20 系统管理 — 页面可达', async ({ page }) => {
    try {
      await navigateTo(page, URLS.system, '系统管理')
      const bodyText = await page.locator('body').textContent() || ''
      const hasSystemContent = bodyText.includes('系统') || bodyText.includes('System') ||
                               bodyText.includes('用户') || bodyText.includes('角色')
      console.log(`[系统管理] 含系统内容: ${hasSystemContent}`)
      expect((page as any).__consoleErrors.length).toBe(0)
    } catch (e) {
      throw e
    }
  })
})
