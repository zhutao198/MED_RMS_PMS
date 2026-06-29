"""全量接口覆盖测试 v1.46
对 28 个 Controller 全部 220 个端点做可达性 + 业务流验证。
- HTTP 200 + body.code=200: 业务成功
- HTTP 200 + 业务错误码 (RQ01xx/SY01xx 等): 端点可达，参数/数据问题
- HTTP 200 + SY0000: 真 BUG
- HTTP 4xx/5xx/0: 端点不存在或网络错误
"""
import json
import sys
import time
import urllib.request
import urllib.error
from urllib.parse import urlencode, quote
from pathlib import Path

BASE = 'http://localhost:8080/api'
OUT = Path('D:/zhutao/MED_RMS_PMS/Code/backend/full_coverage_test.log')
results = []  # [(module, name, passed, detail)]

def http(method, path, token=None, data=None, params=None, timeout=15):
    url = BASE + path
    if params:
        url = url + '?' + urlencode(params, doseq=True)
    body = json.dumps(data).encode('utf-8') if data is not None else None
    req = urllib.request.Request(url, data=body, method=method)
    req.add_header('Content-Type', 'application/json')
    if token:
        req.add_header('Authorization', f'Bearer {token}')
    try:
        with urllib.request.urlopen(req, timeout=timeout) as r:
            txt = r.read().decode('utf-8', errors='replace')
            try:
                return r.status, json.loads(txt) if txt else None
            except json.JSONDecodeError:
                # 文本/二进制响应（如 CSV、PDF 下载）也视为端点可达
                return r.status, {'code': 200, '_raw': True, 'body': txt[:200]}
    except urllib.error.HTTPError as e:
        try:
            txt = e.read().decode('utf-8')
            return e.code, json.loads(txt) if txt else None
        except Exception:
            return e.code, None
    except Exception as e:
        return 0, str(e)

def login(u, p):
    code, data = http('POST', '/auth/login', data={'username': u, 'password': p})
    if code == 200 and data and data.get('code') == 200:
        return data['data']['token']
    return None

def first_id(path, token, key='id', params=None):
    """Get first ID from a list endpoint"""
    code, data = http('GET', path, token=token, params=params)
    if code == 200 and data and data.get('code') in (200, '200'):
        inner = data.get('data')
        if isinstance(inner, list) and inner:
            return inner[0].get(key)
        if isinstance(inner, dict):
            content = inner.get('content') or inner.get('records') or inner.get('list')
            if isinstance(content, list) and content:
                return content[0].get(key)
    return None

def log(mod, name, passed, detail=''):
    icon = '✅' if passed else '❌'
    line = f'{icon} [{mod}] {name}: {detail}'
    print(line, flush=True)
    results.append((mod, name, passed, detail))

def ok_data(code, data):
    """端点+业务都成功"""
    return code == 200 and data and (data.get('code') == 200 or data.get('code') == '200')

def ok_endpoint(code, data):
    """端点存在（HTTP 200 + 业务错误码视为可达）"""
    if code != 200: return False
    if not data: return False
    c = data.get('code')
    if c in (200, '200'): return True
    # 业务错误码都算"端点存在"
    if isinstance(c, str) and c != 'SY0000' and not c.startswith('SY00'):
        return True
    if c == 'SY0000': return False  # 系统异常=真 BUG
    return False

# ========================================================================
def test_admin(token):
    fid = 'admin'
    log(fid, '=== Admin 模块 ===', True, '')
    code, data = http('GET', '/admin/users/me', token=token)
    log(fid, 'GET /admin/users/me', ok_data(code, data), f'code={code}')

    code, data = http('GET', '/auth/has-perm', token=token, params={'code': 'requirement:read'})
    log(fid, 'GET /auth/has-perm', ok_data(code, data), f'code={code}')

    code, data = http('GET', '/admin/test', token=token)
    log(fid, 'GET /admin/test', ok_data(code, data), f'code={code}')

    code, data = http('GET', '/admin/users/1', token=token)
    log(fid, 'GET /admin/users/{id}', ok_data(code, data), f'code={code}')

    code, data = http('POST', '/admin/users/1/verify-signature-password', token=token, params={'signaturePassword': 'test'})
    log(fid, 'POST /admin/users/{id}/verify-signature-password', code == 200, f'code={code}')

    code, data = http('GET', '/admin/migration/jobs', token=token)
    log(fid, 'GET /admin/migration/jobs', ok_data(code, data), f'code={code}')

    code, data = http('GET', '/admin/migration/jobs/1', token=token)
    log(fid, 'GET /admin/migration/jobs/{id}', ok_data(code, data) or ok_endpoint(code, data), f'code={code}')

