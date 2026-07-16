const puppeteer = require('puppeteer-core')
const path = require('path')
const fs = require('fs')

const CHROME_PATH = 'C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe'
const BASE_URL = 'http://localhost:5173'
const SCREENSHOT_DIR = path.join(__dirname, '..', '测试报告', 'screenshots')
const REPORT_FILE = path.join(__dirname, '..', '测试报告', '视觉验收报告.md')

const PAGES = [
  { path: '/dashboard', name: 'Dashboard', group: '1-仪表盘' },

  { path: '/requirements', name: '需求列表', group: '2-需求管理' },
  { path: '/requirements/create', name: '新建需求', group: '2-需求管理' },
  { path: '/decompose', name: '分解列表', group: '2-需求管理' },
  { path: '/requirements/kanban', name: '需求看板', group: '2-需求管理' },
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
  // Clean and create screenshot dir
  if (!fs.existsSync(SCREENSHOT_DIR)) fs.mkdirSync(SCREENSHOT_DIR, { recursive: true })

  const browser = await puppeteer.launch({
    executablePath: CHROME_PATH,
    headless: 'new',
    args: ['--no-sandbox', '--disable-setuid-sandbox', '--window-size=1440,900'],
  })

  const page = await browser.newPage()
  await page.setViewport({ width: 1440, height: 900 })
  
  const results = []
  const issues = []

  try {
    // Login
    console.log('=== 登录 ===')
    await page.goto(`${BASE_URL}/login`, { waitUntil: 'networkidle0', timeout: 30000 })
    await page.waitForSelector('input[type="text"], input[placeholder*="用户" i], input[name="username"]', { timeout: 10000 }).catch(() => {})
    // Try to find and fill login form
    const usernameInputs = await page.$$('input')
    if (usernameInputs.length >= 2) {
      await usernameInputs[0].click()
      await usernameInputs[0].type('admin')
      await usernameInputs[1].click()
      await usernameInputs[1].type('admin123')
      // Click login button
      const buttons = await page.$$('button')
      for (const btn of buttons) {
        const text = await btn.evaluate(el => el.textContent)
        if (text && (text.includes('登录') || text.includes('Login') || text.includes('登 录'))) {
          await btn.click()
          break
        }
      }
      await sleep(3000)
    }

    const currentUrl = page.url()
    console.log(`登录后 URL: ${currentUrl}`)

    // Visit each page
    for (const p of PAGES) {
      console.log(`\n=== [${p.group}] ${p.name} (${p.path}) ===`)
      const result = { ...p, errors: [], warnings: [], status: 'ok' }

      try {
        await page.goto(`${BASE_URL}${p.path}`, { waitUntil: 'networkidle0', timeout: 30000 })
        await sleep(2000) // Wait for rendering

        // Screenshot
        const safeName = p.name.replace(/[/\\?%*:|"<>]/g, '_')
        const screenshotPath = path.join(SCREENSHOT_DIR, `${p.group}-${safeName}.png`)
        await page.screenshot({ path: screenshotPath, fullPage: true })
        result.screenshot = screenshotPath

        // Collect console errors
        const consoleErrors = await page.evaluate(() => {
          return window.__auditErrors || []
        })

        // Check for empty states
        const hasEmpty = await page.evaluate(() => {
          return document.querySelector('.el-empty') !== null ||
                 document.body.innerText.includes('暂无数据') ||
                 document.body.innerText.includes('暂无')
        })

        // Check for loading indicator
        const hasLoading = await page.evaluate(() => {
          return document.querySelector('.el-loading-mask') !== null
        })

        // Check viewport overflow
        const hasOverflow = await page.evaluate(() => {
          return document.documentElement.scrollWidth > window.innerWidth + 5
        })

        result.checks = {
          hasEmpty, hasLoading, hasOverflow
        }

        result.status = 'ok'
        console.log(`  截图: ${screenshotPath}`)
        console.log(`  空状态: ${hasEmpty ? '✅' : '❌'} | 溢出: ${hasOverflow ? '❌' : '✅'}`)

      } catch (err) {
        result.status = 'error'
        result.errors.push(err.message)
        console.log(`  ❌ 错误: ${err.message}`)
      }

      results.push(result)

      // Collect global issues
      if (!result.checks?.hasEmpty) {
        issues.push({ page: p.name, path: p.path, issue: '空数据时无 <el-empty> 或 "暂无数据" 提示' })
      }
      if (result.checks?.hasOverflow) {
        issues.push({ page: p.name, path: p.path, issue: '页面水平溢出（横向滚动条）' })
      }
    }

  } catch (err) {
    console.error('致命错误:', err)
  } finally {
    await browser.close()
  }

  // Generate report
  const report = generateReport(results, issues)
  fs.writeFileSync(REPORT_FILE, report, 'utf-8')
  console.log(`\n=== 报告已生成: ${REPORT_FILE} ===`)
  console.log(`=== 截图目录: ${SCREENSHOT_DIR} ===`)
  console.log(`=== 共采集 ${results.length} 页，发现 ${issues.length} 个问题 ===`)
}

function generateReport(results, issues) {
  const lines = []
  lines.push('# 视觉验收自动化报告')
  lines.push('')
  lines.push(`> 生成时间: ${new Date().toISOString()}`)
  lines.push(`> 分辨率: 1440×900`)
  lines.push(`> 页面数: ${results.length}`)
  lines.push(`> 问题数: ${issues.length}`)
  lines.push('')

  // Summary
  lines.push('## 汇总')
  lines.push('')
  lines.push('| 模块 | 页面数 | 正常 | 错误 |')
  lines.push('|------|--------|------|------|')
  const groups = {}
  for (const r of results) {
    if (!groups[r.group]) groups[r.group] = { total: 0, ok: 0, err: 0 }
    groups[r.group].total++
    if (r.status === 'ok') groups[r.group].ok++
    else groups[r.group].err++
  }
  for (const [g, v] of Object.entries(groups)) {
    lines.push(`| ${g} | ${v.total} | ${v.ok} | ${v.err} |`)
  }
  lines.push('')

  // Issues
  if (issues.length > 0) {
    lines.push('## 发现的问题')
    lines.push('')
    lines.push('| 页面 | 路径 | 问题 |')
    lines.push('|------|------|------|')
    for (const issue of issues) {
      lines.push(`| ${issue.page} | ${issue.path} | ${issue.issue} |`)
    }
    lines.push('')
  }

  // Detail
  lines.push('## 各页详情')
  lines.push('')
  for (const r of results) {
    lines.push(`### ${r.name} (${r.path})`)
    lines.push('')
    lines.push(`- **模块**: ${r.group}`)
    lines.push(`- **状态**: ${r.status === 'ok' ? '✅ 正常' : '❌ 错误'}`)
    if (r.screenshot) lines.push(`- **截图**: \`${r.screenshot}\``)
    if (r.checks) {
      lines.push(`- **空状态提示**: ${r.checks.hasEmpty ? '有' : '无'}`)
      lines.push(`- **加载中**: ${r.checks.hasLoading ? '有' : '无'}`)
      lines.push(`- **水平溢出**: ${r.checks.hasOverflow ? '有' : '无'}`)
    }
    if (r.errors.length > 0) {
      lines.push('- **错误**:')
      for (const e of r.errors) lines.push(`  - ${e}`)
    }
    lines.push('')
  }

  return lines.join('\n')
}

run().catch(console.error)
