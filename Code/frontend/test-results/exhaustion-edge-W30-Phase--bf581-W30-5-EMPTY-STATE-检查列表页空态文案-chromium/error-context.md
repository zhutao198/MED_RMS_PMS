# Instructions

- Following Playwright test failed.
- Explain why, be concise, respect Playwright best practices.
- Provide a snippet of code with the fix, if possible.

# Test info

- Name: exhaustion-edge.spec.ts >> W30 Phase 5 边界场景扫描 >> W30-5-EMPTY-STATE 检查列表页空态文案
- Location: e2e\exhaustion-edge.spec.ts:78:3

# Error details

```
Error: expect(received).toContain(expected) // indexOf

Expected substring: "暂无"
Received string:    "
    🏥 Med-RMS 医疗器械需求管理系统🔔1心电监护仪 v3.0admin_updated（系统管理员）📊 仪表盘📋 需求管理概览🗂 需求看板🎯 质量评分🤖 AI 辅助分析🔨 需求拆解📥 需求池🔨 需求→任务🧪 测试用例🔗 追溯管理概览📥 追溯导入🕸️ 追溯图谱📝 变更管理概览✅ 我的审批✅ 合规管理概览📋 IEC 62304 清单📦 DHF 证据包📤 NMPA eRPS 导出📜 法规影响分析📋 合规模板✍️ 电子签名⚠️ 风险管理概览🛠 FMEA 编辑器🌡 风险矩阵📈 风险监控📁 项目管理概览📅 甘特图🚦 IPD 阶段门🎯 里程碑👥 资源管理⏱ 工时统计📋 任务看板🕐 活动流🔍 审计追踪📊 报表与审计概览📤 报告导出🔐 审计日志⚙️ 系统管理概览👤 个人中心🏢 组织架构📋 操作日志🔑 登录日志🔐 角色权限📥 数据迁移🔔 通知📅 甘特图（FR-2.7 含依赖+关键路径）- 0重算关键路径新建任务请先选择项目·········
📋 全部项目PRJ-000003 - 8333PRJ-ECG3-001 - 心电监护仪 v3.0PRJ-R150-TEST - R150 集成测试专用项目"
```

# Page snapshot

