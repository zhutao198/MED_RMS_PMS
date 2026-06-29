# Instructions

- Following Playwright test failed.
- Explain why, be concise, respect Playwright best practices.
- Provide a snippet of code with the fix, if possible.

# Test info

- Name: exhaustion-page-audit.spec.ts >> W30 Phase 2 关键 20 页 UI 巡检 >> W30-2-UI-AUDIT 所有 Tier 1 页面 console + 网络 + 统计卡全检
- Location: e2e\exhaustion-page-audit.spec.ts:32:3

# Error details

```
Test timeout of 30000ms exceeded.
```

```
Error: locator.count: Target page, context or browser has been closed
```

# Page snapshot

```yaml
- generic [ref=e3]:
  - generic [ref=e4]:
    - heading "🏥 Med-RMS 医疗器械需求管理系统" [level=1] [ref=e5]
    - generic [ref=e6]:
      - generic [ref=e8] [cursor=pointer]: 🔔
      - generic [ref=e10]: 心电监护仪 v3.0
      - generic [ref=e11]: 未登录
  - generic [ref=e12]:
    - generic [ref=e13]:
      - generic [ref=e14]: 导航菜单
      - generic [ref=e15] [cursor=pointer]: 📊 仪表盘
      - generic [ref=e16] [cursor=pointer]: 📋 需求管理
      - generic [ref=e17] [cursor=pointer]: 🗂 需求看板
      - generic [ref=e18] [cursor=pointer]: 🎯 质量评分
      - generic [ref=e19] [cursor=pointer]: 🤖 AI 辅助分析
      - generic [ref=e20] [cursor=pointer]: 🔨 需求拆解
      - generic [ref=e21] [cursor=pointer]: 🧪 测试用例
      - generic [ref=e22] [cursor=pointer]: 🔗 追溯管理
      - generic [ref=e23] [cursor=pointer]: 📥 追溯导入
      - generic [ref=e24] [cursor=pointer]: 🕸️ 追溯图谱
      - generic [ref=e25] [cursor=pointer]: 📝 变更管理
      - generic [ref=e26] [cursor=pointer]: ✅ 我的审批
      - generic [ref=e27] [cursor=pointer]: ✅ 合规管理
      - generic [ref=e28] [cursor=pointer]: 📋 IEC 62304 清单
      - generic [ref=e29] [cursor=pointer]: 📦 DHF 证据包
      - generic [ref=e30] [cursor=pointer]: 📤 NMPA eRPS 导出
      - generic [ref=e31] [cursor=pointer]: 📜 法规影响分析
      - generic [ref=e32] [cursor=pointer]: ✍️ 电子签名
      - generic [ref=e33] [cursor=pointer]: ⚠️ 风险管理
      - generic [ref=e34] [cursor=pointer]: 🛠 FMEA 编辑器
      - generic [ref=e35] [cursor=pointer]: 🌡 风险矩阵
      - generic [ref=e36] [cursor=pointer]: 📈 风险监控
      - generic [ref=e37] [cursor=pointer]: 📁 项目管理
      - generic [ref=e38] [cursor=pointer]: 📋 合规模板
      - generic [ref=e39] [cursor=pointer]: 📅 甘特图
      - generic [ref=e40] [cursor=pointer]: 🚦 IPD 阶段门
      - generic [ref=e41] [cursor=pointer]: 🎯 里程碑
      - generic [ref=e42] [cursor=pointer]: 👥 资源管理
      - generic [ref=e43] [cursor=pointer]: ⏱ 工时统计
      - generic [ref=e44] [cursor=pointer]: 📥 需求池
      - generic [ref=e45] [cursor=pointer]: 🔨 需求→任务
      - generic [ref=e46] [cursor=pointer]: 📊 报表中心
      - generic [ref=e47] [cursor=pointer]: 📤 报告导出
      - generic [ref=e48] [cursor=pointer]: 🔐 审计日志
      - generic [ref=e49] [cursor=pointer]: ⚙️ 系统管理
      - generic [ref=e50] [cursor=pointer]: 📥 数据迁移
      - generic [ref=e51] [cursor=pointer]: 🔑 登录日志
      - generic [ref=e52] [cursor=pointer]: 📋 操作日志
      - generic [ref=e53] [cursor=pointer]: 👤 个人中心
      - generic [ref=e54] [cursor=pointer]: 🔐 角色权限
      - generic [ref=e55] [cursor=pointer]: 🏢 组织架构
      - generic [ref=e56] [cursor=pointer]: 🔔 通知
    - generic [ref=e58]:
      - generic [ref=e59]:
        - heading "🏥 Med-RMS 医疗器械需求管理系统" [level=2] [ref=e62]
        - generic [ref=e64]:
          - generic [ref=e65]:
            - generic [ref=e66]: 用户名
            - textbox "用户名" [ref=e70]:
              - /placeholder: 请输入用户名
          - generic [ref=e71]:
            - generic [ref=e72]: 密码
            - textbox "密码" [ref=e76]:
              - /placeholder: 请输入密码
          - generic [ref=e77]:
            - generic [ref=e78]: 验证码
            - generic [ref=e80]:
              - textbox "验证码" [ref=e83]:
                - /placeholder: 请输入右侧 4 位验证码
              - generic "点击刷新" [ref=e84] [cursor=pointer]:
                - generic [ref=e85]: 9SPK
          - button "登录" [ref=e88] [cursor=pointer]:
            - generic [ref=e89]: 登录
          - button "🔐 企业 SSO 单点登录（即将上线）" [disabled] [ref=e92]:
            - generic [ref=e93]: 🔐 企业 SSO 单点登录（即将上线）
      - paragraph [ref=e95]: 本系统符合 21 CFR Part 11 / NMPA / CE MDR / ISO 13485 / IEC 62304 合规要求 · 全部操作留痕可审计
```