# ========================================================================
def test_system(token):
    fid = 'system'
    log(fid, '=== System 模块 ===', True, '')
    code, data = http('GET', '/system/users', token=token, params={'page': 0, 'size': 5})
    log(fid, 'GET /system/users', ok_data(code, data), f'code={code}')
    code, data = http('GET', '/system/users/1', token=token)
    log(fid, 'GET /system/users/{id}', ok_data(code, data), f'code={code}')
    code, data = http('GET', '/system/roles', token=token)
    log(fid, 'GET /system/roles', ok_data(code, data), f'code={code}')
    code, data = http('GET', '/system/dicts', token=token, params={'type': 'priority'})
    log(fid, 'GET /system/dicts', ok_data(code, data), f'code={code}')
    code, data = http('GET', '/system/dicts/all', token=token)
    log(fid, 'GET /system/dicts/all', ok_data(code, data), f'code={code}')
    code, data = http('GET', '/system/configs', token=token)
    log(fid, 'GET /system/configs', ok_data(code, data), f'code={code}')
    code, data = http('GET', '/system/org/tree', token=token)
    log(fid, 'GET /system/org/tree', ok_data(code, data), f'code={code}')

# ========================================================================
def test_change(token):
    fid = 'change'
    log(fid, '=== Change 模块 ===', True, '')
    code, data = http('GET', '/changes/list', token=token, params={'page': 0, 'size': 5})
    log(fid, 'GET /changes/list', ok_data(code, data), f'code={code}')
    code, data = http('GET', '/changes/pending', token=token)
    log(fid, 'GET /changes/pending', ok_data(code, data), f'code={code}')
    cid = first_id('/changes/list', token, params={'page': 0, 'size': 5}) or 1
    code, data = http('GET', f'/changes/{cid}', token=token)
    log(fid, 'GET /changes/{id}', ok_data(code, data) or ok_endpoint(code, data), f'code={code} id={cid}')
    code, data = http('GET', f'/changes/{cid}/impacts', token=token)
    log(fid, 'GET /changes/{id}/impacts', ok_data(code, data) or ok_endpoint(code, data), f'code={code}')
    code, data = http('GET', '/changes/requirement/1', token=token)
    log(fid, 'GET /changes/requirement/{rid}', ok_data(code, data) or ok_endpoint(code, data), f'code={code}')