```yaml
- generic [ref=e3]:
  - generic [ref=e4]:
    - heading "🏥 Med-RMS 医疗器械需求管理系统" [level=1] [ref=e5]
    - generic [ref=e6]:
      - generic [ref=e7]:
        - generic [ref=e8] [cursor=pointer]: 🔔
        - superscript [ref=e9]: "1"
      - generic [ref=e11]: 心电监护仪 v3.0
      - generic [ref=e12]: admin_updated（系统管理员）
  - generic [ref=e13]:
    - menubar [ref=e15]:
      - menuitem "📊 仪表盘" [ref=e16] [cursor=pointer]
      - menuitem "📋 需求管理" [expanded] [ref=e17]:
        - generic [ref=e18] [cursor=pointer]:
          - generic [ref=e19]: 📋 需求管理
          - img [ref=e21]
        - menu [ref=e23]:
          - menuitem "概览" [ref=e24] [cursor=pointer]
          - menuitem "🗂 需求看板" [ref=e25] [cursor=pointer]
          - menuitem "🎯 质量评分" [ref=e26] [cursor=pointer]
          - menuitem "🤖 AI 辅助分析" [ref=e27] [cursor=pointer]
          - menuitem "🔨 需求拆解" [ref=e28] [cursor=pointer]
          - menuitem "📥 需求池" [ref=e29] [cursor=pointer]
          - menuitem "🔨 需求→任务" [ref=e30] [cursor=pointer]
          - menuitem "🧪 测试用例" [ref=e31] [cursor=pointer]
      - menuitem "🔗 追溯管理" [expanded] [ref=e32]:
        - generic [ref=e33] [cursor=pointer]:
          - generic [ref=e34]: 🔗 追溯管理
          - img [ref=e36]
        - menu [ref=e38]:
          - menuitem "概览" [ref=e39] [cursor=pointer]
          - menuitem "📥 追溯导入" [ref=e40] [cursor=pointer]
          - menuitem "🕸️ 追溯图谱" [ref=e41] [cursor=pointer]
      - menuitem "📝 变更管理" [expanded] [ref=e42]:
        - generic [ref=e43] [cursor=pointer]:
          - generic [ref=e44]: 📝 变更管理
          - img [ref=e46]
        - menu [ref=e48]:
          - menuitem "概览" [ref=e49] [cursor=pointer]
          - menuitem "✅ 我的审批" [ref=e50] [cursor=pointer]
      - menuitem "✅ 合规管理" [expanded] [ref=e51]:
        - generic [ref=e52] [cursor=pointer]:
          - generic [ref=e53]: ✅ 合规管理
          - img [ref=e55]
        - menu [ref=e57]:
          - menuitem "概览" [ref=e58] [cursor=pointer]
          - menuitem "📋 IEC 62304 清单" [ref=e59] [cursor=pointer]
          - menuitem "📦 DHF 证据包" [ref=e60] [cursor=pointer]
          - menuitem "📤 NMPA eRPS 导出" [ref=e61] [cursor=pointer]
          - menuitem "📜 法规影响分析" [ref=e62] [cursor=pointer]
          - menuitem "📋 合规模板" [ref=e63] [cursor=pointer]
      - menuitem "✍️ 电子签名" [ref=e64] [cursor=pointer]
      - menuitem "⚠️ 风险管理" [expanded] [ref=e65]:
        - generic [ref=e66] [cursor=pointer]:
          - generic [ref=e67]: ⚠️ 风险管理
          - img [ref=e69]
        - menu [ref=e71]:
          - menuitem "概览" [ref=e72] [cursor=pointer]
          - menuitem "🛠 FMEA 编辑器" [ref=e73] [cursor=pointer]
          - menuitem "🌡 风险矩阵" [ref=e74] [cursor=pointer]
          - menuitem "📈 风险监控" [ref=e75] [cursor=pointer]
      - menuitem "📁 项目管理" [expanded] [ref=e76]:
        - generic [ref=e77] [cursor=pointer]:
          - generic [ref=e78]: 📁 项目管理
          - img [ref=e80]
        - menu [ref=e82]:
          - menuitem "概览" [ref=e83] [cursor=pointer]
          - menuitem "📅 甘特图" [ref=e84] [cursor=pointer]
          - menuitem "🚦 IPD 阶段门" [ref=e85] [cursor=pointer]
          - menuitem "🎯 里程碑" [ref=e86] [cursor=pointer]
          - menuitem "👥 资源管理" [ref=e87] [cursor=pointer]
          - menuitem "⏱ 工时统计" [ref=e88] [cursor=pointer]
          - menuitem "📋 任务看板" [ref=e89] [cursor=pointer]
          - menuitem "🕐 活动流" [ref=e90] [cursor=pointer]
          - menuitem "🔍 审计追踪" [ref=e91] [cursor=pointer]
      - menuitem "📊 报表与审计" [expanded] [ref=e92]:
        - generic [ref=e93] [cursor=pointer]:
          - generic [ref=e94]: 📊 报表与审计
          - img [ref=e96]
        - menu [ref=e98]:
          - menuitem "概览" [ref=e99] [cursor=pointer]
          - menuitem "📤 报告导出" [ref=e100] [cursor=pointer]
          - menuitem "🔐 审计日志" [ref=e101] [cursor=pointer]
      - menuitem "⚙️ 系统管理" [expanded] [ref=e102]:
        - generic [ref=e103] [cursor=pointer]:
          - generic [ref=e104]: ⚙️ 系统管理
          - img [ref=e106]
        - menu [ref=e108]:
          - menuitem "概览" [ref=e109] [cursor=pointer]
          - menuitem "👤 个人中心" [ref=e110] [cursor=pointer]
          - menuitem "🏢 组织架构" [ref=e111] [cursor=pointer]
          - menuitem "📋 操作日志" [ref=e112] [cursor=pointer]
          - menuitem "🔑 登录日志" [ref=e113] [cursor=pointer]
          - menuitem "🔐 角色权限" [ref=e114] [cursor=pointer]
          - menuitem "📥 数据迁移" [ref=e115] [cursor=pointer]
      - menuitem "🔔 通知" [ref=e116] [cursor=pointer]
    - generic [ref=e119]:
      - generic [ref=e121]:
        - generic [ref=e122]: 📅 甘特图（FR-2.7 含依赖+关键路径）-
        - generic [ref=e123]:
          - generic [ref=e125]:
            - generic:
              - combobox [ref=e127]
              - generic [ref=e128]: "0"
            - img [ref=e131] [cursor=pointer]
          - button "重算关键路径" [disabled] [ref=e133]:
            - generic [ref=e134]: 重算关键路径
          - button "新建任务" [ref=e135] [cursor=pointer]:
            - generic [ref=e136]: 新建任务
      - generic [ref=e139]:
        - img [ref=e141]
        - paragraph [ref=e158]: 请先选择项目
```

# Test source

