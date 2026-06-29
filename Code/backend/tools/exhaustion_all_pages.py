"""全量页面可达性扫描器（W30 全量测试）"""
import json
import urllib.request
import urllib.error
from urllib.parse import urlencode
from pathlib import Path

BASE = 'http://localhost:8080/api'
OUT = Path('D:/zhutao/MED_RMS_PMS/Code/backend/output')
OUT.mkdir(exist_ok=True)

def login():
    url = BASE + '/auth/login'
    data = json.dumps({'username': 'admin', 'password': 'admin123'}).encode('utf-8')
    req = urllib.request.Request(url, data=data, method='POST')
    req.add_header('Content-Type', 'application/json')
    try:
        with urllib.request.urlopen(req, timeout=10) as r:
            j = json.loads(r.read().decode('utf-8'))
            if j.get('code') == 200:
                return j['data']['token']
    except: pass
    return None

def http(method, path, token, params=None):
    url = BASE + path
    if params:
        url += '?' + urlencode(params, doseq=True)
    req = urllib.request.Request(url, method=method)
    req.add_header('Content-Type', 'application/json')
    if token:
        req.add_header('Authorization', f'Bearer {token}')
    try:
        with urllib.request.urlopen(req, timeout=10) as r:
            return r.status, json.loads(r.read().decode('utf-8'))
    except urllib.error.HTTPError as e:
        try:
            return e.code, json.loads(e.read().decode('utf-8'))
        except: return e.code, None
    except Exception as ex:
        return 0, None

def main():
    token = login()
    print('=' * 60)
    print('全量端点可达性扫描')
    print('=' * 60)

    # 全量端点列表（按模块）
    endpoints = [
        # 仪表盘
        ('GET', '/dashboard/view/requirements'),
        ('GET', '/dashboard/view/risk'),
        ('GET', '/dashboard/view/compliance'),
        ('GET', '/dashboard/view/management'),
        # 需求
        ('GET', '/requirements'),
        ('GET', '/requirements/1'),
        ('GET', '/requirements/kanban'),
        ('GET', '/requirements/pool'),
        ('GET', '/requirements/quality'),
        ('GET', '/requirements/ai-assist'),
        # 拆解
        ('GET', '/requirements/1/decompose'),
        # 测试用例
        ('GET', '/testcases'),
        # 追溯
        ('GET', '/traceability/gaps'),
        ('GET', '/traceability/coverage?projectId=1'),
        ('GET', '/traceability/import'),
        ('GET', '/trace-graph/project/1'),
        # 变更
        ('GET', '/changes/list?size=10'),
        ('GET', '/changes/pending'),
        ('GET', '/changes/1'),
        ('GET', '/changes/1/impacts'),
        ('GET', '/changes/1/timeline'),
        ('GET', '/changes/approvals'),
        # 合规
        ('GET', '/compliance/iec62304/checklist/1/stats'),
        ('GET', '/compliance/problem-reports'),
        ('GET', '/compliance/dhf'),
        ('GET', '/compliance/erps'),
        ('GET', '/compliance/regulation-impact'),
        ('GET', '/compliance/reports'),
        # 签名
        ('GET', '/esignature/signatures?signerId=1'),
        ('GET', '/esignature/entity/REQUIREMENT/1'),
        # 风险
        ('GET', '/risk/register/list'),
        ('GET', '/risk/fmea?projectId=1'),
        ('GET', '/risk/report/1'),
        # 项目
        ('GET', '/projects'),
        ('GET', '/projects/1'),
        ('GET', '/projects/1/deliverables'),
        ('GET', '/projects/1/gantt'),
        ('GET', '/projects/gantt'),
        ('GET', '/projects/ipd'),
        ('GET', '/projects/resources'),
        ('GET', '/projects/worklog'),
        # 里程碑
        ('GET', '/gantt/milestones/project/1'),
        ('GET', '/gantt/tasks/project/1'),
        ('GET', '/gantt/dependencies/project/1'),
        # IPD
        ('GET', '/project/ipd-gate/list/1'),
        # 报告
        ('GET', '/reports'),
        # 审计
        ('GET', '/compliance/audit-logs'),
        # 通知
        ('GET', '/notifications/unread/count?userId=1'),
        # 系统
        ('GET', '/system/users'),
        ('GET', '/admin/migration/jobs'),
    ]

    results = []
    health_count = {'ok': 0, 'sy0301': 0, 'sy0101': 0, 'sy_other': 0, 'network_err': 0}
    for method, path in endpoints:
        status, body = http(method, path, token)
        if not body:
            results.append({'endpoint': f'{method} {path}', 'status': status, 'issue': 'NETWORK_ERR'})
            health_count['network_err'] += 1
            continue
        code = body.get('code', 'no-code')
        if code == 200:
            health_count['ok'] += 1
            results.append({'endpoint': f'{method} {path}', 'status': status, 'code': code, 'healthy': True})
        elif code == 'SY0301':
            health_count['sy0301'] += 1
            results.append({'endpoint': f'{method} {path}', 'status': status, 'code': code, 'message': body.get('message', '')})
        elif code == 'SY0101':
            health_count['sy0101'] += 1
            results.append({'endpoint': f'{method} {path}', 'status': status, 'code': code, 'message': body.get('message', '')})
        elif isinstance(code, str) and code.startswith('SY'):
            health_count['sy_other'] += 1
            results.append({'endpoint': f'{method} {path}', 'status': status, 'code': code, 'message': body.get('message', '')})
        else:
            results.append({'endpoint': f'{method} {path}', 'status': status, 'code': code})

    print(f'总计 {len(endpoints)} 个端点')
    print(f'  健康(200): {health_count["ok"]}')
    print(f'  SY0301(资源不存在): {health_count["sy0301"]}')
    print(f'  SY0101(参数错): {health_count["sy0101"]}')
    print(f'  其他业务异常: {health_count["sy_other"]}')
    print(f'  网络错误: {health_count["network_err"]}')

    # 列出所有异常
    print()
    print('=== 异常端点详情 ===')
    for r in results:
        if not r.get('healthy') and r.get('code') != 200:
            print(f"  {r['endpoint']:60s} → {r.get('code', r.get('status'))}: {r.get('message', '')[:50]}")

    (OUT / 'all_pages_audit.json').write_text(json.dumps({
        'total': len(endpoints),
        'health': health_count,
        'results': results
    }, ensure_ascii=False, indent=2), encoding='utf-8')

if __name__ == '__main__':
    main()