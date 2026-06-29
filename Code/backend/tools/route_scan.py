#!/usr/bin/env python3
"""通过真实 URL 跳转扫描所有路由（每次完全重载）"""
import re, json
from pathlib import Path
from playwright.sync_api import sync_playwright

BACKEND = Path(r"D:/zhutao/MED_RMS_PMS/Code/backend")
FRONTEND = "http://localhost:5173"

# 解析路由
router_text = Path(r"D:/zhutao/MED_RMS_PMS/Code/frontend/src/router/index.ts").read_text(encoding="utf-8")
path_pat = re.compile(r"path:\s*'([^']+)'")
routes = path_pat.findall(router_text)
routes = [r for r in routes if r not in ('/', '/login')]

# ID 替换
def fill(p):
    if ':id' not in p and 'create' not in p:
        return p
    return p  # 留 :id 给前端；用前端具体页

# 实际替换成具体路径
def resolve(p):
    mapping = {
        "/requirements/:id": "/requirements/1",
        "/requirements/:id/edit": "/requirements/1/edit",
        "/requirements/:id/decompose": "/requirements/1/decompose",
        "/requirements/:id/versions": "/requirements/1/versions",
        "/requirements/:id/versions/create": "/requirements/1/versions/create",
        "/changes/:id": "/changes/1",
        "/changes/:id/impact": "/changes/1/impact",
        "/changes/:id/execute": "/changes/1/execute",
        "/changes/:id/verify": "/changes/1/verify",
        "/projects/:id": "/projects/1",
        "/projects/:id/edit": "/projects/1/edit",
        "/projects/:id/members": "/projects/1/members",
        "/projects/:id/members/add": "/projects/1/members/add",
        "/projects/:id/deliverables": "/projects/1/deliverables",
        "/projects/:id/gates": "/projects/1/gates",
        "/projects/:id/gantt": "/projects/1/gantt",
        "/signature-history/:id": "/signature-history/1",
        "/signature-intent/:id": "/signature-intent/1",
        "/system/roles/:id/edit": "/system/roles/1/edit",
        "/compliance/soup/:id": "/compliance/soup/1",
        "/compliance/soup/:id/review": "/compliance/soup/1/review",
        "/compliance/problem-report/:id": "/compliance/problem-report/1",
        "/compliance/baselines/:id": "/compliance/baselines/1",
        "/compliance/baselines/:id/edit": "/compliance/baselines/1/edit",
    }
    return mapping.get(p, p)

real_routes = [resolve(r) for r in routes]
print(f"Routes to scan: {len(real_routes)}")

with sync_playwright() as pw:
    browser = pw.chromium.launch(headless=True)
    ctx = browser.new_context(viewport={'width': 1440, 'height': 900})
    page = ctx.new_page()
    
    # 登录
    page.goto(f"{FRONTEND}/login")
    page.wait_for_load_state('networkidle')
    # 找用户名/密码输入框
    try:
        page.locator('input').nth(0).fill('admin')
        page.locator('input[type="password"]').fill('admin123')
        # 验证码留空（前端可选）
        page.click('button:has-text("登录")')
        page.wait_for_load_state('networkidle', timeout=10000)
        page.wait_for_timeout(1500)
        # 抓 token 注入到所有请求
        token = page.evaluate('() => localStorage.getItem("token")')
        if token:
            ctx.set_extra_http_headers({'Authorization': f'Bearer {token}'})
            print(f'Token injected: {token[:30]}...')
        else:
            print('WARN: No token in localStorage after login')
    except Exception as e:
        print(f'Login err: {e}')
    
    results = []
    for i, route in enumerate(real_routes):
        url = f"{FRONTEND}{route}"
        try:
            page.goto(url, wait_until='domcontentloaded', timeout=15000)
            page.wait_for_load_state('networkidle', timeout=8000)
        except Exception as e:
            results.append({'route': route, 'error': str(e)[:100]})
            continue
        # 等 1s 让异步组件就绪
        page.wait_for_timeout(1000)
        # 收集内容
        info = page.evaluate('''() => {
            const main = document.querySelector('.el-main, main, [class*="main"]');
            const bodyText = document.body.innerText || '';
            const has404 = !!document.querySelector('.el-result__title, .not-found');
            const errMessages = Array.from(document.querySelectorAll('.el-message--error, .el-notification--error'))
                .map(e => e.innerText).slice(0, 3);
            return {
                bodyLen: bodyText.length,
                mainLen: main?.innerText?.length || 0,
                has404,
                errMessages,
                firstHeading: document.querySelector('h1, h2, .el-page-header__content, [class*="page-title"]')?.innerText?.slice(0, 60) || null
            };
        }''')
        info['route'] = route
        results.append(info)
        if i % 10 == 0:
            print(f'[{i+1}/{len(real_routes)}] {route} bodyLen={info.get("bodyLen")}')
    
    browser.close()
    
    out = BACKEND / 'tools' / 'ui_scan_results.json'
    out.write_text(json.dumps(results, ensure_ascii=False, indent=2), encoding='utf-8')
    print(f'\nWrote {out}')
    
    # 汇总
    bad = [r for r in results if r.get('bodyLen', 0) < 200]
    print(f'Bad (bodyLen<200): {len(bad)}')
    for r in bad[:20]:
        print(f'  {r["route"]}: bodyLen={r.get("bodyLen")}, mainLen={r.get("mainLen")}')
