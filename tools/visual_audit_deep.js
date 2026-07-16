const puppeteer = require('puppeteer-core')
const path = require('path')
const fs = require('fs')

const CHROME_PATH = 'C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe'
const BASE_URL = 'http://localhost:5173'
const DETAIL_DIR = path.join(__dirname, '..', '测试报告', 'screenshots')

const PAGES = [
  { path: '/dashboard', name: 'Dashboard', group: '1-仪表盘' },
  { path: '/requirements', name: '需求列表', group: '2-需求管理' },
  { path: '/requirements/create', name: '新建需求', group: '2-需求管理' },
  { path: '/requirements/kanban', name: '需求看板', group: '2-需求管理' },
  { path: '/decompose', name: '分解列表', group: '2-需求管理' },
  { path: '/testcases', name: '测试用例', group: '2-需求管理' },
  { path: '/reviews', name: '评审管理', group: '2-需求管理' },
  { path: '/requirement-pool', name: '需求池', group: '2-需求管理' },
  { path: '/requirement-tasks', name: '需求任务转化', group: '2-需求管理' },
  { path: '/requirements/quality', name: '需求质量评分', group: '2-需求管理' },
  { path: '/requirements/ai-assist', name: 'AI 辅助编写', group: '2-需求管理' },
  { path: '/traceability', name: '追溯矩阵', group: '3-追溯管理' },
  { path: '/traceability/gaps', name: '追溯缺口', group: '3-追溯管理' },
  { path: '/traceability/coverage', name: '追溯覆盖率', group: '3-追溯管理' },
  { path: '/traceability/import', name: '追溯导入', group: '3-追溯管理' },
  { path: '/trace-graph', name: '追溯图', group: '3-追溯管理' },
  { path: '/changes', name: '变更列表', group: '4-变更管理' },
  { path: '/changes/create', name: '新建变更', group: '4-变更管理' },
  { path: '/changes/approvals', name: '我的审批', group: '4-变更管理' },
  { path: '/compliance', name: '合规列表', group: '5-合规管理' },
  { path: '/compliance/baselines', name: '基线管理', group: '5-合规管理' },
  { path: '/compliance/soup', name: 'SOUP管理', group: '5-合规管理' },
  { path: '/compliance/iec62304', name: 'IEC62304', group: '5-合规管理' },
  { path: '/compliance/problem-report', name: '问题报告', group: '5-合规管理' },
  { path: '/compliance/dhf', name: 'DHF包', group: '5-合规管理' },
  { path: '/compliance/erps', name: 'NMPA eRPS', group: '5-合规管理' },
  { path: '/compliance/regulation-impact', name: '法规影响', group: '5-合规管理' },
  { path: '/compliance/regulations', name: '法规库', group: '5-合规管理' },
  { path: '/compliance/safety', name: '安全分类', group: '5-合规管理' },
  { path: '/compliance/reports', name: '合规报告', group: '5-合规管理' },
  { path: '/risk', name: '风险报告', group: '6-风险管理' },
  { path: '/risk/register', name: '风险登记册', group: '6-风险管理' },
  { path: '/risk/fmea', name: 'FMEA编辑器', group: '6-风险管理' },
  { path: '/risks/matrix', name: '风险矩阵', group: '6-风险管理' },
  { path: '/risks/monitoring', name: '风险监控', group: '6-风险管理' },
  { path: '/projects', name: '项目列表', group: '7-项目管理' },
  { path: '/projects/gantt', name: '甘特图', group: '7-项目管理' },
  { path: '/projects/ipd', name: 'IPD门控', group: '7-项目管理' },
  { path: '/projects/task-board', name: '任务看板', group: '7-项目管理' },
  { path: '/projects/resources', name: '资源管理', group: '7-项目管理' },
  { path: '/projects/worklog', name: '工作日志', group: '7-项目管理' },
  { path: '/projects/activities', name: '项目动态', group: '7-项目管理' },
  { path: '/projects/audit', name: '项目审计日志', group: '7-项目管理' },
  { path: '/milestones', name: '里程碑', group: '7-项目管理' },
  { path: '/projects/templates', name: '项目模板', group: '7-项目管理' },
  { path: '/reports', name: '报告中心', group: '8-报告' },
  { path: '/reports/custom', name: '自定义报告', group: '8-报告' },
  { path: '/reports/export', name: '报告导出', group: '8-报告' },
  { path: '/signatures', name: '电子签名列表', group: '9-电子签名' },
  { path: '/signature-history', name: '签名历史', group: '9-电子签名' },
  { path: '/esignature/settings', name: '签名设置', group: '9-电子签名' },
  { path: '/system', name: '系统管理', group: '10-系统' },
  { path: '/system/users', name: '用户管理', group: '10-系统' },
  { path: '/system/dicts', name: '字典管理', group: '10-系统' },
  { path: '/system/login-logs', name: '登录日志', group: '10-系统' },
  { path: '/system/operation-logs', name: '操作日志', group: '10-系统' },
  { path: '/system/organization', name: '组织管理', group: '10-系统' },
  { path: '/system/profile', name: '个人资料', group: '10-系统' },
  { path: '/audit-logs', name: '审计日志', group: '11-审计' },
  { path: '/audit-logs/verify', name: '审计验证', group: '11-审计' },
  { path: '/audit-logs/export', name: '审计导出', group: '11-审计' },
]

async function sleep(ms) { return new Promise(r => setTimeout(r, ms)) }

