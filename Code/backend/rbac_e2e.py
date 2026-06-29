"""RBAC 综合 e2e 测试（v1.27）"""
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

users = ['admin', 'qa_mgr', 'pm', 're', 'reviewer', 'risk_mgr', 'compliance', 'viewer']
tokens = {u: login(u, 'admin123') for u in users}

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

cases = [
    # 通用列表 - 所有角色都应通过
    ('GET', '/requirements', None, ['admin','qa_mgr','pm','re','reviewer','risk_mgr','compliance','viewer'], []),
    ('GET', '/esignature/signatures', None, ['admin','qa_mgr','pm','re','reviewer','risk_mgr','compliance','viewer'], []),
    ('GET', '/traceability/matrix', None, ['admin','qa_mgr','pm','re','reviewer','risk_mgr','compliance','viewer'], []),
    # 系统用户管理 - 仅 ADMIN
    ('GET', '/system/users', None, ['admin'], ['qa_mgr','pm','re','reviewer','risk_mgr','compliance','viewer']),
    # 创建需求 - QA_MGR 也可（seed 中 QA_MGR 有 req:create）
    ('POST', '/requirements', {'title':'t','projectId':1,'requirementType':'URS'},
        ['admin','qa_mgr','pm','re'], ['reviewer','risk_mgr','compliance','viewer']),
    # 创建变更 - QA_MGR 也可
    ('POST', '/changes', {'title':'t','projectId':1},
        ['admin','qa_mgr','pm','re'], ['reviewer','risk_mgr','compliance','viewer']),
    # 审计日志 - QA_MGR / COMPLIANCE
    ('GET', '/compliance/audit-logs', None,
        ['admin','compliance','qa_mgr'], ['pm','re','reviewer','risk_mgr','viewer']),
    # SOUP 列表 - REVIEWER 没有
    ('GET', '/requirement/soup-components', None,
        ['admin','qa_mgr','pm','re','risk_mgr','compliance','viewer'], ['reviewer']),
    # 变更审批 - REVIEWER/RISK_MGR/COMPLIANCE/VIEWER 不可
    ('POST', '/changes/1/approve', {'comment':'test'},
        ['admin','pm','qa_mgr'], ['re','reviewer','risk_mgr','compliance','viewer']),
    # 审计日志校验 - QA_MGR / COMPLIANCE
    ('POST', '/compliance/audit-logs/verify', {'range':'all'},
        ['admin','qa_mgr','compliance'], ['pm','re','reviewer','risk_mgr','viewer']),
    # 基线列表 - RE / REVIEWER 也可
    ('GET', '/baselines/project/1', None,
        ['admin','qa_mgr','pm','re','risk_mgr','compliance','viewer'], []),
    # 项目列表 - REVIEWER 没有 proj:list
    ('GET', '/projects', None,
        ['admin','qa_mgr','pm','re','risk_mgr','compliance','viewer'], ['reviewer']),
    # 风险登记列表 - REVIEWER/COMPLIANCE 没有 risk:list
    ('GET', '/risk/register/list', None,
        ['admin','qa_mgr','pm','re','risk_mgr','viewer'], ['reviewer','compliance']),
    # 创建风险 - QA_MGR 也可
    ('POST', '/risk/register', {'name':'t'},
        ['admin','qa_mgr','pm','risk_mgr'], ['re','reviewer','compliance','viewer']),
    # 问题报告 - QA_MGR 也可
    ('GET', '/compliance/problem-reports', None,
        ['admin','qa_mgr','pm','re','risk_mgr','compliance','viewer'], ['reviewer']),
    # 创建问题报告 - QA_MGR 也可
    ('POST', '/compliance/problem-reports', {'description':'t'},
        ['admin','qa_mgr','pm','risk_mgr','compliance'], ['re','reviewer','viewer']),
    # 仪表盘 - REVIEWER 没有 report:dashboard
    ('GET', '/dashboard/view/management', None,
        ['admin','qa_mgr','pm','re','risk_mgr','compliance','viewer'], ['reviewer']),
    # 报表列表 - REVIEWER 没有 report:stats
    ('GET', '/reports', None,
        ['admin','qa_mgr','pm','re','risk_mgr','compliance','viewer'], ['reviewer']),
    # 生成报表 - 所有有 report:export 的角色（QA_MGR/RE/RISK_MGR/COMPLIANCE/VIEWER 全有）
    ('POST', '/reports/generate', {'type':'summary'},
        ['admin','qa_mgr','pm','re','risk_mgr','compliance','viewer'], []),
    # 甘特图 - REVIEWER 没有 proj:list
    ('GET', '/gantt/project/1', None,
        ['admin','qa_mgr','pm','re','risk_mgr','compliance','viewer'], ['reviewer']),
    # IPD 阶段门 - 仅 ADMIN/PM/QA_MGR
    ('GET', '/project/ipd-gate/list/1', None,
        ['admin','pm','qa_mgr'], ['re','reviewer','risk_mgr','compliance','viewer']),
    # 通知 - REVIEWER 没有 report:dashboard
    ('GET', '/notifications/unread', None,
        ['admin','qa_mgr','pm','re','risk_mgr','compliance','viewer'], ['reviewer']),
    ('PUT', '/notifications/1/read', None,
        ['admin','qa_mgr','pm','re','risk_mgr','compliance','viewer'], ['reviewer']),
    # IEC 62304 - RE/REVIEWER/RISK_MGR 不可
    ('GET', '/compliance/iec62304/checklist/1', None,
        ['admin','qa_mgr','pm','compliance'], ['re','reviewer','risk_mgr','viewer']),
    # PM 也有 compliance:iec62304
    ('POST', '/compliance/iec62304/checklist/1/run-full-check', {},
        ['admin','qa_mgr','pm','compliance'], ['re','reviewer','risk_mgr','viewer']),
    # 项目成员 - 只有 ADMIN/PM 有 proj:member
    ('GET', '/project/member/list/1', None,
        ['admin','qa_mgr','pm'], ['re','reviewer','risk_mgr','compliance','viewer']),
    # 工时 - QA_MGR 实际有 req:update
    ('POST', '/worklog', {'taskId':1,'hours':1.5},
        ['admin','qa_mgr','pm'], ['re','reviewer','risk_mgr','compliance','viewer']),
    # 需求池 - 所有
    ('GET', '/requirement-pool', None,
        ['admin','qa_mgr','pm','re','reviewer','risk_mgr','compliance','viewer'], []),
    # 需求池创建 - QA_MGR 可
    ('POST', '/requirement-pool', {'description':'t'},
        ['admin','qa_mgr','pm','re'], ['reviewer','risk_mgr','compliance','viewer']),
    # 测试用例 - QA_MGR 可
    ('GET', '/testcases', None,
        ['admin','qa_mgr','pm','re','reviewer','risk_mgr','compliance','viewer'], []),
    ('POST', '/testcases', {'title':'t'},
        ['admin','qa_mgr','pm','re'], ['reviewer','risk_mgr','compliance','viewer']),
    # ============== 新增：真正的"严格"权限拒绝测试 ==============
    # VIEWER 尝试一切写操作
    ('POST', '/requirements', {'title':'t','projectId':1,'requirementType':'URS'},
        ['admin','qa_mgr','pm','re'], ['viewer']),
    ('POST', '/changes', {'title':'t','projectId':1},
        ['admin','qa_mgr','pm','re'], ['viewer']),
    ('POST', '/risk/register', {'name':'t'},
        ['admin','qa_mgr','pm','risk_mgr'], ['viewer']),
    ('POST', '/compliance/problem-reports', {'description':'t'},
        ['admin','qa_mgr','pm','risk_mgr','compliance'], ['viewer']),
    # REVIEWER 尝试系统管理/项目管理
    ('GET', '/system/users', None, ['admin'], ['reviewer']),
    ('GET', '/project/member/list/1', None,
        ['admin','pm'], ['reviewer']),
    # RE 尝试审计日志/合规
    ('GET', '/compliance/audit-logs', None,
        ['admin','compliance','qa_mgr'], ['re']),
    ('POST', '/compliance/audit-logs/verify', {'range':'all'},
        ['admin','qa_mgr','compliance'], ['re']),
    ('GET', '/compliance/iec62304/checklist/1', None,
        ['admin','qa_mgr','pm','compliance'], ['re']),
]

passed = 0
failed = 0
fails = []
for method, path, body, allow, deny in cases:
    for role in allow:
        code = call(tokens[role], method, path, body)
        if code in (200, 201):
            passed += 1
        else:
            failed += 1
            fails.append(f'FAIL_ALLOW: {role:12s} {method:6s} {path:42s} -> {code} (expected 200/201)')
    for role in deny:
        code = call(tokens[role], method, path, body)
        if code == 403:
            passed += 1
        else:
            failed += 1
            fails.append(f'FAIL_DENY:  {role:12s} {method:6s} {path:42s} -> {code} (expected 403)')

code = call('', 'POST', '/requirements', {'title':'t','projectId':1,'requirementType':'URS'})
if code == 403:
    passed += 1
else:
    failed += 1
    fails.append(f'FAIL_UNAUTH: POST /requirements -> {code} (expected 403)')

print(f'Result: {passed} passed, {failed} failed (total {passed+failed} assertions)')
if fails:
    print('\nFailures:')
    for f in fails[:30]:
        print(' ', f)
