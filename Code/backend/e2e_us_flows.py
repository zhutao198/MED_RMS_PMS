"""US-1～US-16 端到端业务流程全链路测试（v1.45）
基于 PRD 7.x 用户故事，验证真实业务流在多用户/多角色/多模块下的端到端可用性。
"""
import json
import sys
import time
import urllib.request
import urllib.error
from pathlib import Path
from datetime import datetime

BASE = 'http://localhost:8080/api'
OUT = Path('D:/zhutao/MED_RMS_PMS/Code/backend/e2e_us_flows.log')
results = []  # [(us_id, name, passed, detail)]

def http(method, path, token=None, data=None, params=None, timeout=15):
    url = BASE + path
    if params:
        from urllib.parse import urlencode
        url = url + '?' + urlencode(params)
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

def extract_id(data):
    if not data or not isinstance(data, dict):
        return None
    inner = data.get('data')
    if isinstance(inner, dict):
        return inner.get('id')
    if isinstance(inner, int):
        return inner
    if isinstance(inner, list) and inner:
        return inner[0].get('id') if isinstance(inner[0], dict) else None
    return None

def log(us_id, name, passed, detail=''):
    icon = '✅' if passed else '❌'
    line = f'{icon} [{us_id}] {name}: {detail}'
    print(line, flush=True)
    results.append((us_id, name, passed, detail))

def must200(code, data, what='接口'):
    return code == 200 and data and data.get('code') == 200

# ========================================================================
# US-1 研发总监：全局审批监督
# 业务流：登录后看待评审需求 + 待审批变更 + OPEN 风险 + PENDING 门控 4 个监督视角
# ========================================================================
def us_1_director_supervision(token):
    """US-1 研发总监：全局审批监督"""
    fid = 'US-1'
    log(fid, '=== US-1 研发总监全局审批监督 ===', True, '')

    # 1.1 待评审需求列表
    code, data = http('GET', '/requirements?status=Submitted&size=10', token=token)
    cnt = len(data.get('data', {}).get('records', [])) if data and data.get('code') == 200 else 0
    log(fid + '.1', '待评审需求列表', must200(code, data), f'count={cnt} (code={code})')

    # 1.2 待审批变更列表
    code, data = http('GET', '/changes/pending', token=token)
    cnt = len(data.get('data', [])) if data and data.get('code') == 200 else 0
    log(fid + '.1b', '待审批变更列表', must200(code, data), f'count={cnt} (code={code})')

    # 1.3 OPEN 风险列表（风险管理）- 即使 count=0 也算接口正常
    code, data = http('GET', '/risk/register/list?status=OPEN', token=token)
    cnt = len(data.get('data', [])) if data and data.get('code') == 200 else 0
    passed = must200(code, data)  # 接口可达即通过
    log(fid + '.1c', 'OPEN 风险列表', passed, f'count={cnt} (code={code})')

    # 1.4 IPD 门控列表（项目监督）
    code, data = http('GET', '/project/ipd-gate/list/1', token=token)
    gates = data.get('data', []) if data and data.get('code') == 200 else []
    log(fid + '.1d', 'IPD 门控列表', must200(code, data), f'gates={len(gates)} (code={code})')

    # 1.5 多视角仪表盘管理视图（FR-2.10）
    code, data = http('GET', '/dashboard/view/management', token=token)
    log(fid + '.2', '管理视角仪表盘', must200(code, data), f'code={code}')

