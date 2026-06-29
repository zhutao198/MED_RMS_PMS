"""端点性能扫描器 - 找所有慢 GET 端点（R93 同类问题）"""
import urllib.request
import urllib.error
import json
import time
from pathlib import Path

BASE = 'http://localhost:8080/api'
OUT = Path('D:/zhutao/MED_RMS_PMS/Code/backend/output')

def login():
    url = BASE + '/auth/login'
    data = json.dumps({'username': 'admin', 'password': 'admin123'}).encode('utf-8')
    req = urllib.request.Request(url, data=data, method='POST')
    req.add_header('Content-Type', 'application/json')
    with urllib.request.urlopen(req, timeout=10) as r:
        j = json.loads(r.read().decode('utf-8'))
        return j['data']['token']

def http(method, path, token, params=None):
    url = BASE + path
    if params:
        from urllib.parse import urlencode
        url += '?' + urlencode(params, doseq=True)
    req = urllib.request.Request(url, method=method)
    if token:
        req.add_header('Authorization', f'Bearer {token}')
    start = time.time()
    try:
        with urllib.request.urlopen(req, timeout=30) as r:
            txt = r.read().decode('utf-8', errors='replace')
            elapsed = (time.time() - start) * 1000
            return r.status, elapsed, len(txt), txt
    except urllib.error.HTTPError as e:
        elapsed = (time.time() - start) * 1000
        return e.code, elapsed, 0, str(e)
    except Exception as e:
        return 0, (time.time() - start) * 1000, 0, str(e)

def main():
    token = login()
    print(f'{"端点":50s} {"状态":6s} {"耗时(ms)":10s} {"大小":10s}')
    print('=' * 90)

    endpoints = [
        # 需求模块
        ('/requirements?size=50'),
        ('/requirements?size=100'),
        ('/requirements/kanban'),
        ('/requirement-pool'),
        ('/requirement-tasks/by-project/1'),
        # 追溯模块（R93 已优化）
        ('/traceability/matrix?projectId=1'),
        ('/traceability/gaps?projectId=1'),
        ('/traceability/coverage?projectId=1'),
        ('/trace-links?projectId=1'),
        # 测试用例
        ('/testcases?size=50'),
        # 风险模块
        ('/risk/register/list'),
        ('/risk/fmea?projectId=1'),
        # 变更模块
        ('/changes/list?size=100'),
        ('/changes/pending'),
        ('/changes/approvals'),
        # 合规模块
        ('/compliance/iec62304/checklist/1/stats'),
        ('/compliance/problem-reports?size=50'),
        ('/compliance/audit-logs?size=50'),
        # 项目模块
        ('/projects?size=50'),
        ('/project/member/list/1'),
        ('/project/ipd-gate/list/1'),
        # 通知
        ('/notifications/unread/count?userId=1'),
        # 用户/系统
        ('/system/users?size=50'),
        ('/system/roles'),
        # 报告
        ('/reports?size=20'),
        # 审计
        ('/audit-logs?size=20'),
        # 看板
        ('/risk/risk-distribution?projectId=1'),
    ]

    results = []
    for path in endpoints:
        status, elapsed, size, _ = http('GET', path, token)
        results.append({'path': path, 'status': status, 'elapsed_ms': round(elapsed), 'size_bytes': size})
        flag = '🔴' if elapsed > 300 else ('🟡' if elapsed > 100 else '✅')
        print(f'{flag} {path:48s} {status:6d} {elapsed:10.0f} {size:10d}')

    # 汇总
    print('=' * 90)
    slow = [r for r in results if r['elapsed_ms'] > 300]
    print(f'\n🔴 慢端点 (>300ms): {len(slow)} 个')
    for r in slow:
        print(f"   {r['path']}: {r['elapsed_ms']}ms ({r['size_bytes']} bytes)")

    OUT.mkdir(parents=True, exist_ok=True)
    (OUT / 'perf_audit.json').write_text(json.dumps({'scan_time': time.strftime('%Y-%m-%d %H:%M:%S'), 'results': results, 'slow': slow}, ensure_ascii=False, indent=2))

if __name__ == '__main__':
    main()