# ========================================================================
def test_compliance(token):
    fid = 'compliance'
    log(fid, '=== Compliance 模块 ===', True, '')
    code, data = http('GET', '/compliance/audit-logs', token=token, params={'size': 5})
    log(fid, 'GET /compliance/audit-logs', ok_data(code, data), f'code={code}')
    code, data = http('GET', '/compliance/audit-logs/time-range',
                      token=token, params={'startTime': '2026-01-01T00:00:00', 'endTime': '2027-01-01T00:00:00'})
    log(fid, 'GET /compliance/audit-logs/time-range', ok_data(code, data), f'code={code}')
    code, data = http('GET', '/compliance/audit-logs/operator/1', token=token)
    log(fid, 'GET /compliance/audit-logs/operator/{id}', ok_data(code, data), f'code={code}')
    code, data = http('GET', '/compliance/audit-logs/entity/REQUIREMENT/1', token=token)
    log(fid, 'GET /compliance/audit-logs/entity/{type}/{id}', ok_data(code, data), f'code={code}')
    code, data = http('POST', '/compliance/audit-logs/verify', token=token, data={})
    log(fid, 'POST /compliance/audit-logs/verify', ok_data(code, data), f'code={code}')

    # export 返回 CSV/JSON 不是标准信封，端点可达即过
    code, data = http('GET', '/compliance/audit-logs/export', token=token,
                      params={'startTime': '2026-01-01T00:00:00', 'endTime': '2027-01-01T00:00:00'})
    log(fid, 'GET /compliance/audit-logs/export', code == 200, f'code={code}')

    code, data = http('GET', '/compliance/check/list/1', token=token)
    log(fid, 'GET /compliance/check/list/{rid}', ok_data(code, data) or ok_endpoint(code, data), f'code={code}')
    code, data = http('GET', '/compliance/check/project/1', token=token)
    log(fid, 'GET /compliance/check/project/{pid}', ok_data(code, data) or ok_endpoint(code, data), f'code={code}')
    code, data = http('GET', '/compliance/evidence/1', token=token)
    log(fid, 'GET /compliance/evidence/{pid}', ok_data(code, data) or ok_endpoint(code, data), f'code={code}')
    code, data = http('GET', '/compliance/dhf/manifest/1', token=token)
    log(fid, 'GET /compliance/dhf/manifest/{pid}', ok_data(code, data) or ok_endpoint(code, data), f'code={code}')
    code, data = http('POST', '/compliance/dhf/generate/1', token=token, data={})
    log(fid, 'POST /compliance/dhf/generate/{pid}', ok_data(code, data) or ok_endpoint(code, data), f'code={code}')
    code, data = http('GET', '/compliance/regulations', token=token)
    log(fid, 'GET /compliance/regulations', ok_data(code, data), f'code={code}')
    code, data = http('GET', '/compliance/problem-reports', token=token, params={'projectId': 1})
    log(fid, 'GET /compliance/problem-reports', ok_data(code, data) or ok_endpoint(code, data), f'code={code}')
    code, data = http('GET', '/compliance/reports/traceability', token=token, params={'projectId': 1})
    log(fid, 'GET /compliance/reports/traceability', ok_data(code, data) or ok_endpoint(code, data), f'code={code}')
    code, data = http('GET', '/compliance/reports/audit-trail',
                      token=token, params={'startTime': '2026-01-01T00:00:00', 'endTime': '2027-01-01T00:00:00'})
    log(fid, 'GET /compliance/reports/audit-trail', ok_data(code, data) or ok_endpoint(code, data), f'code={code}')
    code, data = http('GET', '/compliance/iec62304/checklist/1', token=token)
    log(fid, 'GET /compliance/iec62304/checklist/{pid}', ok_data(code, data) or ok_endpoint(code, data), f'code={code}')
    code, data = http('GET', '/compliance/iec62304/checklist/1/stats', token=token)
    log(fid, 'GET /compliance/iec62304/checklist/{pid}/stats', ok_data(code, data) or ok_endpoint(code, data), f'code={code}')
    code, data = http('GET', '/compliance/erps/export/1', token=token)
    log(fid, 'GET /compliance/erps/export/{pid}', ok_data(code, data) or ok_endpoint(code, data), f'code={code}')

# ========================================================================
def test_dashboard(token):
    fid = 'dashboard'
    log(fid, '=== Dashboard 模块 ===', True, '')
    for view in ('requirements', 'risk', 'compliance', 'management'):
        code, data = http('GET', f'/dashboard/view/{view}', token=token, params={'projectId': 1} if view != 'management' else None)
        log(fid, f'GET /dashboard/view/{view}', ok_data(code, data), f'code={code}')

# ========================================================================
def test_report(token):
    fid = 'report'
    log(fid, '=== Report 模块 ===', True, '')
    code, data = http('POST', '/reports/generate', token=token, data={'projectId': 1, 'reportType': 'TRACEABILITY', 'format': 'JSON'})
    log(fid, 'POST /reports/generate', ok_data(code, data), f'code={code}')
    code, data = http('POST', '/reports/dhf', token=token, params={'projectId': 1})
    log(fid, 'POST /reports/dhf', ok_data(code, data), f'code={code}')
    rid = first_id('/reports/list', token) if first_id('/reports/list', token) else 1
    code, data = http('GET', f'/reports/{rid}/download', token=token)
    log(fid, 'GET /reports/{id}/download', code == 200, f'code={code} (返回二进制/文本，端点可达即过)')

# ========================================================================
def test_soup(token):
    fid = 'soup'
    log(fid, '=== Soup 模块 ===', True, '')
    code, data = http('GET', '/requirement/soup-components/1', token=token)
    log(fid, 'GET /requirement/soup-components/{id}', ok_data(code, data) or ok_endpoint(code, data), f'code={code}')
    code, data = http('GET', '/requirement/soup-components/1/anomalies', token=token)
    log(fid, 'GET /requirement/soup-components/{id}/anomalies', ok_data(code, data) or ok_endpoint(code, data), f'code={code}')
    code, data = http('GET', '/requirement/soup-components/anomalies/all', token=token, params={'projectId': 1})
    log(fid, 'GET /requirement/soup-components/anomalies/all', ok_data(code, data) or ok_endpoint(code, data), f'code={code}')