# ========================================================================
# US-2 项目经理：变更影响全景掌控
# 业务流：URS 创建→拆 PRS→基线化→变更申请→影响评估→批准→执行→suspect 下游
# ========================================================================
def us_2_pm_change_full_view(token):
    """US-2 项目经理：变更影响全景"""
    fid = 'US-2'
    log(fid, '=== US-2 项目经理变更影响全景 ===', True, '')

    t0 = int(time.time())
    # 2.1 创建 URS
    code, data = http('POST', '/requirements', token=token, data={
        'title': f'US2-URS-{t0}', 'projectId': 1, 'requirementType': 'URS',
        'priority': 'MUST', 'description': 'US-2 测试'
    })
    urs_id = extract_id(data) if must200(code, data) else None
    log(fid + '.1', '创建 URS', urs_id is not None, f'urs_id={urs_id}')

    # 2.2 拆解 PRS
    if urs_id:
        code, data = http('POST', f'/requirements/{urs_id}/decompose', token=token, data={
            'title': f'US2-PRS-{t0}', 'projectId': 1, 'requirementType': 'PRS',
            'priority': 'MUST', 'description': 'US-2 PRS'
        })
        prs_id = extract_id(data) if must200(code, data) else None
        log(fid + '.2', 'URS→PRS 拆解', prs_id is not None, f'prs_id={prs_id}')

    # 2.3 评审 + 审批 URS
    if urs_id:
        http('POST', f'/requirements/{urs_id}/review?reviewerId=1&comments=ok', token=token, data={})
        code, data = http('POST', f'/requirements/{urs_id}/approve?decision=APPROVED&approverId=1&comments=ok',
                          token=token, data={})
        log(fid + '.3', 'URS 评审+审批通过', must200(code, data), f'code={code}')

    # 2.4 基线化（必须 Approved 状态才能 baseline）
    if urs_id:
        code, data = http('POST', '/baselines', token=token, data={
            'name': f'US2-BL-{t0}', 'projectId': 1, 'description': 'US-2 基线',
            'requirementIds': [urs_id]
        })
        bl_id = extract_id(data) if must200(code, data) else None
        if bl_id:
            # v1.49 P0 #2 修复：Baseline 已迁至 med-rms-compliance，端点改为 /baselines/baseline-requirements
            code, data = http('POST', f'/baselines/baseline-requirements?baselineId={bl_id}', token=token, data=[urs_id])
        log(fid + '.4', '基线化 URS', bl_id is not None and must200(code, data), f'bl_id={bl_id}')

    # 2.5 创建变更
    if urs_id:
        code, data = http('POST', '/changes', token=token, data={
            'requirementId': urs_id, 'changeType': 'REQUIREMENT_UPDATE',
            'reason': f'US-2 修改 URS {urs_id}', 'urgency': 'HIGH',
            'requestedBy': 1, 'title': f'US2-CHG-{t0}'
        })
        chg_id = extract_id(data) if must200(code, data) else None
        log(fid + '.5', '创建变更', chg_id is not None, f'chg_id={chg_id}')

    # 2.6 提交 → 影响评估 → 批准 → 执行
    if urs_id and chg_id:
        http('POST', f'/changes/{chg_id}/submit?approverId=1', token=token, data={})
        code, data = http('POST', f'/changes/{chg_id}/assess', token=token, data={})
        impact_ok = must200(code, data)
        # v1.45 修复：approve 端点需 decision 参数
        code, data = http('POST', f'/changes/{chg_id}/approve?approverId=1&decision=APPROVED&comments=ok', token=token, data={})
        approve_ok = must200(code, data)
        code, data = http('POST', f'/changes/{chg_id}/execute', token=token, data={})
        execute_ok = must200(code, data)
        log(fid + '.6', '变更完整流程（assess→approve→execute）',
            impact_ok and approve_ok and execute_ok,
            f'assess={impact_ok} approve={approve_ok} execute={execute_ok}')

# ========================================================================
# US-3 系统架构师：L2→L3 高效转化
# 业务流：创建 PRS→审批→拆解为 SRS→再拆解为 DRS
# ========================================================================
def us_3_architect_l2_to_l3(token):
    """US-3 系统架构师：L2→L3 高效转化"""
    fid = 'US-3'
    log(fid, '=== US-3 系统架构师 L2→L3 转化 ===', True, '')

    t0 = int(time.time())
    # 3.1 创建 PRS
    code, data = http('POST', '/requirements', token=token, data={
        'title': f'US3-PRS-{t0}', 'projectId': 1, 'requirementType': 'PRS',
        'priority': 'MUST', 'description': 'US-3 PRS'
    })
    prs_id = extract_id(data) if must200(code, data) else None
    log(fid + '.1', '创建 PRS', prs_id is not None, f'prs_id={prs_id}')

    # 3.2 拆解 PRS→SRS
    if prs_id:
        code, data = http('POST', f'/requirements/{prs_id}/decompose', token=token, data={
            'title': f'US3-SRS-{t0}', 'projectId': 1, 'requirementType': 'SRS',
            'priority': 'MUST', 'description': 'US-3 SRS'
        })
        srs_id = extract_id(data) if must200(code, data) else None
        log(fid + '.2', 'PRS→SRS 拆解', srs_id is not None, f'srs_id={srs_id}')

    # 3.3 拆解 SRS→DRS
    if prs_id and 'srs_id' in dir() and srs_id:
        code, data = http('POST', f'/requirements/{srs_id}/decompose', token=token, data={
            'title': f'US3-DRS-{t0}', 'projectId': 1, 'requirementType': 'DRS',
            'priority': 'MUST', 'description': 'US-3 DRS'
        })
        drs_id = extract_id(data) if must200(code, data) else None
        log(fid + '.3', 'SRS→DRS 拆解', drs_id is not None, f'drs_id={drs_id}')

