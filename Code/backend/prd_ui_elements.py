"""PRD UI 元素级深度遍历 v1.30
对每个前端路由：
1. 访问路由，等 Vue 挂载
2. 枚举所有可交互元素（button / input / select / textarea / a）
3. 对每个元素尝试交互（点击/填写）
4. 记录：是否有 API 调用、JS 错误、UI 异常（弹窗/跳转/无反应）
5. 统计每页元素交互通过率
"""
import json
import re
import sys
import time
from pathlib import Path
from datetime import datetime
from playwright.sync_api import sync_playwright, Page, expect

OUT = Path('D:/zhutao/MED_RMS_PMS/Code/backend/prd_ui_elements.log')
FRONT = 'http://localhost:5173'

# 46 路由（与 prd_ui_e2e.py 保持一致）
ROUTES = [
    ('Dashboard', '/dashboard'),
    ('RequirementList', '/requirements'),
    ('ReqCreate', '/requirements/create'),
    ('DecomposeList', '/decompose'),
    ('RequirementKanban', '/requirements/kanban'),
    ('QualityScore', '/requirements/quality'),
    ('AIRequirementAssist', '/requirements/ai-assist'),
    ('TestCaseList', '/testcases'),
    ('ReviewManagement', '/reviews'),
    ('RequirementPool', '/requirement-pool'),
    ('RequirementTaskConvert', '/requirement-tasks'),
    ('TraceMatrix', '/traceability'),
    ('TraceGaps', '/traceability/gaps'),
    ('TraceCoverage', '/traceability/coverage'),
    ('TraceGraph', '/trace-graph'),
    ('ChangeList', '/changes'),
    ('ChangeCreate', '/changes/create'),
    ('ComplianceList', '/compliance'),
    ('Baselines', '/compliance/baselines'),
    ('SoupManagement', '/compliance/soup'),
    ('ProblemReport', '/compliance/problem-report'),
    ('Iec62304', '/compliance/iec62304'),
    ('DhfPackage', '/compliance/dhf'),
    ('ErpsExport', '/compliance/erps'),
    ('RegulationImpact', '/compliance/regulation-impact'),
    ('SignatureList', '/esignature'),
    ('ESignSettings', '/esignature/settings'),
    ('RiskReport', '/risk'),
    ('RiskRegister', '/risk/register'),
    ('FmeaEditor', '/risk/fmea'),
    ('ProjectsList', '/projects'),
    ('TemplateManagement', '/projects/templates'),
    ('ProjectDetail', '/projects/1'),
    ('GanttView', '/projects/gantt'),
    ('ProjectGantt', '/projects/1/gantt'),
    ('IpdGate', '/projects/ipd'),
    ('MilestoneList', '/milestones'),
    ('ResourceManagement', '/projects/resources'),
    ('WorklogView', '/projects/worklog'),
    ('SystemManagement', '/system'),
    ('UserManage', '/system/users'),
    ('DictManage', '/system/dicts'),
    ('DataMigration', '/system/migration'),
    ('ReportCenter', '/reports'),
    ('ReportsCustom', '/reports/custom'),
    ('NotificationList', '/notifications'),
]

# 危险元素（不点击，避免破坏状态）
DANGEROUS_KEYWORDS = ['删除', '注销', '退出', '禁用', '驳回', '禁用', '撤稿', '清除', '重置数据', '停止', '踢出', 'reset', 'delete', 'logout', 'remove']


def login_as(page: Page, username: str, password: str):
    page.goto(f'{FRONT}/login', wait_until='domcontentloaded', timeout=15000)
    page.wait_for_timeout(500)
    # 清空可能残留
    page.evaluate('() => { localStorage.clear(); sessionStorage.clear(); }')
    page.reload(wait_until='domcontentloaded')
    page.wait_for_timeout(500)
    page.locator('input[placeholder*="用户"], input[type="text"]').first.fill(username)
    page.locator('input[type="password"]').first.fill(password)
    page.locator('button[type="submit"], button:has-text("登录")').first.click()
    page.wait_for_url(re.compile(r'/(dashboard|requirements|projects)'), timeout=10000)
    page.wait_for_timeout(800)


