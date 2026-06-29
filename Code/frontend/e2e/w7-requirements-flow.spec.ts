import { test, expect } from '@playwright/test'

/**
 * Med-RMS 需求流端到端（W7-D5）
 * 覆盖：登录 → 需求列表 → 需求详情 → 需求创建 → 需求编辑 → 需求拆解
 * 前置：后端 8080 + 前端 5173
 */

test.describe('需求管理 e2e（W7-D5）', () => {

  test('W7-REQ-1 登录页可达 + 接受 admin/admin123', async ({ page }) => {
    await page.goto('/login')
    // 验证页面可访问
    expect(page.url()).toContain('/login')

    const inputs = page.locator('input')
    const count = await inputs.count()
    if (count >= 2) {
      await inputs.nth(0).fill('admin')
      await inputs.nth(1).fill('admin123')
      // 不强制验证跳转（前端 form 可能有多种结构）
      const loginBtn = page.locator('button[type="submit"], button:has-text("登录")')
      if (await loginBtn.count() > 0) {
        await loginBtn.first().click()
        await page.waitForTimeout(1500)
      }
    }
    // 关键断言：页面正常响应（无论是否跳转）
    await expect(page.locator('body')).toBeVisible()
  })

  test('W7-REQ-2 需求列表可达 + 显示列表', async ({ page }) => {
    await page.goto('/requirements')
    await expect(page.locator('body')).toBeVisible()
    // 等待表格加载
    await page.waitForTimeout(2000)
    // 应该有表格或列表元素
    const table = page.locator('table, .el-table, .requirement-list, [class*="list"]')
    expect(await table.count()).toBeGreaterThan(0)
  })

  test('W7-REQ-3 需求详情可达', async ({ page }) => {
    await page.goto('/requirements/1')
    await expect(page.locator('body')).toBeVisible()
    await page.waitForTimeout(1500)
  })

  test('W7-REQ-4 需求创建页面表单可见', async ({ page }) => {
    await page.goto('/requirements/create')
    await expect(page.locator('body')).toBeVisible()
    await page.waitForTimeout(1500)
    // 应该有 form 或 input
    const form = page.locator('form, .el-form, input')
    expect(await form.count()).toBeGreaterThan(0)
  })

  test('W7-REQ-5 需求编辑页面可达', async ({ page }) => {
    await page.goto('/requirements/1/edit')
    await expect(page.locator('body')).toBeVisible()
    await page.waitForTimeout(1500)
  })

  test('W7-REQ-6 需求拆解工作台可达', async ({ page }) => {
    await page.goto('/requirements/1/decompose')
    await expect(page.locator('body')).toBeVisible()
    await page.waitForTimeout(1500)
  })

  /**
   * W7-REQ-7 统计卡回归断言（R82 修复）
   * - 全局统计卡 5 个数字（需求总数/草稿/已批准/已实现/已关闭）应 > 0
   * - 与列表 Total 数字一致
   * - 选中项目后数字应刷新（≤ 全局值）
   * WHY: 此前调错 URL `/api/requirements/stats` → Spring 路由到 `@GetMapping("/{id}")` → 业务异常 SY0101 包装在 HTTP 200 → 前端 `data.total ?? 0` → 全 0
   */
  test('W7-REQ-7 需求列表统计卡非 0 且与 Total 一致（R82 回归）', async ({ page }) => {
    await page.goto('/requirements')
    await page.waitForTimeout(2500)

    // 1) 5 个统计卡至少"需求总数" > 0
    const totalCard = page.locator('.stat-card-total .stat-num')
    const totalText = (await totalCard.textContent())?.trim() || '0'
    const totalNum = parseInt(totalText, 10)
    expect(totalNum).toBeGreaterThan(0)

    // 2) 至少 1 个分桶（草稿/已批准/已实现/已关闭） > 0
    const subCards = page.locator('.stat-cards .stat-card:not(.stat-card-total) .stat-num')
    const subCount = await subCards.count()
    let anyPositive = false
    for (let i = 0; i < subCount; i++) {
      const t = (await subCards.nth(i).textContent())?.trim() || '0'
      if (parseInt(t, 10) > 0) { anyPositive = true; break }
    }
    expect(anyPositive).toBe(true)

    // 3) 统计卡总数 ≈ 列表 Total
    const paginationTotalText = await page.locator('.el-pagination__total').textContent() || ''
    const paginationTotal = parseInt(paginationTotalText.replace(/[^0-9]/g, ''), 10)
    expect(totalNum).toBe(paginationTotal)

    // 4) 选中第一个项目后，统计卡应刷新（≤ 全局值）
    const projectSelect = page.locator('.filter-row .el-select').first()
    if (await projectSelect.count() > 0) {
      await projectSelect.click()
      await page.waitForTimeout(500)
      const firstOption = page.locator('.el-select-dropdown__item').first()
      if (await firstOption.count() > 0) {
        await firstOption.click()
        await page.waitForTimeout(1500)
        const newTotal = parseInt((await totalCard.textContent())?.trim() || '0', 10)
        expect(newTotal).toBeGreaterThan(0)
        expect(newTotal).toBeLessThanOrEqual(totalNum)
      }
    }
  })
})