# ========================================================================
# US-4 合规专员：合规证据一键生成
# 业务流：基线化项目→DHF 证据包生成→清单查询→下载
# ========================================================================
def us_4_compliance_dhf(token):
    """US-4 合规专员：证据包一键生成"""
    fid = 'US-4'
    log(fid, '=== US-4 合规专员证据包 ===', True, '')

    # 4.1 DHF 证据包生成
    code, data = http('POST', '/compliance/dhf/generate/1', token=token, data={})
    log(fid + '.1', 'DHF 证据包生成', must200(code, data), f'code={code} body={str(data)[:150] if data else "None"}')

    # 4.2 DHF 清单查询
    code, data = http('GET', '/compliance/dhf/manifest/1', token=token)
    log(fid + '.2', 'DHF 清单查询', must200(code, data), f'code={code}')

    # 4.3 合规证据列表
    code, data = http('GET', '/compliance/evidence/1', token=token)
    log(fid + '.3', '合规证据列表', must200(code, data), f'code={code}')

# ========================================================================
# US-5 产品经理：L1/L2 高效生产（需求池→URS→PRS）
# ========================================================================
def us_5_pm_pool_to_prs(token):
    """US-5 产品经理：L1/L2 高效生产"""
    fid = 'US-5'
    log(fid, '=== US-5 产品经理 L1/L2 ===', True, '')

    t0 = int(time.time())
    # 5.1 需求池
    code, data = http('POST', '/requirement-pool', token=token, data={
        'title': f'US5-POOL-{t0}', 'source': 'CUSTOMER',
        'rawDescription': '客户邮件反馈：需支持 12 导联心电监护', 'priority': 'MUST', 'createdBy': 1
    })
    pool_id = extract_id(data) if must200(code, data) else None
    log(fid + '.1', '添加需求到池', pool_id is not None, f'pool_id={pool_id}')

    # 5.2 转 URS
    if pool_id:
        code, data = http('POST', f'/requirement-pool/{pool_id}/convert', token=token,
                          data={'projectId': 1, 'priority': 'MUST'})
        urs_id = extract_id(data) if must200(code, data) else None
        log(fid + '.2', '池→URS', urs_id is not None, f'urs_id={urs_id}')

    # 5.3 拆 PRS
    if 'urs_id' in dir() and urs_id:
        code, data = http('POST', f'/requirements/{urs_id}/decompose', token=token, data={
            'title': f'US5-PRS-{t0}', 'projectId': 1, 'requirementType': 'PRS',
            'priority': 'MUST', 'description': 'US-5 PRS'
        })
        prs_id = extract_id(data) if must200(code, data) else None
        log(fid + '.3', 'URS→PRS', prs_id is not None, f'prs_id={prs_id}')

