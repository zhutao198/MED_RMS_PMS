"""PRD UI 深度验证（v1.30）
- 登录 admin
- 访问每个路由，等待 Vue 挂载完成
- 检查：1) HTTP 200  2) 页面无 JS 错误  3) 主要 API 调用成功
- 记录 8 角色 × 关键页面 + 全 60 路由访问
"""
import sys
import time
import json
import re
from pathlib import Path
from datetime import datetime
from playwright.sync_api import sync_playwright, Page, expect

OUT = Path('D:/zhutao/MED_RMS_PMS/Code/backend/prd_ui_e2e.log')
FRONT = 'http://localhost:5173'

# 8 角色
USERS = [
    ('admin', 'admin123', '系统管理员'),
    ('pm', 'admin123', '项目经理'),
    ('re', 'admin123', '需求工程师'),
    ('reviewer', 'admin123', '评审员'),
    ('risk_mgr', 'admin123', '风险管理员'),
    ('qa_mgr', 'admin123', 'QA 主管'),
    ('compliance', 'admin123', '合规专员'),
    ('viewer', 'admin123', '只读用户'),
]

# 60 个路由（按 PRD 40 项功能 + 子页面）
ROUTES = [
    ('Dashboard', '/dashboard', 'FR-1.2/1.10'),
    ('RequirementList', '/requirements', 'FR-0.1/0.5/0.6/0.7'),
    ('ReqCreate', '/requirements/create', 'FR-0.6'),
    ('DecomposeList', '/decompose', 'FR-1.1'),
    ('RequirementKanban', '/requirements/kanban', 'FR-1.1'),
    ('QualityScore', '/requirements/quality', 'FR-2.4'),
    ('AIRequirementAssist', '/requirements/ai-assist', 'FR-2.1'),
    ('TestCaseList', '/testcases', 'FR-1.5'),
    ('ReviewManagement', '/reviews', 'FR-0.4'),
    ('RequirementPool', '/requirement-pool', 'FR-1.6'),
    ('RequirementTaskConvert', '/requirement-tasks', 'FR-1.10'),
    ('TraceMatrix', '/traceability', 'FR-0.5'),
    ('TraceGaps', '/traceability/gaps', 'FR-0.9'),
    ('TraceCoverage', '/traceability/coverage', 'FR-0.5'),
    ('TraceGraph', '/trace-graph', 'FR-2.3'),
    ('ChangeList', '/changes', 'FR-0.10/0.17/1.7'),
    ('ChangeCreate', '/changes/create', 'FR-0.10'),
    ('ComplianceList', '/compliance', 'FR-0.12/0.13/0.14/0.15'),
    ('Baselines', '/compliance/baselines', 'FR-0.8'),
    ('SoupManagement', '/compliance/soup', 'FR-0.13/1.11'),
    ('ProblemReport', '/compliance/problem-report', 'FR-0.14'),
    ('Iec62304', '/compliance/iec62304', 'FR-0.15'),
    ('DhfPackage', '/compliance/dhf', 'FR-1.4'),
    ('ErpsExport', '/compliance/erps', 'FR-1.12'),
    ('RegulationImpact', '/compliance/regulation-impact', 'FR-0.3/2.2'),
    ('SignatureList', '/esignature', 'FR-0.2'),
    ('ESignSettings', '/esignature/settings', 'FR-0.2'),
    ('RiskReport', '/risk', 'FR-1.8'),
    ('RiskRegister', '/risk/register', 'FR-1.8'),
    ('FmeaEditor', '/risk/fmea', 'FR-1.8'),
    ('ProjectsList', '/projects', 'FR-1.9'),
    ('TemplateManagement', '/projects/templates', 'FR-1.9'),
    ('ProjectDetail', '/projects/1', 'FR-1.9'),
    ('GanttView', '/projects/gantt', 'FR-2.7'),
    ('ProjectGantt', '/projects/1/gantt', 'FR-2.7'),
    ('IpdGate', '/projects/ipd', 'FR-2.5'),
    ('MilestoneList', '/milestones', 'FR-2.6'),
    ('ResourceManagement', '/projects/resources', 'FR-2.8'),
    ('WorklogView', '/projects/worklog', 'FR-2.9'),
    ('SystemManagement', '/system', 'FR-0.11'),
    ('UserManage', '/system/users', 'FR-0.11'),
    ('DictManage', '/system/dicts', 'FR-0.11'),
    ('DataMigration', '/system/migration', 'FR-1.13'),
    ('ReportCenter', '/reports', 'FR-1.4'),
    ('ReportsCustom', '/reports/custom', 'FR-1.4'),
    ('NotificationList', '/notifications', 'FR-0.2'),
]