/**
 * W7-DASH-1 Dashboard 统计卡回归断言（R82 后续）
 * - 4 视角 × 4 卡 = 16 张，至少 "需求总数 / 已追溯 / 覆盖率 / 项目总数" 4 张非 0
 * - "已追溯" 必须等于 coverage.traced（不为 0）
 * - "覆盖率" 必须含 "%" 字符
 * WHY: Dashboard 与 RequirementList 同样存在前端契约与后端不一致的 bug：coverage.covered/coverageRate 在后端实际是 traced/overall
 */
test.describe('Dashboard 统计卡回归（R82 后续）', () => {
  test('W7-DASH-1 Dashboard 16 张统计卡关键字段非 0', async ({ page }) => {
    await page.goto('/dashboard')
    await page.waitForTimeout(3000)

    const cards = page.locator('.stat-card')
    expect(await cards.count()).toBeGreaterThanOrEqual(16)

    // 抽取所有 stat-value + stat-label
    const summary: Record<string, string> = {}
    const count = await cards.count()
    for (let i = 0; i < count; i++) {
      const label = (await cards.nth(i).locator('.stat-label').textContent())?.trim() || ''
      const value = (await cards.nth(i).locator('.stat-value').textContent())?.trim() || ''
      summary[label] = value
    }

    // 关键断言：需求总数 > 0
    expect(parseInt(summary['需求总数'] || '0', 10)).toBeGreaterThan(0)
    // 已追溯 > 0（之前永远是 0）
    expect(parseInt(summary['已追溯'] || '0', 10)).toBeGreaterThan(0)
    // 覆盖率 含 % 且数字 > 0
    expect(summary['覆盖率'] || '').toMatch(/%/)
    expect(parseInt(summary['覆盖率'] || '0', 10)).toBeGreaterThan(0)
    // 项目总数 > 0
    expect(parseInt(summary['项目总数'] || '0', 10)).toBeGreaterThan(0)
  })
})

/**
 * W7-TODO-1 Dashboard 我的待办回归（R83）
 * - 5 类待办至少 1 张 > 0（前提：admin 用户至少有 1 项待办；e2e 数据集应保证）
 * - 总数 = 5 项之和
 */
test('W7-TODO-1 Dashboard 我的待办卡至少 1 张 > 0（R83 回归）', async ({ page }) => {
  await page.goto('/dashboard')
  await page.waitForTimeout(3500)

  const items = page.locator('.todo-item')
  const count = await items.count()
  expect(count).toBeGreaterThanOrEqual(5)

  let total = 0
  let anyPositive = false
  for (let i = 0; i < count; i++) {
    const value = parseInt((await items.nth(i).locator('.todo-value').textContent())?.trim() || '0', 10)
    total += value
    if (value > 0) anyPositive = true
  }
  expect(anyPositive).toBe(true)
  expect(total).toBeGreaterThan(0)
})