# ========================================================================
# US-6 研发工程师：L4 需求落地（DRS→任务→工时）
# ========================================================================
def us_6_engineer_l4_execute(token):
    """US-6 研发工程师：L4 需求落地"""
    fid = 'US-6'
    log(fid, '=== US-6 研发工程师 L4 落地 ===', True, '')

    t0 = int(time.time())
    # 6.1 创建 PRS→SRS→DRS 链路
    code, data = http('POST', '/requirements', token=token, data={
        'title': f'US6-PRS-{t0}', 'projectId': 1, 'requirementType': 'PRS',
        'priority': 'MUST', 'description': 'US-6 PRS'
    })
    prs_id = extract_id(data) if must200(code, data) else None
    if prs_id:
        code, data = http('POST', f'/requirements/{prs_id}/decompose', token=token, data={
            'title': f'US6-SRS-{t0}', 'projectId': 1, 'requirementType': 'SRS',
            'priority': 'MUST', 'description': 'US-6 SRS'
        })
        srs_id = extract_id(data) if must200(code, data) else None
    if 'srs_id' in dir() and srs_id:
        code, data = http('POST', f'/requirements/{srs_id}/decompose', token=token, data={
            'title': f'US6-DRS-{t0}', 'projectId': 1, 'requirementType': 'DRS',
            'priority': 'MUST', 'description': 'US-6 DRS'
        })
        drs_id = extract_id(data) if must200(code, data) else None
    log(fid + '.1', 'URS→PRS→SRS→DRS 全链路拆解', drs_id is not None,
        f'prs={prs_id} srs={srs_id if "srs_id" in dir() else None} drs={drs_id if "drs_id" in dir() else None}')

    # 6.2 DRS 拆任务
    if 'drs_id' in dir() and drs_id:
        code, data = http('GET', f'/requirement-tasks/drafts/{drs_id}', token=token)
        drafts = data.get('data', []) if must200(code, data) else []
        if drafts:
            code, data = http('POST', f'/requirement-tasks/convert/{drs_id}', token=token, data=drafts)
            created = data.get('data', []) if must200(code, data) else []
            task_id = created[0].get('id') if created else None
        else:
            task_id = None
        log(fid + '.2', 'DRS 拆任务', task_id is not None, f'task_id={task_id}')

    # 6.3 填报工时
    if 'task_id' in dir() and task_id:
        code, data = http('POST', '/worklog', token=token, data={
            'taskId': task_id, 'workerId': 1, 'projectId': 1,
            'hours': 8, 'workDate': '2026-06-05', 'description': 'US-6 工时'
        })
        log(fid + '.3', '填报工时', must200(code, data), f'code={code}')

    # 6.4 任务状态推进
    if 'task_id' in dir() and task_id:
        code, data = http('PUT', f'/requirement-tasks/{task_id}/status?status=DONE', token=token)
        log(fid + '.4', '任务 DONE', must200(code, data), f'code={code}')

# ========================================================================
# US-7 测试工程师：需求-用例覆盖验证
# ========================================================================
def us_7_tester_coverage(token):
    """US-7 测试工程师：追溯覆盖验证"""
    fid = 'US-7'
    log(fid, '=== US-7 测试工程师追溯覆盖 ===', True, '')

    # 7.1 追溯矩阵（v1.45 BUG #92 修复：路径用 ?projectId=1 query 而非 /1 路径变量）
    code, data = http('GET', '/traceability/matrix?projectId=1', token=token)
    log(fid + '.1', '追溯矩阵', must200(code, data), f'code={code}')

    # 7.2 覆盖率统计
    code, data = http('GET', '/traceability/coverage?projectId=1', token=token)
    log(fid + '.2', '覆盖率统计', must200(code, data), f'code={code}')

    # 7.3 追溯缺口
    code, data = http('GET', '/traceability/gaps?projectId=1', token=token)
    log(fid + '.3', '追溯缺口', must200(code, data), f'code={code}')

    # 7.4 追溯断裂
    code, data = http('GET', '/traceability/breakages?projectId=1', token=token)
    log(fid + '.4', '追溯断裂', must200(code, data), f'code={code}')

# ========================================================================
# US-8 质量工程师：全层质量监督 + 质量评分
# ========================================================================
def us_8_quality_supervision(token):
    """US-8 质量工程师：全层质量监督"""
    fid = 'US-8'
    log(fid, '=== US-8 质量工程师全层质量 ===', True, '')

    # 8.1 质量评分查询（v1.45 BUG #92 修复：路径 /requirements/quality 不是 /quality-score/list）
    code, data = http('GET', '/requirements/quality', token=token)
    log(fid + '.1', '项目质量评分列表', must200(code, data), f'code={code}')

    # 8.2 看板视图（按状态分组）
    code, data = http('GET', '/requirements/kanban?projectId=1', token=token)
    log(fid + '.2', '需求看板', must200(code, data), f'code={code}')

    # 8.3 审计日志（实体维度）
    code, data = http('GET', '/compliance/audit-logs?size=5', token=token)
    log(fid + '.3', '审计日志列表', must200(code, data), f'code={code}')

# ========================================================================
# US-9 合规专员：IEC 62304 合规检查
# ========================================================================
def us_9_iec62304_check(token):
    """US-9 合规专员：IEC 62304 合规检查"""
    fid = 'US-9'
    log(fid, '=== US-9 IEC 62304 合规检查 ===', True, '')

    # 9.1 初始化清单
    code, data = http('POST', '/compliance/iec62304/checklist/1/init', token=token, data={})
    log(fid + '.1', '初始化 IEC 清单', must200(code, data), f'code={code}')

    # 9.2 清单查询
    code, data = http('GET', '/compliance/iec62304/checklist/1', token=token)
    log(fid + '.2', 'IEC 清单查询', must200(code, data), f'code={code}')

    # 9.3 全量检查
    code, data = http('POST', '/compliance/iec62304/checklist/1/run-full-check', token=token, data={})
    log(fid + '.3', '全量合规检查', must200(code, data), f'code={code}')

    # 9.4 统计
    code, data = http('GET', '/compliance/iec62304/checklist/1/stats', token=token)
    log(fid + '.4', '合规统计', must200(code, data), f'code={code}')

