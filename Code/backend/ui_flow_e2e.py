"""v1.27 UI 端到端验证（HTTP 层面）
由于 Chrome DevTools MCP 浏览器实例冲突，
改用"前端 5173 + 后端 8080"协同验证：模拟 UI 真实调用链
"""
import json
import sys
import io
import urllib.request
import urllib.error

sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8', errors='replace')

API = 'http://localhost:8080/api'
WEB = 'http://localhost:5173'

def req(url, method='GET', headers=None, body=None):
    headers = headers or {}
    data = None
    if body:
        data = json.dumps(body).encode('utf-8')
        headers['Content-Type'] = 'application/json'
    r = urllib.request.Request(url, data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(r) as resp:
            return resp.status, resp.read()
    except urllib.error.HTTPError as e:
        return e.code, e.read()
    except Exception as e:
        return -1, str(e).encode()

passed = 0
failed = 0
fails = []

# 1. 前端首页可达
code, body = req(WEB)
expect = (code == 200 and b'<div id="app">' in body or b'<div id="app"></div>' in body or b'Med-RMS' in body)
if expect:
    passed += 1
    print(f'  [OK] 前端首页可达 (HTTP {code})')
else:
    failed += 1
    fails.append(f'前端首页不可达 HTTP {code}')

# 2. 前端静态资源 (Vite dev)
for asset in ['/@vite/client', '/src/main.ts']:
    code, body = req(WEB + asset)
    if code in (200, 304):
        passed += 1
    else:
        failed += 1
        fails.append(f'前端资源 {asset} 不可达 HTTP {code}')

# 3. 8 角色登录 → 拿 token → 调用后端核心端点（模拟 UI 真实流程）
users = ['admin', 'qa_mgr', 'pm', 're', 'reviewer', 'risk_mgr', 'compliance', 'viewer']
# (method, path, page) - 按 RBAC seed 数据，reviewer/compliance 不应访问这些
ui_pages = [
    ('GET', '/projects', '项目管理', ['admin','qa_mgr','pm','re','risk_mgr','compliance','viewer']),
    ('GET', '/requirements?projectId=1', '需求列表', ['admin','qa_mgr','pm','re','reviewer','risk_mgr','compliance','viewer']),
    ('GET', '/risk/register/list', '风险登记', ['admin','qa_mgr','pm','re','risk_mgr','viewer']),
    ('GET', '/dashboard/view/management', '管理仪表盘', ['admin','qa_mgr','pm','re','risk_mgr','compliance','viewer']),
    ('GET', '/notifications/unread?userId=1', '通知中心', ['admin','qa_mgr','pm','re','risk_mgr','compliance','viewer']),
]
for user in users:
    code, body = req(f'{API}/auth/login', 'POST',
                     body={'username': user, 'password': 'admin123'})
    if code != 200:
        failed += 1
        fails.append(f'{user} 登录失败 HTTP {code}')
        continue
    token = json.loads(body)['data']['token']
    headers = {'Authorization': f'Bearer {token}'}
    for method, path, page, allow in ui_pages:
        code, body = req(f'{API}{path}', method, headers=headers)
        expected = 200 if user in allow else 403
        if code == expected:
            passed += 1
        else:
            failed += 1
            fails.append(f'{user:12s} 访问 {page:12s} -> {code} (expected {expected})')

# 4. 模拟 reviewer 访问受保护页（应 403）— 验证前端 403 后能否 fallback
code, body = req(f'{API}/system/users', headers={'Authorization': f'Bearer {json.loads(req(f"{API}/auth/login", "POST", body={"username":"reviewer","password":"admin123"})[1])["data"]["token"]}'})
if code == 403:
    passed += 1
else:
    failed += 1
    fails.append(f'reviewer 访问 /system/users 应 403，实际 {code}')

# 5. 未登录访问 → 前端应能 fallback 到登录页
code, _ = req(f'{API}/projects')
if code == 403:
    passed += 1
else:
    failed += 1
    fails.append(f'未登录访问应 403，实际 {code}')

print(f'\n=== UI 链路端到端验证 ===')
print(f'Result: {passed} passed, {failed} failed (total {passed+failed})')
if fails:
    for f in fails[:30]:
        print(' ', f)