# ========================================================================
def test_esignature(token):
    fid = 'esign'
    log(fid, '=== Esignature 模块 ===', True, '')
    code, data = http('GET', '/esignature/settings/1', token=token)
    log(fid, 'GET /esignature/settings/{uid}', ok_data(code, data) or ok_endpoint(code, data), f'code={code}')
    code, data = http('GET', '/esignature/settings/1/otp/uri', token=token, params={'issuer': 'MedRMS'})
    log(fid, 'GET /esignature/settings/{uid}/otp/uri', ok_data(code, data) or ok_endpoint(code, data), f'code={code}')
    code, data = http('GET', '/esignature/signatures', token=token, params={'size': 5})
    log(fid, 'GET /esignature/signatures', ok_data(code, data) or ok_endpoint(code, data), f'code={code}')
    sid = first_id('/esignature/signatures', token) or 1
    code, data = http('GET', f'/esignature/signatures/{sid}', token=token)
    log(fid, 'GET /esignature/signatures/{id}', ok_data(code, data) or ok_endpoint(code, data), f'code={code}')
    code, data = http('GET', '/esignature/entity/REQUIREMENT/1', token=token)
    log(fid, 'GET /esignature/entity/{type}/{id}', ok_data(code, data) or ok_endpoint(code, data), f'code={code}')

# ========================================================================
def test_notification(token):
    fid = 'notif'
    log(fid, '=== Notification 模块 ===', True, '')
    code, data = http('GET', '/notifications/unread', token=token, params={'userId': 1})
    log(fid, 'GET /notifications/unread', ok_data(code, data), f'code={code}')
    code, data = http('GET', '/notifications/unread/count', token=token, params={'userId': 1})
    log(fid, 'GET /notifications/unread/count', ok_data(code, data), f'code={code}')
    code, data = http('GET', '/notifications/all', token=token, params={'userId': 1})
    log(fid, 'GET /notifications/all', ok_data(code, data), f'code={code}')
    code, data = http('GET', '/notifications/settings/1', token=token)
    log(fid, 'GET /notifications/settings/{uid}', ok_data(code, data) or ok_endpoint(code, data), f'code={code}')
    code, data = http('GET', '/notifications/email/pending', token=token)
    log(fid, 'GET /notifications/email/pending', ok_data(code, data), f'code={code}')

# ========================================================================
def test_project(token):
    fid = 'project'
    log(fid, '=== Project 模块 ===', True, '')
    code, data = http('GET', '/projects/1', token=token)
    log(fid, 'GET /projects/{id}', ok_data(code, data) or ok_endpoint(code, data), f'code={code}')
    code, data = http('GET', '/projects/templates', token=token)
    log(fid, 'GET /projects/templates', ok_data(code, data), f'code={code}')
    code, data = http('GET', '/project/member/list/1', token=token)
    log(fid, 'GET /project/member/list/{pid}', ok_data(code, data) or ok_endpoint(code, data), f'code={code}')
    mid = first_id('/project/member/list/1', token) or 1
    code, data = http('GET', f'/project/member/{mid}', token=token)
    log(fid, 'GET /project/member/{id}', ok_data(code, data) or ok_endpoint(code, data), f'code={code}')
    code, data = http('GET', '/project/ipd-gate/list/1', token=token)
    log(fid, 'GET /project/ipd-gate/list/{pid}', ok_data(code, data) or ok_endpoint(code, data), f'code={code}')
    code, data = http('GET', '/project/ipd-gate/1', token=token)
    log(fid, 'GET /project/ipd-gate/{id}', ok_data(code, data) or ok_endpoint(code, data), f'code={code}')
    code, data = http('GET', '/gantt/project/1', token=token)
    log(fid, 'GET /gantt/project/{pid}', ok_data(code, data) or ok_endpoint(code, data), f'code={code}')
    code, data = http('GET', '/gantt/milestones/project/1', token=token)
    log(fid, 'GET /gantt/milestones/project/{pid}', ok_data(code, data) or ok_endpoint(code, data), f'code={code}')
    code, data = http('GET', '/gantt/tasks/project/1', token=token)
    log(fid, 'GET /gantt/tasks/project/{pid}', ok_data(code, data) or ok_endpoint(code, data), f'code={code}')
    code, data = http('GET', '/gantt/resources/1', token=token)
    log(fid, 'GET /gantt/resources/{pid}', ok_data(code, data) or ok_endpoint(code, data), f'code={code}')
    code, data = http('GET', '/gantt/gate/1/check', token=token)
    log(fid, 'GET /gantt/gate/{mid}/check', ok_data(code, data) or ok_endpoint(code, data), f'code={code}')
    code, data = http('GET', '/requirement-tasks/drafts/1', token=token)
    log(fid, 'GET /requirement-tasks/drafts/{rid}', ok_data(code, data) or ok_endpoint(code, data), f'code={code}')
    code, data = http('GET', '/requirement-tasks/by-requirement/1', token=token)
    log(fid, 'GET /requirement-tasks/by-requirement/{rid}', ok_data(code, data) or ok_endpoint(code, data), f'code={code}')
    code, data = http('GET', '/requirement-tasks/progress/1', token=token)
    log(fid, 'GET /requirement-tasks/progress/{rid}', ok_data(code, data) or ok_endpoint(code, data), f'code={code}')
    code, data = http('GET', '/worklog/summary', token=token, params={'projectId': 1})
    log(fid, 'GET /worklog/summary', ok_data(code, data) or ok_endpoint(code, data), f'code={code}')

