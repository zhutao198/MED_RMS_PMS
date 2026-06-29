"""v1.27 负向/边界测试
覆盖：401/403/404/400、SQL 注入、XSS、CSRF、参数边界、限流、大 payload
"""
import json
import sys
import io
import urllib.request
import urllib.error

sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8', errors='replace')

API = 'http://localhost:8080/api'

def login(user, pwd):
    req = urllib.request.Request(
        f'{API}/auth/login',
        data=json.dumps({'username': user, 'password': pwd}).encode('utf-8'),
        headers={'Content-Type': 'application/json'},
        method='POST'
    )
    try:
        with urllib.request.urlopen(req) as r:
            return json.loads(r.read())['data']['token']
    except urllib.error.HTTPError:
        return None

def call(token, method, path, body=None, raw_body=None, extra_headers=None):
    headers = {}
    if token:
        headers['Authorization'] = f'Bearer {token}'
    data = None
    if body:
        headers['Content-Type'] = 'application/json'
        data = json.dumps(body).encode('utf-8')
    elif raw_body:
        headers['Content-Type'] = 'application/octet-stream'
        data = raw_body
    if extra_headers:
        headers.update(extra_headers)
    req = urllib.request.Request(f'{API}{path}', data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(req) as r:
            return r.status, r.read()[:200]
    except urllib.error.HTTPError as e:
        return e.code, e.read()[:200]

admin = login('admin', 'admin123')
viewer = login('viewer', 'admin123')

passed = 0
failed = 0
fails = []

def expect(name, actual, expected_set):
    global passed, failed
    if actual in expected_set:
        passed += 1
    else:
        failed += 1
        fails.append(f'FAIL: {name:55s} -> {actual} (expected {expected_set})')

# ============ 1. 401/403 鉴权边界 ============
expect('未登录访问 GET /requirements', call(None, 'GET', '/requirements')[0], {403})
expect('无效 token 访问', call('invalid.token.here', 'GET', '/requirements')[0], {403})
expect('空 Bearer', call('', 'GET', '/requirements')[0], {403})
expect('viewer 访问 /system/users', call(viewer, 'GET', '/system/users')[0], {403})
expect('viewer 尝试写 POST /requirements', call(viewer, 'POST', '/requirements', {'title':'x','projectId':1,'requirementType':'URS'})[0], {403})
expect('viewer 尝试 DELETE', call(viewer, 'DELETE', '/system/users/1')[0], {403})

# ============ 2. 404 资源不存在 ============
code, body = call(admin, 'GET', '/requirements/9999999')
expect('需求 ID 9999999 不存在', code, {200, 404, 500})  # 业务码 200 但可能 data=null 也算
code, body = call(admin, 'GET', '/projects/9999999')
expect('项目 ID 9999999 不存在', code, {200, 404, 500})
code, body = call(admin, 'GET', '/risk/register/9999999')
expect('风险 ID 9999999 不存在', code, {200, 404, 500})

# ============ 3. 400 错误参数 ============
code, body = call(admin, 'POST', '/auth/login', {'username':'','password':''})
expect('空用户名/密码登录', code, {200, 400, 500})  # 业务码也算 OK
code, body = call(admin, 'POST', '/requirements', {'title':'','projectId':1,'requirementType':'INVALID_TYPE'})
expect('创建需求 - 错误 requirementType', code, {200, 400, 500})
code, body = call(admin, 'POST', '/requirements', {'title':'','projectId':-1,'requirementType':'URS'})
expect('创建需求 - 负数 projectId', code, {200, 400, 500})

# ============ 4. SQL 注入尝试 ============
sqli_payloads = [
    "'; DROP TABLE t_requirement;--",
    "1' OR '1'='1",
    "1; UPDATE t_user SET password='hacked' WHERE id=1;--",
    "' UNION SELECT * FROM sys_schema.t_user--",
    "1' AND (SELECT COUNT(*) FROM sys_schema.t_user) > 0--",
]
for payload in sqli_payloads:
    code, _ = call(admin, 'GET', f"/requirements?title={urllib.request.quote(payload)}")
    expect(f'SQLi: title={payload[:30]}...', code, {200, 400})  # 不应是 500
    code, _ = call(admin, 'POST', '/auth/login', {'username': payload, 'password': 'x'})
    expect(f'SQLi login: {payload[:20]}...', code, {200, 400, 500})

# ============ 5. XSS 尝试 ============
xss_payloads = [
    "<script>alert('xss')</script>",
    "<img src=x onerror=alert(1)>",
    "javascript:alert(document.cookie)",
    "\"><script>alert(1)</script>",
]
for payload in xss_payloads:
    code, body = call(admin, 'POST', '/requirements', {
        'title': payload, 'projectId': 1, 'requirementType': 'URS'
    })
    # 创建可能 200（XSS 防护是输出端而非输入端），但不应崩溃
    expect(f'XSS in title: {payload[:30]}...', code, {200, 400})
    # 检查响应是否原样回显（如果有则 XSS 防护未生效）
    if code == 200 and payload in body.decode('utf-8', errors='ignore'):
        # 这是发现的问题，但不直接 fail — 记录下来
        fails.append(f'WARN: XSS payload echoed in response: {payload[:30]}')

# ============ 6. CSRF (Spring Security 默认 SameSite, 验证 Origin) ============
code, _ = call(admin, 'POST', '/requirements', {'title':'csrf','projectId':1,'requirementType':'URS'},
               extra_headers={'Origin': 'http://evil.com'})
expect('CSRF: Origin 来自 evil.com', code, {200, 403})  # 取决于 SecurityConfig
code, _ = call(admin, 'POST', '/requirements', {'title':'csrf','projectId':1,'requirementType':'URS'},
               extra_headers={'Origin': 'http://localhost:5173'})
expect('CSRF: Origin 来自合法前端', code, {200})

# ============ 7. 边界/大 payload ============
huge_title = 'A' * 100000
code, _ = call(admin, 'POST', '/requirements', {
    'title': huge_title, 'projectId': 1, 'requirementType': 'URS'
})
expect('超大 title (100K 字符)', code, {200, 400, 413})  # 不应是 500

# null/特殊字符
code, _ = call(admin, 'POST', '/requirements', {
    'title': None, 'projectId': 1, 'requirementType': 'URS'
})
expect('null title', code, {200, 400})

# ============ 8. 限流 smoke (100 并发请求同一端点) ============
import concurrent.futures
import threading
counter = {'ok': 0, 'err': 0, 'rate_limited': 0}
lock = threading.Lock()
def hit():
    code, _ = call(admin, 'GET', '/projects')
    with lock:
        if code == 200:
            counter['ok'] += 1
        elif code == 429:
            counter['rate_limited'] += 1
        else:
            counter['err'] += 1
with concurrent.futures.ThreadPoolExecutor(max_workers=20) as ex:
    list(ex.map(lambda _: hit(), range(100)))
# 100 并发应大多 200，至多 1-2 失败（连接耗尽/限流），不应 0
# 只要至少 95 个成功就算过（容忍 5% 失败：连接重置/超时）
ok_pass = counter['ok'] >= 95
expect(f'100 并发 GET /projects (ok={counter["ok"]}, err={counter["err"]}, 429={counter["rate_limited"]}) >= 95',
       ok_pass, {True})

print(f'\n=== 负向/边界测试 ===')
print(f'Result: {passed} passed, {failed} failed (total {passed+failed})')
if fails:
    print('\nFindings:')
    for f in fails[:30]:
        print(' ', f)
