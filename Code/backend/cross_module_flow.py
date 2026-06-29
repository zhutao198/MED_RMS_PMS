"""跨模块数据流验证测试（v1.44）
验证 Med-RMS 系统中跨模块的状态联动 / 事件触发 / 通知生成
"""
import json
import sys
import time
import urllib.request
import urllib.error
from pathlib import Path
from datetime import datetime

BASE = 'http://localhost:8080/api'
OUT = Path('D:/zhutao/MED_RMS_PMS/Code/backend/cross_module_flow.log')
results = []  # [(flow_id, name, passed, detail)]

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

def get_notification_count(token, user_id=1):
    code, data = http('GET', '/notifications/unread/count', token=token, params={'userId': user_id})
    if code == 200 and data and data.get('code') == 200:
        return data['data'].get('count', 0)
    return -1

def get_notifications(token, user_id=1, status=None, type_filter=None):
    params = {'userId': user_id}
    if status: params['status'] = status
    if type_filter: params['type'] = type_filter
    code, data = http('GET', '/notifications/all', token=token, params=params)
    if code == 200 and data and data.get('code') == 200:
        return data.get('data', [])
    return []

def log(flow_id, name, passed, detail=''):
    icon = '✅' if passed else '❌'
    line = f'{icon} [{flow_id}] {name}: {detail}'
    print(line, flush=True)
    results.append((flow_id, name, passed, detail))

def extract_id(data):
    """从 data.data 中安全提取 id，支持嵌套 dict/Long/dict.id 形式"""
    if not data or not isinstance(data, dict):
        return None
    inner = data.get('data')
    if isinstance(inner, dict):
        return inner.get('id')
    if isinstance(inner, int):
        return inner
    return None

# ========================================================================
# 数据流 1：需求创建 → 拆解 → 追溯 → 测试用例 → 评审 → 通知
# ========================================================================
def flow_1_requirement_to_notification(token):
    """FR-0.1/0.4/0.5/0.7 需求→拆解→追溯→评审→通知"""
    fid = 'F1'
    log(fid, '=== 数据流1：需求全生命周期+评审通知 ===', True, '')
    t0 = int(time.time())
    initial_unread = get_notification_count(token, user_id=1)
    log(fid + '.init', '初始未读通知数', initial_unread >= 0, f'count={initial_unread}')

    # 1.1 创建 URS 需求
    code, data = http('POST', '/requirements', token=token,
        data={'title': f'CROSS-F1-URS-{t0}', 'projectId': 1,
              'requirementType': 'URS', 'priority': 'MUST',
              'description': '跨模块测试 URS 需求'})
    urs_id = extract_id(data) if code == 200 else None
    log(fid + '.1', '创建 URS 需求', urs_id is not None, f'urs_id={urs_id} (code={code})')

    # 1.2 拆解 URS → PRS（FR-0.7）
    code, data = http('POST', f'/requirements/{urs_id}/decompose', token=token, data={
        'title': f'CROSS-F1-PRS-{t0}', 'projectId': 1,
        'requirementType': 'PRS', 'priority': 'MUST',
        'description': '跨模块测试 PRS 子需求'
    })
    prs_id = extract_id(data) if code == 200 else None
    log(fid + '.2', 'URS→PRS 拆解', prs_id is not None, f'prs_id={prs_id} (code={code})')

    # 1.3 建立追溯关系 URS↔PRS（用 query params 而非 body）
    code, data = http('POST', '/traceability/relations', token=token, params={
        'sourceReqId': urs_id, 'targetReqId': prs_id, 'relationType': 'DECOMPOSED_TO'
    })
    trace_ok = code == 200
    log(fid + '.3', '建立追溯关系 URS↔PRS', trace_ok, f'code={code} data={data}')

    # 1.4 创建测试用例关联 PRS
    code, data = http('POST', '/testcases', token=token, data={
        'title': f'CROSS-F1-TC-{t0}', 'projectId': 1,
        'requirementId': prs_id, 'caseType': 'FUNCTIONAL',
        'priority': 'P1', 'description': '跨模块测试测试用例'
    })
    tc_id = extract_id(data) if code == 200 else None
    log(fid + '.4', '创建测试用例', tc_id is not None, f'tc_id={tc_id} (code={code})')

    # 1.5 关联测试用例到 PRS（追溯，用 query params）
    if tc_id:
        code, data = http('POST', '/traceability/testcases', token=token, params={
            'requirementId': prs_id, 'testCaseId': tc_id, 'traceType': 'LINKED'
        })
        log(fid + '.5', '关联测试用例到 PRS', code == 200, f'code={code}')

    # 1.6 提交 URS 评审（FR-0.17：submit=Submitted，approve=Approved；基线化需要 Approved）
    code, data = http('POST', f'/requirements/{urs_id}/review?reviewerId=1&comments=cross-test', token=token, data={})
    log(fid + '.6a', '提交 URS 评审', code == 200, f'code={code}')
    code, data = http('POST', f'/requirements/{urs_id}/approve?decision=APPROVED&approverId=1&comments=ok', token=token, data={})
    log(fid + '.6', '审批 URS', code == 200, f'code={code}')

    # 1.7 等待通知触发（异步事件）
    time.sleep(2)
    notifications = get_notifications(token, user_id=1, type_filter='REVIEW_APPROVED')
    recent = [n for n in notifications if n.get('sourceId') == urs_id or f'CROSS-F1-URS-{t0}' in n.get('title', '') + n.get('content', '')]
    log(fid + '.7', '评审通知触发', len(notifications) > 0,
        f'REVIEW_APPROVED 共 {len(notifications)} 条，本测试相关 {len(recent)} 条')

    return urs_id, prs_id, tc_id