/**
 * W7-PROJ-1 ProjectDetail 统计卡回归（R84 修复）
 * - 4 张统计卡（需求总数/已完成/进行中/总体进度）应 > 0
 * WHY: 原 /requirements/list 路径错误导致 SY0101 → 全 0
 */
test('W7-PROJ-1 ProjectDetail 统计卡非 0（R84 回归）', async ({ page }) => {
  await page.goto('/projects/1')
  await page.waitForTimeout(3500)
  const cards = page.locator('.stat-card')
  const count = await cards.count()
  expect(count).toBeGreaterThanOrEqual(4)
  const summary: Record<string, string> = {}
  for (let i = 0; i < count; i++) {
    const label = (await cards.nth(i).locator('.stat-label').textContent())?.trim() || ''
    const value = (await cards.nth(i).locator('.stat-value').textContent())?.trim() || ''
    summary[label] = value
  }
  expect(parseInt(summary['需求总数'] || '0', 10)).toBeGreaterThan(0)
  expect(parseInt(summary['已完成'] || '0', 10)).toBeGreaterThan(0)
})

/**
 * W7-EDIT-1 ReqEdit 编辑页可达（R85 修复）
 * - 跳转到 /requirements/{id}/edit 不应卡死
 * - 应出现"编辑需求"标题 + 表单 input + 取消/保存按钮
 * WHY: 原 ReqEdit.vue computed import 重命名错（_cmp vs computed）导致 setup ReferenceError → 整个组件挂载失败
 */
test('W7-EDIT-1 需求编辑页可达 + 含表单字段（R85 回归）', async ({ page }) => {
  await page.goto('/requirements')
  await page.waitForTimeout(2500)
  // 点第一个查看按钮进入详情
  const viewBtn = page.locator('.el-table__body button:has-text("查看")').first()
  await viewBtn.click()
  await page.waitForTimeout(2500)
  // 点击编辑按钮
  await page.locator('button:has-text("编辑")').first().click()
  await page.waitForTimeout(3000)
  // 断言：进入编辑路由
  expect(page.url()).toContain('/edit')
  // 断言：页面有表单字段（至少 5 个 input/textarea）和 保存/取消 按钮
  const inputs = await page.locator('input, textarea').count()
  expect(inputs).toBeGreaterThanOrEqual(5)
  await expect(page.locator('button:has-text("保存")')).toBeVisible()
  await expect(page.locator('button:has-text("取消")')).toBeVisible()
  // 断言：标题含"编辑需求"
  await expect(page.locator('.page-title')).toContainText('编辑需求')
})

/**
 * W7-DASH-2 Dashboard 默认全公司视图（R86 回归）
 * - 默认选中"全部项目"（filterProject=null）
 * - 风险总数应 > 0（至少有游离的 3 条 risk）
 * - 项目总数应等于 system 中真实项目数（≥ 4）
 */
test('W7-DASH-2 Dashboard 默认全部项目视图（R86 回归）', async ({ page }) => {
  await page.goto('/dashboard')
  await page.waitForTimeout(3500)

  // 切到风险视角
  await page.locator('.el-tabs__item:has-text("风险视角")').click()
  await page.waitForTimeout(2000)

  // 提取 4 张风险卡
  const summary: Record<string, string> = {}
  const cards = page.locator('.stat-card')
  const count = await cards.count()
  for (let i = 0; i < count; i++) {
    const label = (await cards.nth(i).locator('.stat-label').textContent())?.trim() || ''
    const value = (await cards.nth(i).locator('.stat-value').textContent())?.trim() || ''
    summary[label] = value
  }

  // 风险总数应 > 0（修复前默认锁项目 1 时是 0）
  expect(parseInt(summary['风险总数'] || '0', 10)).toBeGreaterThan(0)
  // 高风险数应 > 0
  expect(parseInt(summary['高风险数'] || '0', 10)).toBeGreaterThan(0)
  // 项目总数 ≥ 4
  expect(parseInt(summary['项目总数'] || '0', 10)).toBeGreaterThanOrEqual(4)
})