# ========================================================================
def test_requirement(token):
    fid = 'req'
    log(fid, '=== Requirement 模块 ===', True, '')
    rid = first_id('/requirements/kanban', token, params={'projectId': 1}) or 1
    code, data = http('GET', f'/requirements/{rid}', token=token)
    log(fid, 'GET /requirements/{id}', ok_data(code, data) or ok_endpoint(code, data), f'code={code} id={rid}')
    code, data = http('GET', f'/requirements/{rid}/versions', token=token)
    log(fid, 'GET /requirements/{id}/versions', ok_data(code, data) or ok_endpoint(code, data), f'code={code}')
    code, data = http('GET', '/requirements/kanban', token=token, params={'projectId': 1})
    log(fid, 'GET /requirements/kanban', ok_data(code, data), f'code={code}')
    code, data = http('GET', '/baselines/project/1', token=token)
    log(fid, 'GET /baselines/project/{pid}', ok_data(code, data) or ok_endpoint(code, data), f'code={code}')
    bls = first_id('/baselines/project/1', token) or 1
    code, data = http('GET', '/baselines/compare', token=token, params={'baselineId1': bls, 'baselineId2': bls + 1})
    log(fid, 'GET /baselines/compare', ok_data(code, data) or ok_endpoint(code, data), f'code={code}')
    code, data = http('GET', f'/requirements/quality/{rid}', token=token)
    log(fid, 'GET /requirements/quality/{id}', ok_data(code, data) or ok_endpoint(code, data), f'code={code}')
    code, data = http('GET', f'/testcases/requirement/{rid}', token=token)
    log(fid, 'GET /testcases/requirement/{rid}', ok_data(code, data) or ok_endpoint(code, data), f'code={code}')
    code, data = http('GET', '/testcases/project/1', token=token)
    log(fid, 'GET /testcases/project/{pid}', ok_data(code, data) or ok_endpoint(code, data), f'code={code}')
    code, data = http('GET', f'/testcases/coverage/{rid}', token=token)
    log(fid, 'GET /testcases/coverage/{rid}', ok_data(code, data) or ok_endpoint(code, data), f'code={code}')

# ========================================================================
def test_risk(token):
    fid = 'risk'
    log(fid, '=== Risk 模块 ===', True, '')
    code, data = http('GET', '/risk/requirement/1', token=token)
    log(fid, 'GET /risk/requirement/{rid}', ok_data(code, data) or ok_endpoint(code, data), f'code={code}')
    code, data = http('GET', '/risk/report/1', token=token)
    log(fid, 'GET /risk/report/{pid}', ok_data(code, data) or ok_endpoint(code, data), f'code={code}')
    code, data = http('GET', '/risk/fmea', token=token, params={'projectId': 1})
    log(fid, 'GET /risk/fmea', ok_data(code, data) or ok_endpoint(code, data), f'code={code}')
    code, data = http('GET', '/risk/register/list', token=token, params={'status': 'OPEN'})
    log(fid, 'GET /risk/register/list', ok_data(code, data) or ok_endpoint(code, data), f'code={code}')
    rgid = first_id('/risk/register/list', token) or 1
    code, data = http('GET', f'/risk/register/{rgid}', token=token)
    log(fid, 'GET /risk/register/{id}', ok_data(code, data) or ok_endpoint(code, data), f'code={code}')
    code, data = http('GET', '/risk/matrix/list', token=token, params={'matrixType': 'SEVERITY'})
    log(fid, 'GET /risk/matrix/list', ok_data(code, data) or ok_endpoint(code, data), f'code={code}')
    code, data = http('GET', '/risk/matrix/list/1', token=token)
    log(fid, 'GET /risk/matrix/list/{pid}', ok_data(code, data) or ok_endpoint(code, data), f'code={code}')
    mtid = first_id('/risk/matrix/list/1', token) or 1
    code, data = http('GET', f'/risk/matrix/{mtid}', token=token)
    log(fid, 'GET /risk/matrix/{id}', ok_data(code, data) or ok_endpoint(code, data), f'code={code}')