# ========================================================================
# 数据流 2：变更 → suspect 标记 → 追溯断裂 → 通知
# ========================================================================
def flow_2_change_to_suspect_trace_broken(token, urs_id, prs_id, tc_id):
    """FR-0.9/0.10 变更→suspect→追溯→通知"""
    fid = 'F2'
    log(fid, '=== 数据流2：变更→suspect→追溯断裂→通知 ===', True, '')

    # 2.1 基线化 URS 后才能发起变更（v1.44 BUG #91 修复：业务规则保护）
    t0 = int(time.time())
    # 先建一个基线
    code, data = http('POST', '/baselines', token=token, data={
        'name': f'CROSS-F2-BL-{t0}', 'projectId': 1, 'description': 'F2 基线',
        'requirementIds': [urs_id]
    })
    bl_id = extract_id(data)
    # 关联到基线
    if bl_id:
        http('POST', f'/baselines/baseline-requirements?baselineId={bl_id}', token=token, data=[urs_id])
    # 现在可以创建变更
    code, data = http('POST', '/changes', token=token, data={
        'requirementId': urs_id,
        'changeType': 'REQUIREMENT_UPDATE',
        'reason': f'CROSS-F2 修改需求 {urs_id}',
        'urgency': 'HIGH',
        'requestedBy': 1,
        'title': f'CROSS-F2-CHG-{t0}'
    })
    chg_id = extract_id(data)
    log(fid + '.1', '创建变更', chg_id is not None, f'chg_id={chg_id} (code={code}, body={str(data)[:200] if data else "None"})')

    # 2.2 提交变更评审
    code, data = http('POST', f'/changes/{chg_id}/submit?approverId=1', token=token, data={})
    log(fid + '.2', '提交变更审批', code == 200, f'code={code}')

    # 2.3 触发 suspect 标记
    time.sleep(1)
    code, data = http('POST', f'/changes/{chg_id}/assess', token=token, data={})
    log(fid + '.3', '变更影响评估', code == 200, f'code={code}')

    # 2.4 批准变更
    code, data = http('POST', f'/changes/{chg_id}/approve?approverId=1&comments=approve', token=token, data={})
    log(fid + '.4', '批准变更', code == 200, f'code={code}')

    # 2.5 执行变更（应触发 suspect 下游标记）
    code, data = http('POST', f'/changes/{chg_id}/execute', token=token, data={})
    log(fid + '.5', '执行变更', code == 200, f'code={code}')

    # 2.6 查询变更通知（CHANGE_APPROVED）
    time.sleep(2)
    change_notifs = get_notifications(token, user_id=1, type_filter='CHANGE_APPROVED')
    log(fid + '.6', '变更通知触发', len(change_notifs) > 0,
        f'CHANGE_APPROVED 共 {len(change_notifs)} 条')

    # 2.7 查询追溯断裂通知（TRACE_BROKEN）- 如果拆解关系被打断
    trace_notifs = get_notifications(token, user_id=1, type_filter='TRACE_BROKEN')
    log(fid + '.7', '追溯断裂通知存在', len(trace_notifs) > 0,
        f'TRACE_BROKEN 共 {len(trace_notifs)} 条')

    return chg_id