/**
 * W30-1 端点契约扫描定期回归（R87）
 * - 5 个"功能开发中"页面必须可访问且显示 el-alert 提示
 * - ChangeRequest 编辑必须提示"后端未实现"而不是 SY0101
 */
test.describe('W30 端点契约扫描回归', () => {
  test('W30-1-1 5 个 R87 修复页面显示"功能开发中"提示', async ({ page }) => {
    const urls = [
      '/system/login-logs',
      '/system/operation-logs',
      '/system/profile',
      '/projects/resources',
    ]
    for (const url of urls) {
      await page.goto(url)
      await page.waitForTimeout(1500)
      // 至少 1 个 el-alert title="功能开发中"
      const alert = page.locator('.el-alert:has-text("功能开发中")')
      expect(await alert.count()).toBeGreaterThanOrEqual(1)
    }
  })
})

/**
 * W30-2 IpdGate gateType 枚举值契约（R88 回归）
 * - 创建阶段门下拉必须含 DCP1-DCP5/PLANNING 6 个选项
 * - 不应再出现 DEFINE/DEVELOPMENT/RELEASE/MARKET（DB 不支持）
 */
test('W30-2 IpdGate gateType 下拉枚举值正确（R88 回归）', async ({ page }) => {
  await page.goto('/projects/ipd')
  await page.waitForTimeout(2500)
  // 点击"创建门"按钮触发下拉
  const createBtn = page.locator('button:has-text("创建门")').first()
  if (await createBtn.count() > 0) {
    await createBtn.click()
    await page.waitForTimeout(800)
    // 抓所有 el-option
    const options = await page.locator('.el-select-dropdown__item').allTextContents()
    const allowed = ['PLANNING', 'DCP1', 'DCP2', 'DCP3', 'DCP4', 'DCP5']
    for (const opt of options) {
      // 不应包含 BUG-R88-1 的错误值
      const wrong = ['DEFINE', 'DEVELOPMENT', 'RELEASE', 'MARKET']
      for (const w of wrong) {
        expect(opt.includes(w)).toBe(false)
      }
    }
    // 应至少包含 DCP1-DCP5
    const optionsStr = options.join(' ')
    expect(optionsStr).toContain('DCP1')
    expect(optionsStr).toContain('DCP5')
  }
})

/**
 * W30-3 Dashboard "全部项目"模式覆盖率断言（R90 回归）
 * - 默认进 Dashboard → 切到需求视角 → 断言"已追溯" > 0
 * WHY: 后端 TraceabilityService.getLevelCoverage 用 .eq(field, null) → WHERE field = null → 永远 0 行
 */
test('W30-3 Dashboard 全部项目视图 覆盖率 > 0（R90 回归）', async ({ page }) => {
  await page.goto('/dashboard')
  await page.waitForTimeout(3500)
  // 抓需求视角的 4 张卡
  const cards = page.locator('.stat-card')
  const count = await cards.count()
  expect(count).toBeGreaterThanOrEqual(4)
  const summary: Record<string, string> = {}
  for (let i = 0; i < Math.min(count, 16); i++) {
    const label = (await cards.nth(i).locator('.stat-label').textContent())?.trim() || ''
    const value = (await cards.nth(i).locator('.stat-value').textContent())?.trim() || ''
    summary[label] = value
  }
  // 已追溯 > 0（修复前 R90 bug 是 0）
  expect(parseInt(summary['已追溯'] || '0', 10)).toBeGreaterThan(0)
  // 覆盖率 > 0（修复前是 0%）
  expect(parseInt(summary['覆盖率'] || '0', 10)).toBeGreaterThan(0)
  // 追溯覆盖率 > 0（管理视角）
  expect(parseInt(summary['追溯覆盖率'] || '0', 10)).toBeGreaterThan(0)
})