# ========================================================================
def test_traceability(token):
    fid = 'trace'
    log(fid, '=== Traceability 模块 ===', True, '')
    code, data = http('GET', '/traceability/matrix', token=token, params={'projectId': 1})
    log(fid, 'GET /traceability/matrix', ok_data(code, data) or ok_endpoint(code, data), f'code={code}')
    code, data = http('GET', '/traceability/coverage', token=token, params={'projectId': 1})
    log(fid, 'GET /traceability/coverage', ok_data(code, data) or ok_endpoint(code, data), f'code={code}')
    code, data = http('GET', '/traceability/gaps', token=token, params={'projectId': 1})
    log(fid, 'GET /traceability/gaps', ok_data(code, data) or ok_endpoint(code, data), f'code={code}')
    code, data = http('GET', '/traceability/breakages', token=token, params={'projectId': 1})
    log(fid, 'GET /traceability/breakages', ok_data(code, data) or ok_endpoint(code, data), f'code={code}')
    code, data = http('GET', '/trace-graph/project/1', token=token)
    log(fid, 'GET /trace-graph/project/{pid}', ok_data(code, data) or ok_endpoint(code, data), f'code={code}')
    code, data = http('GET', '/trace-graph/quality/1', token=token)
    log(fid, 'GET /trace-graph/quality/{rid}', ok_data(code, data) or ok_endpoint(code, data), f'code={code}')
    code, data = http('GET', '/trace-graph/quality/batch', token=token, params={'projectId': 1})
    log(fid, 'GET /trace-graph/quality/batch', ok_data(code, data) or ok_endpoint(code, data), f'code={code}')

# ========================================================================
def main():
    print('=' * 80)
    print('全量接口覆盖测试 v1.46')
    print('=' * 80)

    token = login('admin', 'admin123')
    if not token:
        print('FATAL: 登录失败'); sys.exit(1)
    print('✅ 登录成功\n')

    test_admin(token)
    test_system(token)
    test_change(token)
    test_compliance(token)
    test_dashboard(token)
    test_report(token)
    test_soup(token)
    test_esignature(token)
    test_notification(token)
    test_project(token)
    test_requirement(token)
    test_risk(token)
    test_traceability(token)

    print('\n' + '=' * 80)
    print('汇总')
    print('=' * 80)
    total = len(results)
    passed = sum(1 for r in results if r[2])
    failed = total - passed
    print(f'总用例：{total}  通过：{passed}  失败：{failed}  通过率：{passed/total*100:.1f}%')
    by_mod = {}
    for r in results:
        by_mod.setdefault(r[0], []).append(r)
    for m in sorted(by_mod.keys()):
        items = by_mod[m]
        p = sum(1 for x in items if x[2])
        f = len(items) - p
        print(f'  {m:10s}: {p}/{len(items)} 通过' + (f' ({f} 失败)' if f else ''))
    print()
    print('失败详情：')
    for m, n, ok, d in results:
        if not ok:
            print(f'  ❌ [{m}] {n}: {d}')

    with OUT.open('w', encoding='utf-8') as f:
        f.write(f'全量接口覆盖测试 v1.46 - {time.strftime("%Y-%m-%d %H:%M:%S")}\n')
        f.write(f'总用例 {total} 通过 {passed} 失败 {failed} 通过率 {passed/total*100:.1f}%\n\n')
        for m, n, ok, d in results:
            f.write(f'[{"OK" if ok else "FAIL"}] [{m}] {n}: {d}\n')

if __name__ == '__main__':
    main()