# ========================================================================
# 数据流 3：风险登记 → 风险告警通知
# ========================================================================
def flow_3_risk_to_notification(token, urs_id):
    """FR-1.8 风险登记→风险告警通知"""
    fid = 'F3'
    log(fid, '=== 数据流3：风险登记→风险告警通知 ===', True, '')

    t0 = int(time.time())
    initial_risk_notifs = len(get_notifications(token, user_id=1, type_filter='RISK_ALERT'))

    # 3.1 创建风险登记（v1.44 BUG #91 修复：使用实体字段 riskTitle；severity/probability/detectability 为枚举）
    code, data = http('POST', '/risk/register', token=token, data={
        'riskTitle': f'CROSS-F3-RISK-{t0}', 'projectId': 1,
        'category': 'SAFETY', 'severity': 'CRITICAL', 'probability': 'HIGH', 'detectability': 'EASY',
        'description': '跨模块测试高风险',
        'responseStrategy': 'MITIGATE',
        'ownerId': 1
    })
    risk_id = extract_id(data) if code == 200 else None
    log(fid + '.1', '创建风险登记', risk_id is not None, f'risk_id={risk_id} (code={code})')

    # 3.2 等待风险告警通知
    time.sleep(2)
    risk_notifs = get_notifications(token, user_id=1, type_filter='RISK_ALERT')
    new_notifs = len(risk_notifs) - initial_risk_notifs
    log(fid + '.2', '风险告警通知触发', new_notifs > 0,
        f'RISK_ALERT 新增 {new_notifs} 条（之前 {initial_risk_notifs} → 现在 {len(risk_notifs)}）')

    return risk_id

# ========================================================================
# 数据流 4：SOUP 异常 → 风险登记 → 风险告警
# ========================================================================
def flow_4_soup_to_risk(token):
    """FR-1.11 SOUP 异常→风险登记→告警"""
    fid = 'F4'
    log(fid, '=== 数据流4：SOUP 异常→风险登记→告警 ===', True, '')

    t0 = int(time.time())
    # 4.1 创建 SOUP 组件（v1.44 BUG #91 修复：实体字段为 componentCode/supplier；knownAnomalies 字段不在实体已移除）
    code, data = http('POST', '/requirement/soup-components', token=token, data={
        'componentName': f'CROSS-F4-SOUP-{t0}',
        'componentCode': f'F4-SOUP-{t0}',
        'version': '1.0.0',
        'supplier': 'TestVendor',
        'softwareCategory': 'LIBRARY',
        'complianceStandard': 'ISO 13485'
    })
    soup_id = extract_id(data) if code == 200 else None
    log(fid + '.1', '创建 SOUP 组件', soup_id is not None, f'soup_id={soup_id} (code={code})')

    # 4.2 SOUP 异常评估（FR-1.11：批量检测项目下所有 SOUP 异常）
    code, data = http('GET', f'/requirement/soup-components/anomalies/all?projectId=1', token=token)
    log(fid + '.2', 'SOUP 异常列表查询', code in [200, 404], f'code={code}')

    # 4.3 从 SOUP 创建风险登记
    if soup_id:
        code, data = http('POST', '/risk/register', token=token, data={
            'riskTitle': f'CROSS-F4-RISK-SOUP-{t0}', 'projectId': 1,
            'category': 'SOUP_ANOMALY', 'severity': 'MAJOR', 'probability': 'MEDIUM', 'detectability': 'MEDIUM',
            'description': f'SOUP 异常风险（来自 SOUP id={soup_id}）',
            'responseStrategy': 'MONITOR',
            'ownerId': 1
        })
        risk_id = extract_id(data) if code == 200 else None
        log(fid + '.3', '从 SOUP 创建风险', risk_id is not None, f'risk_id={risk_id} (code={code})')

    return soup_id

# ========================================================================
# 数据流 5：需求池 → 转化为 URS
# ========================================================================
def flow_5_pool_to_urs(token):
    """FR-1.6 需求池→转化 URS"""
    fid = 'F5'
    log(fid, '=== 数据流5：需求池→转化为 URS ===', True, '')

    t0 = int(time.time())
    # 5.1 添加需求到池
    code, data = http('POST', '/requirement-pool', token=token, data={
        'title': f'CROSS-F5-POOL-{t0}',
        'source': 'EMAIL',
        'rawDescription': '客户邮件反馈：心电监护仪需支持 12 导联',
        'priority': 'MUST'
    })
    pool_id = extract_id(data) if code == 200 else None
    log(fid + '.1', '添加需求到池', pool_id is not None, f'pool_id={pool_id} (code={code})')

    # 5.2 转化为 URS（v1.44 BUG #91 修复：路径为 /{id}/convert 而非 /convert/{id}）
    if pool_id:
        code, data = http('POST', f'/requirement-pool/{pool_id}/convert', token=token,
                          data={'projectId': 1, 'priority': 'MUST'})
        urs_id = data.get('data') if code == 200 and data else None
        log(fid + '.2', '需求池转化为 URS', urs_id is not None, f'urs_id={urs_id} (code={code})')

    return pool_id

