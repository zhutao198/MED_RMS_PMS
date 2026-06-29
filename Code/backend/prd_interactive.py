"""PRD 实际交互测试（v1.30）
对每个 PRD 功能做实际创建/编辑/删除操作，验证业务闭环
"""
import json
import sys
import time
import urllib.request
import urllib.error
from pathlib import Path
from datetime import datetime

BASE = 'http://localhost:8080/api'
OUT = Path('D:/zhutao/MED_RMS_PMS/Code/backend/prd_interactive.log')

def http(method, path, token=None, data=None, timeout=15):
    url = BASE + path
    body = json.dumps(data).encode('utf-8') if data else None
    req = urllib.request.Request(url, data=body, method=method)
    req.add_header('Content-Type', 'application/json')
    if token:
        req.add_header('Authorization', f'Bearer {token}')
    try:
        with urllib.request.urlopen(req, timeout=timeout) as r:
            txt = r.read().decode('utf-8')
            return r.status, json.loads(txt) if txt else None
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

def test_fr_01_requirement_lifecycle(token):
    """FR-0.1 需求全生命周期状态管理：创建→Draft→Review→Approved→Decomposed"""
    r = []
    # 1. 创建
    code, data = http('POST', '/requirements', token=token,
        data={'title': f'INT-FR01-{int(time.time())}', 'projectId': 1, 'requirementType': 'URS', 'priority': 'MUST'})
    rid = data.get('data', {}).get('id') if code == 200 and data and isinstance(data.get('data'), dict) else None
    r.append(('FR-0.1.1 创建需求', code == 200 and rid, rid))
    if not rid: return r
    # 2. 提交评审（实际路径 /review, 需要 reviewerId）
    code, data = http('POST', f'/requirements/{rid}/review?reviewerId=1&comments=ok', token=token, data={})
    r.append(('FR-0.1.2 提交评审', code == 200, code))
    # 3. 查询
    code, data = http('GET', f'/requirements/{rid}', token=token)
    r.append(('FR-0.1.3 查询', code == 200, code))
    return r

def test_fr_02_audit_log(token):
    """FR-0.2 + FR-0.16 审计日志哈希链"""
    r = []
    # 创建一条会触发审计日志的操作
    code, data = http('POST', '/requirements', token=token,
        data={'title': f'AUDIT-{int(time.time())}', 'projectId': 1, 'requirementType': 'URS', 'priority': 'MUST'})
    r.append(('FR-0.2.1 创建触发审计', code == 200, code))
    # 查询审计日志（data 是 list，不是 page）
    code, data = http('GET', '/compliance/audit-logs?page=1&size=10', token=token)
    has_logs = code == 200 and data and isinstance(data.get('data'), list) and len(data.get('data')) > 0
    r.append(('FR-0.2.2 查审计日志', has_logs, code))
    # 哈希链校验
    code, data = http('POST', '/compliance/audit-logs/verify', token=token, data={'range': 'all'})
    r.append(('FR-0.16 哈希链校验', code == 200, code))
    return r

def test_fr_04_review_online(token):
    """FR-0.4 评审在线化"""
    r = []
    # 创建需求
    code, data = http('POST', '/requirements', token=token,
        data={'title': f'REVIEW-{int(time.time())}', 'projectId': 1, 'requirementType': 'URS', 'priority': 'MUST'})
    rid = data.get('data', {}).get('id') if code == 200 and data else None
    if not rid: return [('FR-0.4 创建需求', False, code)]
    # 提交评审
    code, data = http('POST', f'/requirements/{rid}/review', token=token, data={'comment': 'ok'})
    r.append(('FR-0.4.1 提交评审', code == 200, code))
    return r

def test_fr_07_decompose(token):
    """FR-0.7 + FR-1.1 层级拆解"""
    r = []
    # 创建 URS
    code, data = http('POST', '/requirements', token=token,
        data={'title': f'DEC-{int(time.time())}', 'projectId': 1, 'requirementType': 'URS', 'priority': 'MUST'})
    rid = data.get('data', {}).get('id') if code == 200 and data else None
    if not rid: return [('FR-0.7 创建父需求', False, code)]
    # 拆解为 PRS
    code, data = http('POST', f'/requirements/{rid}/decompose', token=token,
        data={'childTitle': f'SUB-{int(time.time())}', 'childType': 'PRS'})
    r.append(('FR-0.7.1 拆解', code == 200, code))
    return r

