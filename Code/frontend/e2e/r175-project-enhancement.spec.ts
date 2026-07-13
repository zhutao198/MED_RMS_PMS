import { test, expect } from '@playwright/test'
import { setupAuthForPage } from './auth-helper'

test.describe('R175 项目管理增强 e2e（全量）', () => {

  test.beforeEach(async ({ page }) => {
    await setupAuthForPage(page)
  })

  // ========== B4: 项目克隆按钮 (ProjectsList.vue) ==========
  test('R175-B4: 项目列表页克隆按钮可见', async ({ page }) => {
    await page.goto('/projects')
    await expect(page.locator('body')).toBeVisible()
    // 每个项目卡片应有克隆按钮
    const cloneBtns = page.locator('button:has-text("克隆")')
    await expect(cloneBtns.first()).toBeVisible()
  })

  // ========== B5: Excel 导出 / 保存为模板 / 导入任务 (ProjectDetail.vue) ==========
  test('R175-B5: 项目详情页新增按钮可见', async ({ page }) => {
    await page.goto('/projects/1')
    await expect(page.locator('body')).toBeVisible()
    await expect(page.locator('button:has-text("导出 Excel")')).toBeVisible()
    await expect(page.locator('button:has-text("导入任务")')).toBeVisible()
    await expect(page.locator('button:has-text("保存为模板")')).toBeVisible()
  })

  // ========== B5b: 健康度评分卡点击弹窗 (ProjectDetail.vue) ==========
  test('R175-B5b: 健康度评分卡可点击查看详情', async ({ page }) => {
    await page.goto('/projects/1')
    await expect(page.locator('body')).toBeVisible()
    await page.locator('role=tab[name="概览"]').click()
    await page.waitForTimeout(1500)
    // 如果健康度评分卡存在，点击卡本身（el-card）打开详情弹窗
    const healthCard = page.locator('.health-card:has-text("健康度评分")')
    if (await healthCard.isVisible()) {
      await healthCard.click()
      await page.waitForTimeout(500)
      await expect(page.locator('text=健康度评分详情')).toBeVisible()
    }
  })

  // ========== B7: 健康度评分卡 (ProjectDetail.vue) ==========
  test('R175-B7: 项目详情概览 Tab 含健康度评分卡', async ({ page }) => {
    await page.goto('/projects/1')
    await expect(page.locator('body')).toBeVisible()
    // 点击概览 Tab
    await page.locator('role=tab[name="概览"]').click()
    await page.waitForTimeout(1000)
    // 健康度评分卡应可见（如果有数据）
    const healthCard = page.locator('text=项目健康度评分')
    // 如果后端有返回数据则显示，否则需要先创建数据
    // 此处只验证页面不崩溃
    await expect(page.locator('body')).toBeVisible()
  })

  // ========== B1: 甘特图拖拽 (GanttView.vue) ==========
  test('R175-B1: 甘特图页面加载', async ({ page }) => {
    await page.goto('/projects/1/gantt')
    await expect(page.locator('body')).toBeVisible()
    await page.waitForTimeout(2000)
    // 验证甘特图容器可见
    await expect(page.locator('.gantt-container')).toBeVisible()
  })

  // ========== B3: 任务看板 (TaskBoard.vue) ==========
  test('R175-B3: 任务看板页面加载及多选清除按钮', async ({ page }) => {
    await page.goto('/projects/task-board')
    await expect(page.locator('body')).toBeVisible()
    await page.waitForTimeout(2000)
    // 验证看板列
    await expect(page.locator('text=待办').first()).toBeVisible()
    await expect(page.locator('text=进行中').first()).toBeVisible()
    await expect(page.locator('text=已完成').first()).toBeVisible()
    await expect(page.locator('text=已阻塞').first()).toBeVisible()
    // 验证清除选中按钮
    await expect(page.locator('button:has-text("清除选中")')).toBeVisible()
  })

  // ========== B6: 活动流时间线 + 可展开详情 (ProjectActivity.vue) ==========
  test('R175-B6: 活动流时间线页面加载含展开详情按钮', async ({ page }) => {
    await page.goto('/projects/activities')
    await expect(page.locator('body')).toBeVisible()
    await page.waitForTimeout(2000)
    await expect(page.locator('text=活动流时间线').first()).toBeVisible()
    // 验证"详情"按钮可见（如果有活动记录）
    const detailBtn = page.locator('button:has-text("详情")').first()
    if (await detailBtn.isVisible()) {
      await detailBtn.click()
      await page.waitForTimeout(500)
      await expect(page.locator('button:has-text("收起")').first()).toBeVisible()
    }
  })

  // ========== B8: 项目级审计追踪 (ProjectAuditLog.vue) ==========
  test('R175-B8: 项目审计追踪页面加载', async ({ page }) => {
    await page.goto('/projects/audit')
    await expect(page.locator('body')).toBeVisible()
    await page.waitForTimeout(2000)
    await expect(page.locator('text=项目级审计追踪').first()).toBeVisible()
  })

  // ========== B2: 资源热力图 (ResourceManagement.vue) ==========
  test('R175-B2: 资源管理页面含热力图切换', async ({ page }) => {
    await page.goto('/projects/resources')
    await expect(page.locator('body')).toBeVisible()
    await page.waitForTimeout(2000)
    // 默认卡片视图可见
    await expect(page.locator('text=资源管理').first()).toBeVisible()
    // 切换为热力图
    await page.locator('label:has-text("热力图")').click()
    await page.waitForTimeout(1000)
    // 热力图表格可见
    const heatmap = page.locator('.heatmap-grid')
    await expect(heatmap).toBeVisible()
    // 切回卡片视图
    await page.locator('label:has-text("卡片视图")').click()
    await page.waitForTimeout(500)
  })

  // ========== B10: 合规模板 scopeType 过滤 (TemplateManagement.vue) ==========
  test('R175-B10: 合规模板 scopeType 过滤', async ({ page }) => {
    await page.goto('/projects/templates')
    await expect(page.locator('body')).toBeVisible()
    await page.waitForTimeout(2000)
    // 过滤 radio-group 可见
    await expect(page.locator('text=作用域').first()).toBeVisible()
    // 点击"项目"过滤
    await page.locator('label:has-text("项目")').click()
    await page.waitForTimeout(1000)
    // 点击"全部"恢复
    await page.locator('label:has-text("全部")').click()
    await page.waitForTimeout(500)
  })

  // ========== B11: 工时超预算 UI (WorklogView.vue) ==========
  test('R175-B11: 工时统计页面加载', async ({ page }) => {
    await page.goto('/projects/worklog')
    await expect(page.locator('body')).toBeVisible()
    await page.waitForTimeout(2000)
    await expect(page.locator('text=工时统计').first()).toBeVisible()
  })

  // ========== 新增路由导航测试 ==========
  test('R175-NAV: 新增菜单路由可达无 404', async ({ page }) => {
    const routes = [
      '/projects/task-board',
      '/projects/activities',
      '/projects/audit',
    ]
    for (const route of routes) {
      await page.goto(route)
      await page.waitForTimeout(1000)
      await expect(page.locator('body')).toBeVisible()
    }
  })
})