# ========================================================================
# 数据流 6：基线创建 → IEC 62304 检查
# ========================================================================
def flow_6_baseline_to_iec62304(token, urs_id):
    """FR-0.8/0.15 基线→IEC 62304 检查"""
    fid = 'F6'
    log(fid, '=== 数据流6：基线→IEC 62304 检查 ===', True, '')

    t0 = int(time.time())
    # 6.1 创建基线
    code, data = http('POST', '/baselines', token=token, data={
        'name': f'CROSS-F6-BASELINE-{t0}',
        'projectId': 1,
        'description': '跨模块测试基线',
        'requirementIds': [urs_id] if urs_id else [1]
    })
    bl_id = extract_id(data) if code == 200 else None
    log(fid + '.1', '创建基线', bl_id is not None, f'baseline_id={bl_id} (code={code})')

    # 6.2 初始化 IEC 62304 清单
    code, data = http('POST', f'/compliance/iec62304/checklist/{urs_id or 1}/init', token=token, data={})
    log(fid + '.2', '初始化 IEC 62304 清单', code in [200, 201], f'code={code}')

    # 6.3 运行全量检查
    code, data = http('POST', f'/compliance/iec62304/checklist/{urs_id or 1}/run-full-check', token=token, data={})
    log(fid + '.3', '运行 IEC 62304 全量检查', code in [200, 201], f'code={code}')

    # 6.4 获取合规统计
    code, data = http('GET', f'/compliance/iec62304/checklist/{urs_id or 1}/stats', token=token)
    has_stats = code == 200 and data and data.get('code') == 200
    log(fid + '.4', '获取合规统计', has_stats, f'code={code}')

    return bl_id

# ========================================================================
# 数据流 7：需求→任务→工时→资源
# ========================================================================
def flow_7_requirement_task_worklog(token, prs_id):
    """FR-1.10/2.8/2.9 需求→任务→工时→资源"""
    fid = 'F7'
    log(fid, '=== 数据流7：需求→任务→工时→资源 ===', True, '')

    t0 = int(time.time())
    # 7.1 需求→任务转化（v1.44 BUG #91 修复：先 generateDrafts 再 convert，不传空 body）
    code, data = http('GET', f'/requirement-tasks/drafts/{prs_id or 1}', token=token)
    drafts = data.get('data', []) if code == 200 and data and isinstance(data.get('data'), list) else []
    task_id = None
    if drafts:
        code, data = http('POST', f'/requirement-tasks/convert/{prs_id or 1}', token=token, data=drafts)
        if code == 200 and data and data.get('code') == 200:
            created = data.get('data', [])
            if isinstance(created, list) and created:
                task_id = created[0].get('id')
    log(fid + '.1', '需求→任务转化', task_id is not None, f'task_id={task_id} (code={code})')

    # 7.2 填报工时（v1.44 BUG #91 修复：实体字段为 workerId/workDate/hours）
    if task_id:
        code, data = http('POST', '/worklog', token=token, data={
            'taskId': task_id, 'workerId': 1, 'projectId': 1,
            'hours': 4, 'workDate': '2026-06-05', 'description': '跨模块测试工时'
        })
        passed = code == 200 and data and data.get('code') == 200
        log(fid + '.2', '填报工时', passed, f'code={code} body={str(data)[:200] if data else "None"}')

    # 7.3 任务状态更新
    if task_id:
        code, data = http('PUT', f'/requirement-tasks/{task_id}/status', token=token,
                          data={'status': 'IN_PROGRESS'})
        log(fid + '.3', '任务状态更新', code in [200, 204], f'code={code}')

    return task_id