async function run() {
  const browser = await puppeteer.launch({
    executablePath: CHROME_PATH,
    headless: 'new',
    args: ['--no-sandbox', '--disable-setuid-sandbox', '--window-size=1440,900'],
  })

  const page = await browser.newPage()
  await page.setViewport({ width: 1440, height: 900 })

  // Collect page errors
  const pageErrors = []
  page.on('pageerror', err => pageErrors.push(err.message))
  page.on('console', msg => {
    if (msg.type() === 'error') pageErrors.push(`console.error: ${msg.text()}`)
  })

  const detailResults = []

  try {
    // Login
    await page.goto(`${BASE_URL}/login`, { waitUntil: 'networkidle0', timeout: 30000 })
    await sleep(1000)
    // Fill login form
    const inputs = await page.$$('input')
    if (inputs.length >= 2) {
      await inputs[0].click(); await inputs[0].type('admin')
      await inputs[1].click(); await inputs[1].type('admin123')
      const buttons = await page.$$('button')
      for (const btn of buttons) {
        const text = await btn.evaluate(el => el.textContent)
        if (text && (text.includes('登录') || text.includes('登 录'))) { await btn.click(); break }
      }
      await sleep(3000)
    }

    for (const p of PAGES) {
      console.log(`\n=== [${p.group}] ${p.name} ===`)
      pageErrors.length = 0

      try {
        await page.goto(`${BASE_URL}${p.path}`, { waitUntil: 'networkidle0', timeout: 30000 })
        await sleep(1000)

        const info = await page.evaluate(() => {
          const el = document.documentElement
          const body = document.body
          return {
            // Layout
            scrollWidth: el.scrollWidth,
            clientWidth: el.clientWidth,
            scrollHeight: el.scrollHeight,
            bodyChildren: body.children.length,
            // Elements presence
            hasElTable: !!document.querySelector('.el-table'),
            hasElTableRows: !!document.querySelector('.el-table__body-wrapper tbody tr'),
            hasElEmpty: !!document.querySelector('.el-empty'),
            hasElDialog: !!document.querySelector('.el-dialog'),
            hasElDialogWrapper: !!document.querySelector('.el-overlay-dialog'),
            hasElForm: !!document.querySelector('.el-form'),
            hasElButton: !!document.querySelector('.el-button'),
            hasVLoading: !!document.querySelector('.el-loading-mask'),
            hasLoadingText: body.innerText.includes('加载') || body.innerText.includes('Loading'),
            hasNoData: body.innerText.includes('暂无数据') || body.innerText.includes('No Data') || body.innerText.includes('空'),
            hasErrorTip: body.innerText.includes('失败') || body.innerText.includes('错误') || body.innerText.includes('出错'),
            // Route guard (should show sidebar)
            hasSidebar: !!document.querySelector('.el-menu, .sidebar, .layout-sidebar, [class*="sidebar"]'),
            // Title
            pageTitle: document.title || '',
            h1Count: document.querySelectorAll('h1, h2, .page-title, [class*="page-title"]').length,
          }
        })

        info.name = p.name
        info.path = p.path
        info.group = p.group
        info.consoleErrors = [...pageErrors]

        // Determine real issues
        const issues = []
        if (info.hasElTable && !info.hasElTableRows && !info.hasNoData) {
          issues.push('表格为空但无空状态提示')
        }
        if (info.scrollWidth > info.clientWidth + 10) {
          issues.push(`水平溢出 ${info.scrollWidth - info.clientWidth}px`)
        }
        if (info.consoleErrors.length > 0) {
          issues.push(`控制台错误: ${info.consoleErrors.slice(0, 3).join('; ')}`)
        }
        if (info.hasErrorTip) {
          issues.push('页面显示错误提示')
        }

        console.log(`  表格: ${info.hasElTable}(${info.hasElTableRows ? '有数据' : '空'}) | 空态: ${info.hasNoData ? '✅' : '❌'} | 溢出: ${info.scrollWidth > info.clientWidth ? '❌' : '✅'} | 控制台错误: ${info.consoleErrors.length}`)
        if (issues.length > 0) {
          console.log(`  ⚠️ ${issues.join(' | ')}`)
        }

        info.issues = issues
        detailResults.push(info)

      } catch (err) {
        console.log(`  ❌ ${err.message}`)
        detailResults.push({ name: p.name, path: p.path, group: p.group, issues: [`导航失败: ${err.message}`], error: true })
      }
    }

  } finally {
    await browser.close()
  }

  // Generate consolidated report
  const realIssues = detailResults.filter(r => r.issues && r.issues.length > 0)
  const emptyTables = detailResults.filter(r => r.hasElTable && !r.hasElTableRows && !r.hasNoData && !r.issues?.some(i => i.includes('导航失败')))
  const consoleErrors = detailResults.filter(r => r.consoleErrors && r.consoleErrors.length > 0)

  console.log('\n\n========= 汇总报告 =========')
  console.log(`总页数: ${detailResults.length}`)
  console.log(`有表格空态问题的页面: ${emptyTables.length}`)
  console.log(`有控制台错误的页面: ${consoleErrors.length}`)
  console.log(`有水平溢出的页面: ${detailResults.filter(r => r.scrollWidth > r.clientWidth + 10).length}`)
  console.log(`有问题页面: ${realIssues.length}`)

  if (emptyTables.length > 0) {
    console.log('\n--- 表格空态缺提示 ---')
    for (const r of emptyTables) {
      console.log(`  ${r.name} (${r.path})`)
    }
  }

  if (consoleErrors.length > 0) {
    console.log('\n--- 控制台错误 ---')
    for (const r of consoleErrors) {
      console.log(`  ${r.name} (${r.path}): ${r.consoleErrors.slice(0, 2).join(' | ')}`)
    }
  }

  // Save detailed JSON
  const jsonPath = path.join(DETAIL_DIR, '..', 'visual_audit_detail.json')
  fs.writeFileSync(jsonPath, JSON.stringify(detailResults, null, 2))
  console.log(`\n详细结果: ${jsonPath}`)
}

run().catch(console.error)