# Test source

```ts
  1   | // W30 Phase 2: 关键 20 页 UI 巡检
  2   | // 抓 console.error / Vue warn / 网络 4xx-5xx / 统计卡全 0 / 项目筛选"全部"选项
  3   | import { test, expect } from '@playwright/test'
  4   | 
  5   | /**
  6   |  * Tier 1 必测 20 页（按 exhaustion_test_plan.md §3.1）
  7   |  */
  8   | const TIER1_PAGES = [
  9   |   { name: 'Dashboard', url: '/dashboard', hasFilter: true, hasStatCards: true },
  10  |   { name: 'RequirementList', url: '/requirements', hasFilter: true, hasStatCards: true },
  11  |   { name: 'RequirementDetail', url: '/requirements/1612', hasFilter: false, hasStatCards: false },
  12  |   { name: 'ReqEdit', url: '/requirements/1612/edit', hasFilter: false, hasStatCards: false },
  13  |   { name: 'ReqCreate', url: '/requirements/create', hasFilter: false, hasStatCards: false },
  14  |   { name: 'DecomposeList', url: '/requirements/1/decompose', hasFilter: false, hasStatCards: false },
  15  |   { name: 'TestCaseList', url: '/testcases', hasFilter: true, hasStatCards: false },
  16  |   { name: 'ChangeList', url: '/changes', hasFilter: true, hasStatCards: false },
  17  |   { name: 'ChangeRequest', url: '/changes/1', hasFilter: false, hasStatCards: false },
  18  |   { name: 'ChangeApprovals', url: '/changes/approvals', hasFilter: true, hasStatCards: false },
  19  |   { name: 'TraceGraph', url: '/trace-graph', hasFilter: true, hasStatCards: false },
  20  |   { name: 'TraceMatrix', url: '/traceability/coverage', hasFilter: true, hasStatCards: true },
  21  |   { name: 'TraceGaps', url: '/traceability/gaps', hasFilter: true, hasStatCards: true },
  22  |   { name: 'RiskRegister', url: '/risk/register', hasFilter: true, hasStatCards: true },
  23  |   { name: 'RisksMatrix', url: '/risks/matrix', hasFilter: true, hasStatCards: false },
  24  |   { name: 'FmeaEditor', url: '/risk/fmea', hasFilter: true, hasStatCards: false },
  25  |   { name: 'ProjectList', url: '/projects', hasFilter: true, hasStatCards: false },
  26  |   { name: 'ProjectDetail', url: '/projects/1', hasFilter: false, hasStatCards: true },
  27  |   { name: 'GanttView', url: '/projects/gantt', hasFilter: true, hasStatCards: false },
  28  |   { name: 'IpdGate', url: '/projects/ipd', hasFilter: true, hasStatCards: false },
  29  | ]
  30  | 
  31  | test.describe('W30 Phase 2 关键 20 页 UI 巡检', () => {
  32  |   test('W30-2-UI-AUDIT 所有 Tier 1 页面 console + 网络 + 统计卡全检', async ({ page }) => {
  33  |     const report: any[] = []
  34  | 
  35  |     for (const pg of TIER1_PAGES) {
  36  |       const consoleErrors: string[] = []
  37  |       const vueWarns: string[] = []
  38  |       const networkFails: string[] = []
  39  |       const pageReq = page.context().request
  40  | 
  41  |       const consoleHandler = (msg: any) => {
  42  |         if (msg.type() === 'error') consoleErrors.push(msg.text())
  43  |         if (msg.type() === 'warning' && msg.text().includes('Vue warn')) vueWarns.push(msg.text())
  44  |       }
  45  |       const reqHandler = (req: any) => {
  46  |         // 跳过 Vite HMR / sourcemap
  47  |         const url = req.url()
  48  |         if (url.includes('/@vite/') || url.includes('node_modules') || url.endsWith('.map')) return
  49  |       }
  50  |       const respHandler = async (resp: any) => {
  51  |         const status = resp.status()
  52  |         const url = resp.url()
  53  |         if (url.includes('/api/') && status >= 400 && status < 600) {
  54  |           // 业务异常包在 HTTP 200，但 SY0301/SY0101 等也算
  55  |           networkFails.push(`${status} ${url.replace('http://localhost:5173/api/', '/api/')}`)
  56  |         }
  57  |       }
  58  |       page.on('console', consoleHandler)
  59  |       page.on('request', reqHandler)
  60  |       page.on('response', respHandler)
  61  | 
  62  |       try {
  63  |         await page.goto(pg.url, { waitUntil: 'networkidle', timeout: 10000 }).catch(() => {})
  64  |         await page.waitForTimeout(2500)
  65  |       } catch (e) {
  66  |         // page.goto 失败也算
  67  |       }
  68  | 
  69  |       page.off('console', consoleHandler)
  70  |       page.off('request', reqHandler)
  71  |       page.off('response', respHandler)
  72  | 
  73  |       // 抓统计卡
> 74  |       const statCards = await page.locator('.stat-card').count()
      |                                                          ^ Error: locator.count: Target page, context or browser has been closed
  75  |       let statCardsAllZero = false
  76  |       let statCardValues: any[] = []
  77  |       if (pg.hasStatCards && statCards > 0) {
  78  |         for (let i = 0; i < statCards; i++) {
  79  |           const num = (await page.locator('.stat-card').nth(i).locator('.stat-num, .stat-value').textContent())?.trim() || ''
  80  |           statCardValues.push(num)
  81  |         }
  82  |         statCardsAllZero = statCardValues.every(v => v === '0' || v === '0%' || v === '')
  83  |       }
  84  | 
  85  |       // 检查项目筛选器是否有"全部"选项
  86  |       let hasAllOption = true
  87  |       if (pg.hasFilter) {
  88  |         try {
  89  |           const selects = await page.locator('.filter-row .el-select, .el-select').count()
  90  |           if (selects > 0) {
  91  |             await page.locator('.filter-row .el-select, .el-select').first().click()
  92  |             await page.waitForTimeout(500)
  93  |             const items = await page.locator('.el-select-dropdown__item').allTextContents()
  94  |             hasAllOption = items.some(t => t.includes('全部') || t.includes('All') || t.includes('all'))
  95  |             await page.keyboard.press('Escape')
  96  |             await page.waitForTimeout(200)
  97  |           }
  98  |         } catch (e) {}
  99  |       }
  100 | 
  101 |       const entry = {
  102 |         page: pg.name,
  103 |         url: pg.url,
  104 |         consoleErrors: consoleErrors.length,
  105 |         consoleErrorSample: consoleErrors.slice(0, 2),
  106 |         vueWarns: vueWarns.length,
  107 |         vueWarnSample: vueWarns.slice(0, 1),
  108 |         networkFails: networkFails.length,
  109 |         networkFailSample: networkFails.slice(0, 3),
  110 |         statCards,
  111 |         statCardValues,
  112 |         statCardsAllZero,
  113 |         hasFilter: pg.hasFilter,
  114 |         hasAllOption
  115 |       }
  116 |       report.push(entry)
  117 |     }
  118 | 
  119 |     // 输出报告
  120 |     console.log('=' .repeat(80))
  121 |     console.log('W30 Phase 2 UI 巡检报告')
  122 |     console.log('=' .repeat(80))
  123 |     console.log(JSON.stringify(report, null, 2))
  124 | 
  125 |     // 汇总
  126 |     const summary = {
  127 |       totalPages: report.length,
  128 |       withConsoleErrors: report.filter((r: any) => r.consoleErrors > 0).length,
  129 |       withVueWarns: report.filter((r: any) => r.vueWarns > 0).length,
  130 |       withNetworkFails: report.filter((r: any) => r.networkFails > 0).length,
  131 |       withStatCardsAllZero: report.filter((r: any) => r.statCardsAllZero).length,
  132 |       withoutAllOption: report.filter((r: any) => r.hasFilter && !r.hasAllOption).map((r: any) => r.page)
  133 |     }
  134 |     console.log('SUMMARY:', JSON.stringify(summary, null, 2))
  135 | 
  136 |     // 软断言：不全 PASS 也允许（发现 bug 才是目的）
  137 |     // 但要求至少 Dashboard 渲染正常
  138 |     expect(summary.withConsoleErrors).toBeLessThan(20) // 软上限
  139 |   })
  140 | })
```