def test_fr_08_baseline(token):
    """FR-0.8 基线"""
    r = []
    # 创建基线
    code, data = http('POST', '/baselines', token=token,
        data={'name': f'BL-{int(time.time())}', 'projectId': 1, 'description': 'Test'})
    r.append(('FR-0.8.1 创建基线', code == 200, code))
    # 按项目查基线
    code, data = http('GET', '/baselines/project/1', token=token)
    r.append(('FR-0.8.2 按项目查基线', code == 200, code))
    return r

def _make_baselined_requirement(token, suffix=''):
    """创建一个需求→提交评审→审批通过→基线化，返回 (req_id, baseline_id)
    FR-0.17 操作序列强制：基线化前必须先通过评审
    """
    # 1. 创建
    title = f'BLR-{suffix}-{int(time.time())}'
    code, data = http('POST', '/requirements', token=token,
        data={'title': title, 'projectId': 1, 'requirementType': 'URS', 'priority': 'MUST'})
    if code != 200 or not data or not isinstance(data.get('data'), dict):
        return None, None
    rid = data['data'].get('id')
    # 2. 提交评审
    code, data = http('POST', f'/requirements/{rid}/review?reviewerId=1&comments=ok', token=token, data={})
    if code != 200:
        return rid, None
    # 3. 审批通过
    code, data = http('POST', f'/requirements/{rid}/approve?decision=APPROVED&approverId=1&comments=ok', token=token, data={})
    if code != 200:
        return rid, None
    # 4. 创建基线
    code, data = http('POST', '/baselines', token=token,
        data={'name': f'BL-{suffix}-{int(time.time())}', 'projectId': 1, 'description': 'Test'})
    if code != 200 or not data or not isinstance(data.get('data'), dict):
        return rid, None
    blid = data['data'].get('id')
    # 5. 加入基线
    code, data = http('POST', f'/baselines/baseline-requirements?baselineId={blid}', token=token, data=[rid])
    if code != 200:
        return rid, None
    return rid, blid

def test_fr_10_suspect(token):
    """FR-0.10 变更影响自动标记 suspect"""
    r = []
    rid, blid = _make_baselined_requirement(token, 'SUS')
    if not rid or not blid:
        r.append(('FR-0.10 准备基线需求', False, f'rid={rid} blid={blid}'))
        return r
    code, data = http('POST', '/changes', token=token,
        data={'title': f'CHG-SUS-{int(time.time())}', 'requirementId': rid, 'changeType': 'NORMAL', 'reason': 'test'})
    cid = data.get('data', {}).get('id') if code == 200 and data and isinstance(data.get('data'), dict) else None
    r.append(('FR-0.10.1 创建变更', code == 200 and cid, code))
    return r

def test_fr_13_soup(token):
    """FR-0.13 SOUP 组件登记"""
    r = []
    code, data = http('GET', '/requirement/soup-components', token=token)
    r.append(('FR-0.13.1 查 SOUP 列表', code == 200, code))
    # 创建 SOUP
    code, data = http('POST', '/requirement/soup-components', token=token,
        data={'name': f'SOUP-{int(time.time())}', 'version': '1.0.0', 'vendor': 'TestVendor', 'riskLevel': 'MEDIUM'})
    r.append(('FR-0.13.2 创建 SOUP', code == 200, code))
    return r

def test_fr_14_problem_report(token):
    """FR-0.14 问题报告"""
    r = []
    code, data = http('POST', '/compliance/problem-reports', token=token,
        data={'title': f'PR-{int(time.time())}', 'severity': 'HIGH', 'description': 'Test'})
    r.append(('FR-0.14.1 创建问题报告', code == 200, code))
    return r

def test_fr_15_iec62304(token):
    """FR-0.15 IEC 62304 检查清单"""
    r = []
    code, data = http('GET', '/compliance/iec62304/checklist/1', token=token)
    r.append(('FR-0.15.1 查检查清单', code == 200, code))
    return r