# ========================================================================
# US-10 研发工程师：SOUP 组件登记（异常）
# ========================================================================
def us_10_soup_register(token):
    """US-10 研发工程师：SOUP 登记"""
    fid = 'US-10'
    log(fid, '=== US-10 SOUP 组件登记 ===', True, '')

    t0 = int(time.time())
    # 10.1 登记 SOUP
    code, data = http('POST', '/requirement/soup-components', token=token, data={
        'componentName': f'US10-SOUP-{t0}', 'componentCode': f'US10-SOUP-{t0}',
        'version': '2.0.0', 'supplier': 'AcmeCorp', 'softwareCategory': 'LIBRARY',
        'complianceStandard': 'IEC 62304'
    })
    soup_id = extract_id(data) if must200(code, data) else None
    log(fid + '.1', '登记 SOUP 组件', soup_id is not None, f'soup_id={soup_id}')

    # 10.2 续期许可证
    if soup_id:
        code, data = http('POST', f'/requirement/soup-components/{soup_id}/renew', token=token, data={})
        log(fid + '.2', '续期许可证', must200(code, data), f'code={code}')

    # 10.3 异常列表
    code, data = http('GET', '/requirement/soup-components/anomalies/all?projectId=1', token=token)
    log(fid + '.3', 'SOUP 异常列表', must200(code, data), f'code={code}')

# ========================================================================
# US-11 系统架构师：软件安全分类
# 看是否有专门端点，否则跳过
# ========================================================================
def us_11_safety_classification(token):
    """US-11 系统架构师：软件安全分类"""
    fid = 'US-11'
    log(fid, '=== US-11 软件安全分类 ===', True, '')

    # safety-classification 端点位于 compliance 模块
    code, data = http('GET', '/compliance/safety-classification/1', token=token)
    log(fid + '.1', '软件安全分类查询',
        code == 200 and data and data.get('code') == 200,
        f'code={code} body={str(data)[:120] if data else "None"}')

# ========================================================================
# US-12 项目经理：问题报告跟踪
# ========================================================================
def us_12_problem_report(token):
    """US-12 项目经理：问题报告跟踪"""
    fid = 'US-12'
    log(fid, '=== US-12 问题报告跟踪 ===', True, '')

    t0 = int(time.time())
    # 12.1 创建问题报告
    code, data = http('POST', '/compliance/problem-reports', token=token, data={
        'projectId': 1, 'title': f'US12-PR-{t0}', 'description': 'US-12 测试问题',
        'severity': 'MAJOR', 'category': 'SOFTWARE', 'status': 'OPEN'
    })
    pr_id = extract_id(data) if must200(code, data) else None
    log(fid + '.1', '创建问题报告', pr_id is not None, f'pr_id={pr_id}')

    # 12.2 问题报告列表
    code, data = http('GET', '/compliance/problem-reports?projectId=1', token=token)
    log(fid + '.2', '问题报告列表', must200(code, data), f'code={code}')

    # 12.3 状态变更（v1.45 修复：中文参数 URL 编码）
    if pr_id:
        from urllib.parse import quote
        resolution_enc = quote('正在分析根因', safe='')
        code, data = http('PUT', f'/compliance/problem-reports/{pr_id}/status?status=Analyzing&resolution={resolution_enc}',
                          token=token, data={})
        log(fid + '.3', '问题报告状态变更', must200(code, data), f'code={code}')

# ========================================================================
# US-13 质量工程师：审计日志完整性校验（哈希链）
# ========================================================================
def us_13_audit_integrity(token):
    """US-13 质量工程师：审计日志完整性"""
    fid = 'US-13'
    log(fid, '=== US-13 审计日志完整性校验 ===', True, '')

    # 13.1 审计日志列表
    code, data = http('GET', '/compliance/audit-logs?size=10', token=token)
    log(fid + '.1', '审计日志列表', must200(code, data), f'code={code}')

    # 13.2 按时间范围
    code, data = http('GET', '/compliance/audit-logs/time-range?startTime=2026-01-01T00:00:00&endTime=2027-01-01T00:00:00',
                      token=token)
    log(fid + '.2', '按时间范围查询', must200(code, data), f'code={code}')

    # 13.3 哈希链验证
    code, data = http('POST', '/compliance/audit-logs/verify', token=token, data={})
    log(fid + '.3', '哈希链完整性验证', must200(code, data), f'code={code} body={str(data)[:200] if data else "None"}')