```ts
  1  | // W30 Phase 5: 边界场景扫描
  2  | // 检查每个有筛选器的页面是否提供"全部"选项 + 空态文案 + 必填校验
  3  | import { test, expect } from '@playwright/test'
  4  | import { setupAuthForPage } from './auth-helper'
  5  | 
  6  | /**
  7  |  * Phase 5 检查矩阵
  8  |  * 每页检查：
  9  |  * 1. 有 .filter-row 页面 → 检查每个 .el-select 是否提供"全部"选项（value 包含 全部/All）
  10 |  * 2. 列表页空态是否有"暂无/无数据"文案
  11 |  * 3. 表单页必填项是否有 * 标记
  12 |  */
  13 | const PAGES_WITH_FILTER = [
  14 |   { name: 'RequirementList', url: '/requirements', filterSelectors: ['.filter-row .el-select'] },
  15 |   { name: 'TestCaseList', url: '/testcases', filterSelectors: ['.filter-row .el-select'] },
  16 |   { name: 'ChangeList', url: '/changes', filterSelectors: ['.filter-row .el-select'] },
  17 |   { name: 'ChangeApprovals', url: '/changes/approvals', filterSelectors: ['.filter-row .el-select'] },
  18 |   { name: 'RiskRegister', url: '/risk/register', filterSelectors: ['.el-select'] },
  19 |   { name: 'RisksMatrix', url: '/risks/matrix', filterSelectors: ['.el-select'] },
  20 |   { name: 'FmeaEditor', url: '/risk/fmea', filterSelectors: ['.el-select'] },
  21 |   { name: 'ProjectList', url: '/projects', filterSelectors: ['.el-select'] },
  22 |   { name: 'GanttView', url: '/projects/gantt', filterSelectors: ['.el-select'] },
  23 |   { name: 'IpdGate', url: '/projects/ipd', filterSelectors: ['.el-select'] },
  24 |   { name: 'TraceabilityCoverage', url: '/traceability/coverage', filterSelectors: ['.el-select'] },
  25 |   { name: 'TraceabilityGaps', url: '/traceability/gaps', filterSelectors: ['.el-select'] },
  26 |   { name: 'TraceGraph', url: '/trace-graph', filterSelectors: ['.el-select'] },
  27 |   { name: 'ResourceManagement', url: '/projects/resources', filterSelectors: ['.el-select'] },
  28 | ]
  29 | 
  30 | test.describe('W30 Phase 5 边界场景扫描', () => {
  31 |   test('W30-5-ALL-OPTION 检查所有有筛选器的页面是否提供"全部"选项', async ({ page }) => {
  32 |     test.setTimeout(60000)
  33 |     await setupAuthForPage(page)
  34 |     const report: any[] = []
  35 |     for (const pg of PAGES_WITH_FILTER) {
  36 |       await page.goto(pg.url, { timeout: 15000 }).catch(() => {})
  37 |       await page.waitForTimeout(1000)
  38 | 
  39 |       const allSelects: any[] = []
  40 |       const selectors = await page.locator('.el-select, .el-cascader').count()
  41 |       for (let i = 0; i < Math.min(selectors, 5); i++) {
  42 |         try {
  43 |           await page.locator('.el-select, .el-cascader').nth(i).click()
  44 |           await page.waitForTimeout(400)
  45 |           const items = await page.locator('.el-select-dropdown__item').allTextContents()
  46 |           const hasAll = items.some(t => /全部|All|all/i.test(t))
  47 |           const value = await page.locator('.el-select, .el-cascader').nth(i).locator('input').first().inputValue().catch(() => '')
  48 |           allSelects.push({ index: i, hasAll, items: items.slice(0, 8), value })
  49 |           // 关闭
  50 |           await page.keyboard.press('Escape')
  51 |           await page.waitForTimeout(200)
  52 |         } catch (e) {
  53 |           allSelects.push({ index: i, error: String(e).slice(0, 80) })
  54 |         }
  55 |       }
  56 |       report.push({ page: pg.name, url: pg.url, selects: allSelects })
  57 |     }
  58 | 
  59 |     // 汇总
  60 |     const issues: any[] = []
  61 |     for (const r of report) {
  62 |       for (const s of r.selects) {
  63 |         if (!s.hasAll && !s.error && s.value !== undefined) {
  64 |           // 当前有值的筛选器必须有"全部"选项
  65 |           issues.push({ page: r.page, selectorIndex: s.index, value: s.value, items: s.items })
  66 |         }
  67 |       }
  68 |     }
  69 | 
  70 |     console.log('FILTER OPTION REPORT:')
  71 |     console.log(JSON.stringify(report, null, 2))
  72 |     console.log('ISSUES (无"全部"选项的筛选器):')
  73 |     console.log(JSON.stringify(issues, null, 2))
  74 | 
  75 |     expect(issues.length).toBeLessThan(10) // 软上限
  76 |   })
  77 | 
  78 |   test('W30-5-EMPTY-STATE 检查列表页空态文案', async ({ page }) => {
  79 |     await setupAuthForPage(page)
  80 |     // 访问已知空数据的页面（GanttView 暂无数据已确认）
  81 |     await page.goto('/projects/gantt', { timeout: 15000 }).catch(() => {})
  82 |     await page.waitForTimeout(2500)
  83 |     const body = await page.locator('body').textContent()
  84 |     // GanttView "暂无甘特图数据" 应该有
> 85 |     expect(body).toContain('暂无') // 任何空态提示
     |                  ^ Error: expect(received).toContain(expected) // indexOf
  86 |   })
  87 | })
```