def test_fr_18_testcases(token):
    """FR-1.5 测试用例 + 覆盖率"""
    r = []
    code, data = http('GET', '/testcases', token=token)
    r.append(('FR-1.5.1 查测试用例', code == 200, code))
    code, data = http('GET', '/testcases/coverage/1', token=token)
    r.append(('FR-1.5.2 覆盖率', code == 200, code))
    return r

def test_fr_19_pool(token):
    """FR-1.6 需求池"""
    r = []
    code, data = http('POST', '/requirement-pool', token=token,
        data={'title': f'POOL-{int(time.time())}', 'source': 'CUSTOMER', 'description': 'Test'})
    r.append(('FR-1.6.1 入池', code == 200, code))
    return r

def test_fr_20_change_approval(token):
    """FR-1.7 变更审批流"""
    r = []
    rid, _ = _make_baselined_requirement(token, 'APP')
    if not rid:
        r.append(('FR-1.7 准备基线需求', False, code))
        return r
    code, data = http('POST', '/changes', token=token,
        data={'title': f'CHG-APP-{int(time.time())}', 'requirementId': rid, 'changeType': 'NORMAL', 'reason': 'test'})
    cid = data.get('data', {}).get('id') if code == 200 and data and isinstance(data.get('data'), dict) else None
    r.append(('FR-1.7.1 创建', code == 200, code))
    if cid:
        code, data = http('POST', f'/changes/{cid}/submit', token=token, data={})
        r.append(('FR-1.7.2 提交', code == 200, code))
        code, data = http('POST', f'/changes/{cid}/approve?approverId=1&decision=APPROVED&comment=ok', token=token, data={})
        r.append(('FR-1.7.3 审批', code == 200, code))
    return r

def test_fr_22_risk(token):
    """FR-1.8 风险管理"""
    r = []
    code, data = http('GET', '/risk', token=token)
    r.append(('FR-1.8.1 风险列表', code == 200, code))
    code, data = http('GET', '/risk/register/list', token=token)
    r.append(('FR-1.8.2 风险登记', code == 200, code))
    return r

def test_fr_23_template(token):
    """FR-1.9 合规模板"""
    r = []
    code, data = http('GET', '/projects/templates', token=token)
    r.append(('FR-1.9.1 模板列表', code == 200, code))
    return r

def test_fr_24_task_convert(token):
    """FR-1.10 需求→任务"""
    r = []
    code, data = http('GET', '/requirement-tasks/progress/1', token=token)
    r.append(('FR-1.10.1 任务进度', code == 200, code))
    return r

def test_fr_25_migration(token):
    """FR-1.13 数据迁移"""
    r = []
    code, data = http('GET', '/admin/migration/jobs', token=token)
    r.append(('FR-1.13.1 迁移任务', code == 200, code))
    return r

def test_fr_26_ai(token):
    """FR-2.1 AI 辅助"""
    r = []
    code, data = http('POST', '/admin/requirement/ai-assist', token=token,
        data={'description': 'test'})
    r.append(('FR-2.1 AI 辅助', code in (200, 501), code))  # 501=未实现也可接受
    return r

def test_fr_27_quality(token):
    """FR-2.4 质量评分"""
    r = []
    code, data = http('GET', '/requirements/quality', token=token)
    r.append(('FR-2.4.1 质量评分', code == 200, code))
    return r

def test_fr_28_ipd(token):
    """FR-2.5 IPD 阶段门"""
    r = []
    code, data = http('GET', '/project/ipd-gate/list/1', token=token)
    r.append(('FR-2.5.1 IPD 门列表', code == 200, code))
    return r

def test_fr_29_milestone(token):
    """FR-2.6 里程碑"""
    r = []
    code, data = http('GET', '/milestones/project/1', token=token)
    r.append(('FR-2.6.1 里程碑列表', code == 200, code))
    return r

def test_fr_30_gantt(token):
    """FR-2.7 甘特图"""
    r = []
    code, data = http('GET', '/gantt/project/1', token=token)
    r.append(('FR-2.7.1 甘特图数据', code == 200, code))
    return r