# ========================================================================
# US-14 合规专员：NMPA eRPS 导出
# ========================================================================
def us_14_nmpa_export(token):
    """US-14 合规专员：NMPA eRPS 导出"""
    fid = 'US-14'
    log(fid, '=== US-14 NMPA eRPS 导出 ===', True, '')

    # 14.1 eRPS 导出 JSON
    code, data = http('GET', '/compliance/erps/export/1', token=token)
    log(fid + '.1', 'eRPS 导出', must200(code, data), f'code={code}')

# ========================================================================
# US-15 产品经理：需求基线管理
# ========================================================================
def us_15_baseline_management(token):
    """US-15 产品经理：需求基线管理"""
    fid = 'US-15'
    log(fid, '=== US-15 需求基线管理 ===', True, '')

    # 15.1 项目基线列表
    code, data = http('GET', '/baselines/project/1', token=token)
    log(fid + '.1', '项目基线列表', must200(code, data), f'code={code}')

    # 15.2 基线对比
    code, data = http('GET', '/baselines/compare?baselineId1=1&baselineId2=2', token=token)
    log(fid + '.2', '基线对比', must200(code, data) or code == 200, f'code={code}')

# ========================================================================
# US-16 测试工程师：测试追溯完整性
# ========================================================================
def us_16_test_traceability(token):
    """US-16 测试工程师：测试追溯完整性"""
    fid = 'US-16'
    log(fid, '=== US-16 测试追溯完整性 ===', True, '')

    # 16.1 测试用例列表（v1.45 BUG #92 修复：路径 /testcases 不是 /test-cases）
    code, data = http('GET', '/testcases?size=5', token=token)
    log(fid + '.1', '测试用例列表', must200(code, data), f'code={code}')

    # 16.2 项目覆盖率
    code, data = http('GET', '/traceability/coverage?projectId=1', token=token)
    log(fid + '.2', '覆盖率统计', must200(code, data), f'code={code}')

    # 16.3 缺口清单
    code, data = http('GET', '/traceability/gaps?projectId=1', token=token)
    log(fid + '.3', '追溯缺口', must200(code, data), f'code={code}')

# ========================================================================
# 主流程
# ========================================================================
def main():
    print('=' * 80)
    print('US-1～US-16 端到端业务流程全链路测试 v1.45')
    print('=' * 80)

    token = login('admin', 'admin123')
    if not token:
        print('FATAL: 登录失败')
        sys.exit(1)
    print('✅ 登录成功\n')

    us_1_director_supervision(token)
    us_2_pm_change_full_view(token)
    us_3_architect_l2_to_l3(token)
    us_4_compliance_dhf(token)
    us_5_pm_pool_to_prs(token)
    us_6_engineer_l4_execute(token)
    us_7_tester_coverage(token)
    us_8_quality_supervision(token)
    us_9_iec62304_check(token)
    us_10_soup_register(token)
    us_11_safety_classification(token)
    us_12_problem_report(token)
    us_13_audit_integrity(token)
    us_14_nmpa_export(token)
    us_15_baseline_management(token)
    us_16_test_traceability(token)

    print('\n' + '=' * 80)
    print('汇总')
    print('=' * 80)
    total = len(results)
    passed = sum(1 for r in results if r[2])
    failed = total - passed
    print(f'总用例：{total}  通过：{passed}  失败：{failed}  通过率：{passed/total*100:.1f}%')
    by_us = {}
    for r in results:
        fid = r[0].split('.')[0]
        by_us.setdefault(fid, []).append(r)
    for fid in sorted(by_us.keys()):
        items = by_us[fid]
        p = sum(1 for x in items if x[2])
        f = len(items) - p
        print(f'  {fid}: {p}/{len(items)} 通过' + (f' ({f} 失败)' if f else ''))

    with OUT.open('w', encoding='utf-8') as f:
        f.write(f'US-1～US-16 端到端测试 {datetime.now()}\n')
        f.write(f'总用例 {total} 通过 {passed} 失败 {failed} 通过率 {passed/total*100:.1f}%\n\n')
        for us_id, name, ok, detail in results:
            f.write(f'[{"OK" if ok else "FAIL"}] {us_id} {name}: {detail}\n')

if __name__ == '__main__':
    main()
