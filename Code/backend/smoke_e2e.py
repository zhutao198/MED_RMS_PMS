"""v1.27 业务端点 smoke test - 验证核心业务功能"""
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
    with urllib.request.urlopen(req) as r:
        return json.loads(r.read())['data']['token']

def call(token, method, path, body=None):
    headers = {'Authorization': f'Bearer {token}'}
    data = None
    if body:
        headers['Content-Type'] = 'application/json'
        data = json.dumps(body).encode('utf-8')
    req = urllib.request.Request(f'{API}{path}', data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(req) as r:
            return r.status
    except urllib.error.HTTPError as e:
        return e.code

token = login('admin', 'admin123')
pm_token = login('pm', 'admin123')
qa_token = login('qa_mgr', 'admin123')

passed = 0
failed = 0
fails = []

# 业务 smoke - 全部用 admin，应该 200
smoke = [
    # 项目管理
    ('GET', '/projects', None, 'projects.list'),
    ('GET', '/projects/1', None, 'projects.getById'),
    ('GET', '/projects/templates', None, 'projects.templates'),
    # 需求
    ('GET', '/requirements', None, 'req.list'),
    ('GET', '/requirements/kanban?projectId=1', None, 'req.kanban'),
    # 风险
    ('GET', '/risk/register/list', None, 'risk.list'),
    # 变更
    ('GET', '/changes', None, 'change.list'),
    # 合规
    ('GET', '/compliance/audit-logs', None, 'compliance.audit'),
    ('GET', '/compliance/problem-reports', None, 'compliance.problems'),
    # 报表
    ('GET', '/reports', None, 'reports.list'),
    # 仪表盘
    ('GET', '/dashboard/view/management', None, 'dashboard.mgmt'),
    ('GET', '/dashboard/view/requirements', None, 'dashboard.req'),
    ('GET', '/dashboard/view/risk', None, 'dashboard.risk'),
    ('GET', '/dashboard/view/compliance', None, 'dashboard.comp'),
    # 通知
    ('GET', '/notifications/unread?userId=1', None, 'notif.unread'),
    ('GET', '/notifications/unread/count?userId=1', None, 'notif.count'),
    # 测试用例
    ('GET', '/testcases', None, 'testcases.list'),
    # 需求池
    ('GET', '/requirement-pool', None, 'pool.list'),
    # 基线
    ('GET', '/baselines/project/1', None, 'baseline.list'),
    # 甘特
    ('GET', '/gantt/project/1', None, 'gantt.data'),
    ('GET', '/gantt/resources/1', None, 'gantt.resources'),
    # IPD 阶段门
    ('GET', '/project/ipd-gate/list/1', None, 'ipd.list'),
    # 项目成员
    ('GET', '/project/member/list/1', None, 'member.list'),
    # 系统
    ('GET', '/system/users', None, 'sys.users'),
    ('GET', '/system/roles', None, 'sys.roles'),
    ('GET', '/system/dicts/all', None, 'sys.dicts'),
    ('GET', '/system/configs', None, 'sys.configs'),
    # 追溯
    ('GET', '/traceability/matrix', None, 'trace.matrix'),
    # 电子签名
    ('GET', '/esignature/signatures', None, 'esign.list'),
    # SOUP
    ('GET', '/requirement/soup-components', None, 'soup.list'),
]

for method, path, body, name in smoke:
    code = call(token, method, path, body)
    if code in (200, 201):
        passed += 1
    else:
        failed += 1
        fails.append(f'FAIL: {name:30s} {method:6s} {path:50s} -> {code}')

# 写入测试 - 验证 POST 也工作
writes = [
    ('POST', '/requirements', {'title':'smoke-test','projectId':1,'requirementType':'URS','priority':'HIGH'}, 'req.create'),
    ('POST', '/requirement-pool', {'source':'EMAIL','rawDescription':'smoke test','createdBy':1}, 'pool.add'),
    ('POST', '/risk/register', {'riskTitle':'smoke-test','riskLevel':'HIGH','category':'SOFTWARE','responseStrategy':'MITIGATE','ownerId':1,'ownerName':'admin'}, 'risk.create'),
    ('POST', '/notifications/1/read', None, 'notif.read'),
    ('POST', '/worklog', {'taskId':1,'hours':1.0,'workerId':1,'projectId':1}, 'worklog.create'),
]
for method, path, body, name in writes:
    code = call(token, method, path, body)
    if code in (200, 201):
        passed += 1
    else:
        failed += 1
        fails.append(f'FAIL: {name:30s} {method:6s} {path:50s} -> {code}')

# IEC 62304 校验
code = call(token, 'GET', '/compliance/iec62304/checklist/1', None)
if code in (200, 201): passed += 1
else:
    failed += 1
    fails.append(f'FAIL: iec.checklist {code}')

# 项目编号自动生成
code = call(token, 'POST', '/projects', {'projectName':'smoke','managerId':1})
if code in (200, 201): passed += 1
else:
    failed += 1
    fails.append(f'FAIL: project.create {code}')

print(f'\nSmoke test: {passed} passed, {failed} failed (total {passed+failed})')
if fails:
    print('\nFailures:')
    for f in fails[:30]:
        print(' ', f)