def enumerate_elements(page: Page):
    """枚举页面上所有可交互元素"""
    return page.evaluate('''() => {
        const result = { buttons: [], inputs: [], selects: [], textareas: [], links: [] };

        // 所有可见 button
        document.querySelectorAll('button, [role="button"]').forEach((el, i) => {
            const r = el.getBoundingClientRect();
            if (r.width > 0 && r.height > 0 && r.bottom > 0) {
                const text = (el.textContent || el.getAttribute('aria-label') || '').trim().slice(0, 40);
                const disabled = el.disabled || el.classList.contains('is-disabled');
                const visible = r.top < window.innerHeight && r.bottom > 0;
                if (visible) {
                    result.buttons.push({ idx: i, text, disabled, type: el.type || 'button' });
                }
            }
        });

        // input
        document.querySelectorAll('input').forEach((el, i) => {
            const r = el.getBoundingClientRect();
            if (r.width > 0 && r.height > 0) {
                const type = el.type || 'text';
                if (['hidden', 'file'].includes(type)) return;
                const placeholder = el.placeholder || el.getAttribute('aria-label') || '';
                const value = el.value;
                result.inputs.push({ idx: i, type, placeholder: placeholder.slice(0, 30), value: value.slice(0, 30) });
            }
        });

        // select
        document.querySelectorAll('select, .el-select').forEach((el, i) => {
            const r = el.getBoundingClientRect();
            if (r.width > 0 && r.height > 0) {
                const placeholder = el.querySelector('.el-select__placeholder, .el-input__inner')?.textContent || '';
                result.selects.push({ idx: i, placeholder: placeholder.trim().slice(0, 30) });
            }
        });

        // textarea
        document.querySelectorAll('textarea').forEach((el, i) => {
            const r = el.getBoundingClientRect();
            if (r.width > 0 && r.height > 0) {
                result.textareas.push({ idx: i, placeholder: (el.placeholder || '').slice(0, 30) });
            }
        });

        return result;
    }''')


def is_dangerous(text: str) -> bool:
    return any(k in text.lower() for k in DANGEROUS_KEYWORDS)