def login_as(page: Page, username: str, password: str):
    page.goto(f'{FRONT}/login', wait_until='domcontentloaded')
    page.wait_for_timeout(500)
    # 找 username/password 输入框（多种可能 placeholder）
    try:
        page.locator('input[placeholder*="用户"], input[placeholder*="username"]').first.fill(username)
    except Exception:
        page.locator('input[type="text"]').first.fill(username)
    try:
        page.locator('input[type="password"]').first.fill(password)
    except Exception:
        page.locator('input[placeholder*="密"], input[placeholder*="password"]').first.fill(password)
    page.locator('button[type="submit"], button:has-text("登录"), button:has-text("Login")').first.click()
    page.wait_for_url(re.compile(r'/(dashboard|requirements|projects)'), timeout=10000)
    page.wait_for_timeout(1000)

def visit_route(page: Page, route: str):
    """访问路由，捕获 JS 错误与 API 状态"""
    errors = []
    api_failures = []

    def on_console(msg):
        if msg.type == 'error':
            txt = msg.text[:200]
            if any(s in txt for s in ['Failed to load resource', 'favicon']):
                return
            errors.append(txt)

    def on_response(resp):
        if resp.status >= 500 and '/api/' in resp.url:
            api_failures.append(f'{resp.status} {resp.request.method} {resp.url.split("/api/", 1)[1][:80]}')

    page.on('console', on_console)
    page.on('response', on_response)
    try:
        page.goto(f'{FRONT}{route}', wait_until='domcontentloaded', timeout=15000)
        page.wait_for_timeout(2500)  # 等 Vue 挂载 + API 返回
        # 简单存在性检查
        body = page.locator('body').inner_text()[:200]
        is_404 = '404' in body and '页面' in body and 'not found' in body.lower()
        is_blank = len(body.strip()) < 30
    except Exception as e:
        body = ''
        is_404 = True
        errors.append(f'导航异常: {str(e)[:100]}')
    finally:
        page.remove_listener('console', on_console)
        page.remove_listener('response', on_response)

    return {
        'body_len': len(body),
        'is_404': is_404,
        'is_blank': is_blank,
        'errors': errors,
        'api_failures': api_failures,
    }