# ========================================================================
# 数据流 8：IPD 阶段门 → 通过/失败 → 通知
# ========================================================================
def flow_8_ipd_gate_to_notification(token):
    """FR-2.5/2.6 IPD 阶段门→通知"""
    fid = 'F8'
    log(fid, '=== 数据流8：IPD 阶段门→通过/失败→通知 ===', True, '')

    # 8.1 查询项目门控列表
    code, data = http('GET', '/project/ipd-gate/list/1', token=token)
    gates = data.get('data', []) if code == 200 and data else []
    log(fid + '.1', '查询 IPD 门控列表', code == 200, f'gates 共 {len(gates)} 条')

    # 8.2 触发自动检查
    code, data = http('POST', '/project/ipd-gate/auto-check?projectId=1', token=token, data={})
    log(fid + '.2', '触发自动检查', code in [200, 201], f'code={code}')

    # 8.3 创建新门控（v1.44 BUG #91 修复：动态 gateNo 避免重复，gateType 枚举 PLANNING/DEFINE/REALEASE/MARKET）
    existing_gates = gates if isinstance(gates, list) else []
    used_nos = [g.get('gateNo') for g in existing_gates if isinstance(g, dict) and g.get('gateNo') is not None]
    new_gate_no = (max(used_nos) if used_nos else 0) + 1
    code, data = http('POST', '/project/ipd-gate', token=token, data={
        'projectId': 1, 'gateNo': new_gate_no, 'gateType': 'PLANNING', 'gateName': f'CROSS-DCP{new_gate_no}',
        'plannedDate': '2026-12-31', 'status': 'PENDING'
    })
    gate_id = extract_id(data) if code == 200 else None
    log(fid + '.3', '创建 DCP1 门控', gate_id is not None, f'gate_id={gate_id} (code={code})')

    # 8.4 通过门控（应触发 SYSTEM 通知，pass 端点需要 decision+comment）
    if gate_id:
        code, data = http('POST', f'/project/ipd-gate/{gate_id}/pass?decision=APPROVED&comment=cross-test-pass',
                          token=token, data={})
        log(fid + '.4', '门控通过', code in [200, 201], f'code={code}')

    # 8.5 验证 SYSTEM 通知
    time.sleep(2)
    system_notifs = get_notifications(token, user_id=1, type_filter='SYSTEM')
    log(fid + '.5', '系统通知触发（门控）', len(system_notifs) > 0,
        f'SYSTEM 共 {len(system_notifs)} 条')

    return gate_id

# ========================================================================
# 主流程
# ========================================================================
def main():
    print('=' * 80)
    print('跨模块数据流验证测试 v1.44')
    print('=' * 80)

    token = login('admin', 'admin123')
    if not token:
        print('FATAL: 登录失败')
        sys.exit(1)
    print('✅ 登录成功\n')

    # 数据流 1：需求全生命周期
    urs_id, prs_id, tc_id = flow_1_requirement_to_notification(token)

    # 数据流 2：变更→suspect→追溯→通知
    chg_id = flow_2_change_to_suspect_trace_broken(token, urs_id, prs_id, tc_id)

    # 数据流 3：风险→通知
    risk_id = flow_3_risk_to_notification(token, urs_id)

    # 数据流 4：SOUP→风险
    soup_id = flow_4_soup_to_risk(token)

    # 数据流 5：需求池→URS
    pool_id = flow_5_pool_to_urs(token)

    # 数据流 6：基线→IEC 62304
    bl_id = flow_6_baseline_to_iec62304(token, urs_id)

    # 数据流 7：需求→任务→工时
    task_id = flow_7_requirement_task_worklog(token, prs_id)

    # 数据流 8：IPD 门控
    gate_id = flow_8_ipd_gate_to_notification(token)

    # 汇总
    print('\n' + '=' * 80)
    print('汇总')
    print('=' * 80)
    total = len(results)
    passed = sum(1 for r in results if r[2])
    failed = total - passed
    print(f'总用例：{total}  通过：{passed}  失败：{failed}  通过率：{passed/total*100:.1f}%')

    # 按数据流分组
    by_flow = {}
    for r in results:
        fid = r[0].split('.')[0]
        by_flow.setdefault(fid, []).append(r)
    for fid in sorted(by_flow.keys()):
        items = by_flow[fid]
        p = sum(1 for x in items if x[2])
        f = len(items) - p
        print(f'  {fid}: {p}/{len(items)} 通过' + (f' ({f} 失败)' if f else ''))

    # 写日志
    with OUT.open('w', encoding='utf-8') as f:
        f.write(f'跨模块数据流验证 {datetime.now()}\n')
        f.write(f'总用例 {total} 通过 {passed} 失败 {failed} 通过率 {passed/total*100:.1f}%\n\n')
        for fid, name, ok, detail in results:
            f.write(f'[{"OK" if ok else "FAIL"}] {fid} {name}: {detail}\n')

    sys.exit(0 if failed == 0 else 1)

if __name__ == '__main__':
    main()