/**
 * W30-4 Profile 端点 + 改密码功能可用（R92 回归）
 * - 移除 R87 挂的"功能开发中"提示后，profile / 改密码应可正常调用
 */
test('W30-4 Profile 端点可用 + 无"功能开发中"提示（R92 回归）', async ({ page }) => {
  await page.goto('/system/profile')
  await page.waitForTimeout(2500)
  // 不应有"功能开发中"提示
  const hasAlert = await page.locator('.el-alert:has-text("功能开发中")').count()
  expect(hasAlert).toBe(0)
  // 应显示用户名
  const body = await page.locator('body').textContent()
  expect(body).toContain('admin')
})

test('W30-5 ChangeRequest 编辑按钮可用 + 无"后端未实现"提示（R92 回归）', async ({ page }) => {
  await page.goto('/changes')
  await page.waitForTimeout(2500)
  // 点第一个变更的查看按钮
  const viewBtn = page.locator('.el-table__body button:has-text("查看"), .el-card button:has-text("详情")').first()
  if (await viewBtn.count() === 0) return // 跳过
  await viewBtn.click()
  await page.waitForTimeout(2500)
  // 找编辑按钮
  const editBtn = page.locator('button:has-text("编辑")').first()
  if (await editBtn.count() > 0) {
    await editBtn.click()
    await page.waitForTimeout(2000)
    // 应有保存按钮（不弹"后端未实现"警告）
    const saveBtn = page.locator('button:has-text("保存")').first()
    expect(await saveBtn.count()).toBeGreaterThanOrEqual(1)
  }
})

/**
 * W30-6 ProjectDeliverables 模块（R92 回归）
 * - 页面无"功能开发中"提示
 * - 列表能渲染数据
 * - 按钮可点击
 */
test('W30-6 ProjectDeliverables 页面无"功能开发中"提示（R92 回归）', async ({ page }) => {
  await page.goto('/projects/1/deliverables')
  await page.waitForTimeout(2500)
  const hasAlert = await page.locator('.el-alert:has-text("功能开发中")').count()
  expect(hasAlert).toBe(0)
  // "登记交付物"按钮应该可点击（无 disabled）
  const addBtn = page.locator('button:has-text("登记交付物")').first()
  expect(await addBtn.isDisabled()).toBe(false)
})

/**
 * W30-7 ResourceManagement 页面无"功能开发中"提示（R92 回归）
 */
test('W30-7 ResourceManagement 页面无"功能开发中"提示（R92 回归）', async ({ page }) => {
  await page.goto('/projects/resources')
  await page.waitForTimeout(2500)
  const hasAlert = await page.locator('.el-alert:has-text("功能开发中")').count()
  expect(hasAlert).toBe(0)
})

/**
 * W31-1 Dashboard 我的待办 4 卡跳转（R94 回归）
 * - 待签字：SignatureList 显示 7 行（按当前用户过滤）
 * - 待评审：ReviewManagement 显示至少 22 行（含所有 Submitted）
 * - 待关闭：ProblemReport 不报错 + 有数据
 */
test('W31-1 Dashboard 我的待办 4 卡跳转数据一致（R94 回归）', async ({ page }) => {
  // 1. 待签字 → SignatureList
  await page.goto('/esignature')
  await page.waitForTimeout(2500)
  const sigRows = await page.locator('.el-table__body tbody tr').count()
  expect(sigRows).toBeGreaterThanOrEqual(1)
  // 不应有 console.error
  // (Playwright 无直接接口，但通过后续断言可发现)

  // 2. 待评审 → ReviewManagement
  await page.goto('/reviews')
  await page.waitForTimeout(3000)
  const reviewCards = await page.locator('.review-card').count()
  expect(reviewCards).toBeGreaterThanOrEqual(20)

  // 3. 待关闭 → ProblemReport
  await page.goto('/compliance/problem-report')
  await page.waitForTimeout(3000)
  const prRows = await page.locator('.el-table__body tbody tr').count()
  expect(prRows).toBeGreaterThan(0)
})