def main():
    with sync_playwright() as p:
        browser = p.chromium.launch(headless=True)
        ctx = browser.new_context(viewport={'width': 1440, 'height': 900})
        page = ctx.new_page()

        # 全局监听 console error
        all_errors = []
        def on_console(msg):
            if msg.type == 'error':
                txt = msg.text[:200]
                if any(s in txt for s in ['favicon', 'Failed to load resource']):
                    return
                all_errors.append(txt)
        page.on('console', on_console)

        # 1. admin 登录
        print('[1] admin 登录 ...')
        login_as(page, 'admin', 'admin123')

        # 2. 遍历每个路由
        page_results = []
        n_total_btns = 0
        n_clicked = 0
        n_click_with_api = 0
        n_click_with_alert = 0
        n_click_no_response = 0
        n_danger_skipped = 0
        n_input_filled = 0
        n_select_opened = 0
        n_page_ok = 0
        n_page_fail = 0
        all_bugs = []

        for name, route in ROUTES:
            print(f'[2] {route:<35}', end=' ')
            errors_before = len(all_errors)
            api_before = 0
            api_calls = []
            def on_resp(r):
                if '/api/' in r.url and r.url != page.url:
                    api_calls.append((r.request.method, r.url.split('/api/', 1)[1].split('?')[0][:60], r.status))
            page.on('response', on_resp)

            try:
                page.goto(f'{FRONT}{route}', wait_until='domcontentloaded', timeout=15000)
                page.wait_for_timeout(2500)  # 等 Vue 挂载 + API 返回
                body = page.locator('body').inner_text()
                is_blank = len(body.strip()) < 30
                is_404 = '404' in body and 'not found' in body.lower()
                if is_blank or is_404:
                    page_results.append((name, route, 'FAIL', 'blank/404', 0, 0, 0, 0, []))
                    n_page_fail += 1
                    print('FAIL (blank/404)')
                    continue

                elements = enumerate_elements(page)
                n_btn = len(elements['buttons'])
                n_in = len(elements['inputs'])
                n_sel = len(elements['selects'])
                n_ta = len(elements['textareas'])
                n_total_btns += n_btn

                # 3. 点击每个非危险、非禁用的 button
                btn_clicks = 0
                btn_with_api = 0
                btn_with_alert = 0
                btn_no_response = 0
                btn_danger = 0
                bug_list = []

                for btn_info in elements['buttons']:
                    if btn_info['disabled']:
                        continue
                    if is_dangerous(btn_info['text']):
                        btn_danger += 1
                        n_danger_skipped += 1
                        continue

                    try:
                        # 用 evaluate 找到并点击
                        page.evaluate(f'''() => {{
                            const btns = document.querySelectorAll('button, [role="button"]');
                            const b = btns[{btn_info['idx']}];
                            if (b) b.click();
                        }}''')
                        btn_clicks += 1
                        page.wait_for_timeout(500)
                        # 检查是否有 API 调用
                        if api_calls:
                            btn_with_api += 1
                        # 检查是否有 alert/弹窗
                        has_dialog = page.evaluate('() => !!document.querySelector(".el-dialog, .el-message, .el-notification, .el-message-box")')
                        if has_dialog:
                            btn_with_alert += 1
                            # 关闭弹窗
                            try:
                                page.evaluate('() => { document.querySelectorAll(".el-dialog__close, .el-message__closeBtn, .el-notification__closeBtn").forEach(e => e.click()); }')
                                page.wait_for_timeout(300)
                            except: pass
                    except Exception as e:
                        bug_list.append(f'按钮"{btn_info["text"]}"点击异常: {str(e)[:80]}')

                # 4. 尝试填写每个 input（仅前 5 个避免过度）
                input_fills = 0
                for in_info in elements['inputs'][:5]:
                    if in_info['type'] in ('hidden', 'file', 'submit', 'button'):
                        continue
                    try:
                        test_val = 'UI-E2E-TEST'
                        if in_info['type'] == 'number':
                            test_val = '123'
                        page.evaluate(f'''() => {{
                            const inputs = document.querySelectorAll('input');
                            const el = inputs[{in_info['idx']}];
                            if (el && !el.disabled) {{
                                const native = Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype, 'value').set;
                                native.call(el, '{test_val}');
                                el.dispatchEvent(new Event('input', {{ bubbles: true }}));
                                el.dispatchEvent(new Event('change', {{ bubbles: true }}));
                            }}
                        }}''')
                        input_fills += 1
                    except: pass

                # 5. 尝试打开每个 select
                select_opens = 0
                for sel_info in elements['selects'][:5]:
                    try:
                        page.evaluate(f'''() => {{
                            const sels = document.querySelectorAll('.el-select, select');
                            const s = sels[{sel_info['idx']}];
                            if (s) s.click();
                        }}''')
                        select_opens += 1
                        page.wait_for_timeout(200)
                        # 关闭下拉
                        page.keyboard.press('Escape')
                        page.wait_for_timeout(100)
                    except: pass

                # 汇总
                no_resp_rate = (btn_clicks - btn_with_api - btn_with_alert) / btn_clicks if btn_clicks > 0 else 0
                page_ok = no_resp_rate < 0.5  # 超过 50% 无反应视为页面问题
                status = 'OK' if page_ok else 'WARN'
                n_page_ok += 1 if page_ok else 0
                n_page_fail += 0 if page_ok else 1
                n_clicked += btn_clicks
                n_click_with_api += btn_with_api
                n_click_with_alert += btn_with_alert
                n_input_filled += input_fills
                n_select_opened += select_opens

                if not page_ok:
                    all_bugs.append((route, f'按钮无反应率 {no_resp_rate:.0%}', bug_list[:3]))

                page_results.append((name, route, status, '', n_btn, btn_clicks, btn_with_api, input_fills + select_opens, bug_list))
                print(f'{status} btn={n_btn} clicked={btn_clicks} api={btn_with_api} alert={btn_with_alert} input={input_fills} sel={select_opens}')

            except Exception as e:
                page_results.append((name, route, 'FAIL', str(e)[:50], 0, 0, 0, 0, []))
                n_page_fail += 1
                print(f'FAIL ({str(e)[:30]})')
            finally:
                page.remove_listener('response', on_resp)

        # 关闭浏览器
        browser.close()

    # 3. 写日志
    n_pages = len(ROUTES)
    with OUT.open('w', encoding='utf-8') as f:
        f.write(f'PRD UI 元素级深度遍历 v1.30 — {datetime.now().strftime("%Y-%m-%d %H:%M:%S")}\n')
        f.write('=========================================\n\n')
        f.write(f'路由：{n_pages} 个  admin 登录\n')
        f.write(f'页面 OK：{n_page_ok}  WARN/FAIL：{n_page_fail}\n')
        f.write(f'按钮总数：{n_total_btns}  跳过危险：{n_danger_skipped}\n')
        f.write(f'实际点击：{n_clicked}  触发 API：{n_click_with_api}  触发弹窗：{n_click_with_alert}\n')
        f.write(f'Input 填写：{n_input_filled}  Select 打开：{n_select_opened}\n\n')

        f.write(f'{"路由":<35}{"OK?":<6}{"按钮":<5}{"点击":<5}{"API":<5}{"弹窗":<5}{"填表":<5}{"问题"}\n')
        f.write('-' * 110 + '\n')
        for name, route, status, _, n_btn, clicked, api, fills, bugs in page_results:
            f.write(f'{route:<35}{status:<6}{n_btn:<5}{clicked:<5}{api:<5}{0:<5}{fills:<5}{"" if not bugs else bugs[0][:40]}\n')

        if all_bugs:
            f.write(f'\n发现的问题（{len(all_bugs)} 个）：\n')
            for route, desc, bugs in all_bugs:
                f.write(f'  {route}: {desc}\n')
                for b in bugs[:3]:
                    f.write(f'    - {b}\n')

        f.write(f'\n全局 JS 错误：{len(all_errors)}\n')
        # 去重
        seen = set()
        for e in all_errors:
            key = e[:80]
            if key not in seen:
                seen.add(key)
                f.write(f'  - {e[:150]}\n')

    print(f'\n=== PRD UI 元素级深度遍历 v1.30 ===')
    print(f'路由 {n_page_ok}/{n_pages}  OK')
    print(f'按钮点击 {n_clicked}  触发 API {n_click_with_api}  弹窗 {n_click_with_alert}')
    print(f'输入填写 {n_input_filled}  Select {n_select_opened}')
    if all_bugs:
        print(f'\n发现问题：{len(all_bugs)}')
        for r, d, _ in all_bugs:
            print(f'  {r}: {d}')
    print(f'日志: {OUT}')


if __name__ == '__main__':
    main()