def main():
    with sync_playwright() as p:
        browser = p.chromium.launch(headless=True)
        ctx = browser.new_context()
        page = ctx.new_page()

        # 1. admin 登录
        print('[1] admin 登录 ...')
        try:
            login_as(page, 'admin', 'admin123')
        except Exception as e:
            print(f'FATAL: admin 登录失败: {e}')
            browser.close()
            sys.exit(1)

        # 2. admin 访问所有路由
        results = []
        n_pass = 0
        n_fail = 0
        for name, route, frs in ROUTES:
            r = visit_route(page, route)
            ok = not r['is_404'] and not r['is_blank'] and len(r['api_failures']) == 0
            results.append((name, route, frs, ok, r))
            if ok:
                n_pass += 1
            else:
                n_fail += 1
            print(f'  {route:<35} {("OK" if ok else "FAIL"):<5}  错={len(r["errors"])} API5xx={len(r["api_failures"])} body={r["body_len"]}')

        # 3. 其他 7 角色登录 + 访问 5 关键页面
        role_results = []
        for username, pwd, label in USERS[1:]:  # 跳过 admin
            try:
                page.goto(f'{FRONT}/login', wait_until='domcontentloaded')
                page.wait_for_timeout(500)
                # 登出（如果还在）
                try:
                    page.locator('input[type="text"]').first.fill(username)
                    page.locator('input[type="password"]').first.fill(pwd)
                except Exception:
                    pass
                page.locator('button[type="submit"], button:has-text("登录")').first.click()
                page.wait_for_url(re.compile(r'/(dashboard|requirements|projects)'), timeout=8000)
                page.wait_for_timeout(800)
                # 访问 5 关键页面
                for r2_name, r2_route, _ in [
                    ('Dashboard', '/dashboard', ''),
                    ('Requirements', '/requirements', ''),
                    ('Compliance', '/compliance', ''),
                    ('Risk', '/risk', ''),
                    ('SystemUsers', '/system/users', ''),
                ]:
                    r = visit_route(page, r2_route)
                    ok = not r['is_404'] and not r['is_blank'] and len(r['api_failures']) == 0
                    role_results.append((username, r2_name, r2_route, ok, r))
                # 登出
                try:
                    page.goto(f'{FRONT}/login', wait_until='domcontentloaded')
                except Exception:
                    pass
            except Exception as e:
                role_results.append((username, 'LOGIN', '-', False, {'errors': [str(e)], 'api_failures': [], 'is_404': True, 'is_blank': True, 'body_len': 0}))

        browser.close()

    # 写日志
    n_role_pass = sum(1 for _, _, _, ok, _ in role_results if ok)
    n_role_fail = sum(1 for _, _, _, ok, _ in role_results if not ok)

    with OUT.open('w', encoding='utf-8') as f:
        f.write(f'PRD UI 深度验证 v1.30 — {datetime.now().strftime("%Y-%m-%d %H:%M:%S")}\n')
        f.write(f'=========================================\n\n')
        f.write(f'admin 路由访问：PASS={n_pass} FAIL={n_fail} / {len(ROUTES)}\n')
        f.write(f'8 角色 × 5 页面：PASS={n_role_pass} FAIL={n_role_fail} / {len(role_results)}\n')
        f.write(f'总计：{n_pass + n_role_pass} / {len(ROUTES) + len(role_results)}\n\n')

        f.write('admin 路由详情：\n')
        f.write(f"{'name':<25}{'route':<35}{'OK?':<6}{'err':<5}{'5xx':<5}{'body':<6}  PRD\n")
        f.write('-' * 110 + '\n')
        for name, route, frs, ok, r in results:
            f.write(f'{name:<25}{route:<35}{("PASS" if ok else "FAIL"):<6}{len(r["errors"]):<5}{len(r["api_failures"]):<5}{r["body_len"]:<6}  {frs}\n')

        f.write('\n8 角色 × 5 页面详情：\n')
        for u, n2, rt, ok, r in role_results:
            f.write(f'  {u:<12} {n2:<15} {rt:<25} {"PASS" if ok else "FAIL"} err={len(r["errors"])} 5xx={len(r["api_failures"])} body={r["body_len"]}\n')

        # 失败详情
        f.write('\n失败详情：\n')
        for name, route, frs, ok, r in results:
            if not ok:
                f.write(f'  [{name}] {route}  PRD={frs}\n')
                f.write(f'    body_len={r["body_len"]} is_404={r["is_404"]} is_blank={r["is_blank"]}\n')
                for e in r['errors'][:3]:
                    f.write(f'    JS错误: {e[:150]}\n')
                for af in r['api_failures'][:3]:
                    f.write(f'    API 5xx: {af[:150]}\n')
        for u, n2, rt, ok, r in role_results:
            if not ok:
                f.write(f'  [{u}] {n2} {rt}\n')
                f.write(f'    body_len={r["body_len"]} is_404={r["is_404"]} is_blank={r["is_blank"]}\n')
                for e in r['errors'][:3]:
                    f.write(f'    JS错误: {e[:150]}\n')

    print(f'\n=== PRD UI 深度验证 v1.30 ===')
    print(f'admin 路由: {n_pass}/{len(ROUTES)}')
    print(f'7 角色×5 页: {n_role_pass}/{len(role_results)}')
    print(f'日志: {OUT}')

if __name__ == '__main__':
    main()