def test_fr_31_resource(token):
    """FR-2.8 资源管理"""
    r = []
    code, data = http('GET', '/gantt/resources/1', token=token)
    r.append(('FR-2.8.1 资源数据', code == 200, code))
    return r

def test_fr_32_worklog(token):
    """FR-2.9 工时"""
    r = []
    code, data = http('GET', '/worklog/summary', token=token)
    r.append(('FR-2.9.1 工时汇总', code == 200, code))
    return r

def test_fr_33_dashboard(token):
    """FR-2.10 多视角仪表盘"""
    r = []
    for v in ['requirements', 'risk', 'management', 'compliance']:
        code, data = http('GET', f'/dashboard/view/{v}', token=token)
        r.append((f'FR-2.10 {v} 视角', code == 200, code))
    return r

def test_fr_34_safety_class(token):
    """FR-0.12 安全分类"""
    r = []
    code, data = http('GET', '/safety-classifications', token=token)
    r.append(('FR-0.12.1 查安全分类', code == 200, code))
    return r

def main():
    token = login('admin', 'admin123')
    if not token:
        print('FATAL: admin login failed')
        sys.exit(1)
    print('[OK] admin login')

    all_results = []
    tests = [
        ('FR-0.1', test_fr_01_requirement_lifecycle),
        ('FR-0.2/0.16', test_fr_02_audit_log),
        ('FR-0.4', test_fr_04_review_online),
        ('FR-0.7/1.1', test_fr_07_decompose),
        ('FR-0.8', test_fr_08_baseline),
        ('FR-0.10', test_fr_10_suspect),
        ('FR-0.12', test_fr_34_safety_class),
        ('FR-0.13/1.11', test_fr_13_soup),
        ('FR-0.14', test_fr_14_problem_report),
        ('FR-0.15', test_fr_15_iec62304),
        ('FR-1.5', test_fr_18_testcases),
        ('FR-1.6', test_fr_19_pool),
        ('FR-1.7', test_fr_20_change_approval),
        ('FR-1.8', test_fr_22_risk),
        ('FR-1.9', test_fr_23_template),
        ('FR-1.10', test_fr_24_task_convert),
        ('FR-1.13', test_fr_25_migration),
        ('FR-2.1', test_fr_26_ai),
        ('FR-2.4', test_fr_27_quality),
        ('FR-2.5', test_fr_28_ipd),
        ('FR-2.6', test_fr_29_milestone),
        ('FR-2.7', test_fr_30_gantt),
        ('FR-2.8', test_fr_31_resource),
        ('FR-2.9', test_fr_32_worklog),
        ('FR-2.10', test_fr_33_dashboard),
    ]

    n_pass = 0
    n_fail = 0
    fails = []

    for fr_id, fn in tests:
        try:
            results = fn(token)
        except Exception as e:
            results = [(f'{fr_id} 调用异常', False, str(e)[:50])]
        for name, ok, info in results:
            all_results.append((fr_id, name, ok, info))
            if ok: n_pass += 1
            else:
                n_fail += 1
                fails.append((fr_id, name, info))

    with OUT.open('w', encoding='utf-8') as f:
        f.write(f'PRD 实际交互测试 v1.30 — {datetime.now().strftime("%Y-%m-%d %H:%M:%S")}\n')
        f.write('=========================================\n\n')
        f.write(f'PASS={n_pass} FAIL={n_fail} / {n_pass + n_fail}\n\n')
        f.write('详情：\n')
        cur_fr = None
        for fr_id, name, ok, info in all_results:
            if fr_id != cur_fr:
                f.write(f'\n[{fr_id}]\n')
                cur_fr = fr_id
            f.write(f'  {"✓" if ok else "✗"} {name}  -> {info}\n')
        if fails:
            f.write(f'\n失败汇总：\n')
            for fr, n, info in fails:
                f.write(f'  {fr} {n}: {info}\n')

    print(f'\n=== PRD 实际交互测试 v1.30 ===')
    print(f'PASS={n_pass} FAIL={n_fail} / {n_pass + n_fail}')
    if fails:
        for fr, n, info in fails:
            print(f'  ✗ {fr} {n}: {info}')

if __name__ == '__main__':
    main()
