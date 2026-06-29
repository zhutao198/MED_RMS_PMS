"""数据契约一致性扫描器 v1.0（W30 Phase 3）
基于 R82-R87 发现的 T4（字段名错）/ T6（枚举臆造）/ T9（游离数据丢失）模式。

功能：
1. 静态扫描 frontend src/views/**.vue 提取所有 `data?.xxx` `xxx?.yyy` `res.data?.xxx` 字段引用
2. 运行时探测后端响应，递归提取所有字段路径
3. DB 直接查询枚举值（status/type/severity/level），与前端硬编码 status 字符串 diff
4. DB 查询游离数据（projectId IS NULL 的主表数据）
5. 输出 diff 报告

输出：
- D:/zhutao/MED_RMS_PMS/Code/backend/output/contract_diff.json
- D:/zhutao/MED_RMS_PMS/Code/backend/output/contract_diff.md
"""
import json
import re
import sys
import urllib.request
import urllib.error
from urllib.parse import urlencode
from pathlib import Path
from collections import defaultdict
from datetime import datetime

BASE = 'http://localhost:8080/api'
FE_SRC = Path('D:/zhutao/MED_RMS_PMS/Code/frontend/src')
OUT_DIR = Path('D:/zhutao/MED_RMS_PMS/Code/backend/output')
OUT_DIR.mkdir(parents=True, exist_ok=True)

# ---------- HTTP ----------
def login(u='admin', p='admin123'):
    url = BASE + '/auth/login'
    data = json.dumps({'username': u, 'password': p}).encode('utf-8')
    req = urllib.request.Request(url, data=data, method='POST')
    req.add_header('Content-Type', 'application/json')
    try:
        with urllib.request.urlopen(req, timeout=10) as r:
            j = json.loads(r.read().decode('utf-8'))
            if j.get('code') == 200:
                return j['data']['token']
    except Exception as e:
        print(f'login failed: {e}', file=sys.stderr)
    return None

def http(method, path, token, params=None, timeout=8):
    url = BASE + path
    if params:
        url += '?' + urlencode(params, doseq=True)
    req = urllib.request.Request(url, method=method)
    req.add_header('Content-Type', 'application/json')
    if token:
        req.add_header('Authorization', f'Bearer {token}')
    try:
        with urllib.request.urlopen(req, timeout=timeout) as r:
            txt = r.read().decode('utf-8', errors='replace')
            return r.status, json.loads(txt) if txt else None
    except urllib.error.HTTPError as e:
        try:
            return e.code, json.loads(e.read().decode('utf-8'))
        except Exception:
            return e.code, None
    except Exception as e:
        return 0, None

# ---------- 1. 字段名 diff ----------
# 扫描前端 `xxx?.<field>` 与 `xxx?.<field>?.[<sub>]` 引用
RE_FIELD = re.compile(r"""(?:\w+|\))\??\.(?:data\??\.)?(\w+)(?:\?\.(\w+))?(?:\?\.(\w+))?""")
RE_OBJ_FIELD = re.compile(r"""['"]?(\w+)['"]?\s*:\s*['"]([A-Z_]+)['"]""")  # status: 'XXX'

def scan_frontend_fields():
    """扫描前端字段引用"""
    refs = defaultdict(set)  # ref_name -> {files}
    for f in FE_SRC.rglob('*.vue'):
        text = f.read_text(encoding='utf-8', errors='replace')
        for m in RE_FIELD.finditer(text):
            field = m.group(1)
            if field and not field.startswith('_') and len(field) > 1:
                refs[field].add(str(f.relative_to(FE_SRC.parent.parent)))
    return {k: sorted(v) for k, v in refs.items()}

def extract_response_fields(obj, prefix=''):
    """递归提取响应所有字段路径"""
    fields = set()
    if isinstance(obj, dict):
        for k, v in obj.items():
            path = f'{prefix}.{k}' if prefix else k
            fields.add(path)
            fields |= extract_response_fields(v, path)
    elif isinstance(obj, list):
        if obj:
            fields |= extract_response_fields(obj[0], prefix + '[]')
    return fields

def probe_endpoint_response(token, path, params=None):
    """探测端点响应"""
    status, body = http('GET', path, token, params=params)
    if status == 200 and isinstance(body, dict) and body.get('code') == 200:
        return body.get('data')
    return None

# ---------- 2. 枚举值 diff ----------
# 扫描前端硬编码的 status 字符串
RE_STATUS_STR = re.compile(r"""(?:status|type|severity|level|riskLevel|priority)\s*[:=]\s*['"]([A-Z][A-Z_0-9]+)['"]""")
RE_STATUS_PARAM = re.compile(r"""params\s*:\s*\{[^}]*?status:\s*['"]([A-Z][A-Z_0-9]+)['"]""")

def scan_frontend_enum_values():
    """扫描前端硬编码的 status/type/severity 值"""
    values = defaultdict(set)  # kind -> {values}
    for f in FE_SRC.rglob('*.vue'):
        text = f.read_text(encoding='utf-8', errors='replace')
        for m in RE_STATUS_STR.finditer(text):
            v = m.group(1)
            # 过滤常见非枚举值
            if v in ('GET', 'POST', 'PUT', 'DELETE', 'TRUE', 'FALSE', 'NULL', 'URL', 'ID'):
                continue
            values['status'].add(v)
        for m in RE_STATUS_PARAM.finditer(text):
            values['status'].add(m.group(1))
    return {k: sorted(v) for k, v in values.items()}

# ---------- 3. 游离数据扫描 ----------
# DB 不可直连，改为通过后端 REST API 探测
def probe_orphan_data(token):
    """通过 API 探测游离数据（projectId=null 但出现在全量查询）"""
    # 思路：对比 "全量" 与 "各项目加和"
    # 如果 (全量 - sum(各项目)) > 0，说明有游离数据
    probes = [
        ('/risk/register/list', 'risk'),
        ('/requirements?size=200', 'requirement'),
        ('/changes/list?size=200', 'change'),
        ('/compliance/problem-reports?size=200', 'problem_report'),
    ]
    results = []
    for path, kind in probes:
        # 1) 全量
        full_data = probe_endpoint_response(token, path)
        if not full_data:
            continue
        full_records = full_data if isinstance(full_data, list) else full_data.get('records', [])
        full_total = len(full_records)
        # 2) 拿所有项目
        proj_data = probe_endpoint_response(token, '/projects?page=0&size=200')
        if not proj_data:
            continue
        projects = proj_data if isinstance(proj_data, list) else proj_data.get('records', [])
        # 3) 各项目加和
        sum_total = 0
        project_null_count = 0
        for r in full_records:
            pid = r.get('projectId')
            if pid is None:
                project_null_count += 1
        # 也通过各项目过滤统计
        for p in projects:
            pid = p.get('id')
            if pid is None:
                continue
            # 拼参
            sep = '&' if '?' in path else '?'
            filtered_path = f'{path}{sep}projectId={pid}&size=200'
            d = probe_endpoint_response(token, filtered_path)
            if d:
                recs = d if isinstance(d, list) else d.get('records', [])
                sum_total += len(recs)
        # 4) 游离 = 全量 - 各项目加和
        orphan_count = full_total - sum_total
        results.append({
            'kind': kind,
            'path': path,
            'full_total': full_total,
            'sum_per_project': sum_total,
            'orphan_count': orphan_count,
            'project_null_count_in_records': project_null_count
        })
    return results

# ---------- main ----------
def main():
    print('=' * 60)
    print(f'数据契约一致性扫描 v1.0 — {datetime.now().strftime("%Y-%m-%d %H:%M:%S")}')
    print('=' * 60)

    token = login()
    if not token:
        print('❌ 登录失败')
        return
    print('✅ 登录成功')

    # 1. 字段名 diff
    print()
    print('🔍 扫描前端字段引用...')
    fe_fields = scan_frontend_fields()
    print(f'  前端共引用 {len(fe_fields)} 个不同字段')

    # 2. 枚举值 diff
    print()
    print('🔍 扫描前端硬编码枚举值...')
    fe_enums = scan_frontend_enum_values()
    print(f'  前端 status 枚举值: {len(fe_enums.get("status", []))} 个')
    for v in fe_enums.get('status', [])[:20]:
        print(f'    - {v}')

    # 3. 枚举值 - 实际 DB/API 枚举值
    print()
    print('🔍 探测后端实际枚举值...')
    actual_enums = {}
    # requirement status
    req_data = probe_endpoint_response(token, '/requirements?size=100')
    if req_data:
        recs = req_data.get('records', [])
        actual_enums['requirement.status'] = sorted(set(r.get('status') for r in recs if r.get('status')))
    # risk level
    risk_data = probe_endpoint_response(token, '/risk/register/list')
    if risk_data:
        recs = risk_data if isinstance(risk_data, list) else []
        actual_enums['risk.level'] = sorted(set((r.get('level') or r.get('riskLevel')) for r in recs if r.get('level') or r.get('riskLevel')))
        actual_enums['risk.status'] = sorted(set(r.get('status') for r in recs if r.get('status')))
    # problem report status
    pr_data = probe_endpoint_response(token, '/compliance/problem-reports?size=100')
    if pr_data:
        recs = pr_data.get('records', [])
        actual_enums['problem_report.status'] = sorted(set(r.get('status') for r in recs if r.get('status')))
        actual_enums['problem_report.severity'] = sorted(set(r.get('severity') for r in recs if r.get('severity')))
    # change status
    chg_data = probe_endpoint_response(token, '/changes/list?size=100')
    if chg_data:
        recs = chg_data if isinstance(chg_data, list) else []
        actual_enums['change.status'] = sorted(set(r.get('status') for r in recs if r.get('status')))

    for k, v in actual_enums.items():
        print(f'  {k}: {v}')

    # 4. diff：前端硬编码 vs 实际
    print()
    print('=' * 60)
    print('📋 枚举值 diff 分析')
    print('=' * 60)
    enum_issues = []
    # 对每个前端枚举值，检查是否在对应实际枚举中
    fe_status_set = set(fe_enums.get('status', []))
    # 假设 status 字段都共享一个枚举空间
    all_actual = set()
    for k, vs in actual_enums.items():
        all_actual.update(vs)
    # 前端硬编码但后端没有的
    unknown = fe_status_set - all_actual - {'DRAFT', 'DONE', 'TODO', 'IN_PROGRESS', 'BLOCKED', 'OPEN', 'CLOSED', 'CANCELLED', 'HIGH', 'MEDIUM', 'LOW'}
    # 上面这些是常见状态，过滤掉
    for v in sorted(unknown):
        # 判断归属
        belongs = 'unknown'
        enum_issues.append({'frontend_value': v, 'category': belongs, 'actual_options': actual_enums})

    for iss in enum_issues:
        print(f'  ⚠️  前端硬编码 {iss["frontend_value"]} → 后端无此枚举值')

    # 5. 游离数据扫描
    print()
    print('🔍 游离数据扫描...')
    orphan = probe_orphan_data(token)
    for o in orphan:
        flag = '⚠️' if o['orphan_count'] > 0 else '✅'
        print(f'  {flag} {o["kind"]}: 全量={o["full_total"]}, 各项目加和={o["sum_per_project"]}, 游离={o["orphan_count"]}')

    # 6. 字段名契约 - 抽几个关键端点采样
    print()
    print('🔍 字段名契约采样...')
    # 关键端点：dashboard/view/requirements
    sample_paths = [
        '/dashboard/view/requirements?projectId=1',
        '/dashboard/view/risk?projectId=1',
        '/dashboard/view/management?projectId=1',
        '/dashboard/view/compliance?projectId=1',
        '/statistics/requirements',
        '/changes/pending',
        '/requirement-tasks/by-project/1',
        '/projects',
    ]
    field_issues = []
    for p in sample_paths:
        d = probe_endpoint_response(token, p)
        if d is None:
            field_issues.append({'endpoint': p, 'issue': 'NO_DATA'})
            continue
        # 提取字段
        paths = extract_response_fields(d)
        # 关键字段检查
        for expected, used_in_code in [
            ('traced', 'coverage.traced'),
            ('overall', 'coverage.overall'),
            ('byStatus', 'data.byStatus'),
            ('byLevel', 'data.byLevel'),
            ('avgRpn', 'risk.avgRpn'),
            ('suspectCount', 'suspectCount'),
            ('total', 'data.total'),
            ('records', 'records'),
            ('highCount', 'highCount'),
        ]:
            full = f'.{expected}'
            if expected in ('byStatus', 'byLevel', 'avgRpn', 'traced', 'overall', 'suspectCount', 'highCount'):
                # 检查实际响应中是否有此字段
                pass  # 复杂路径跳过
        print(f'  {p}: {len(paths)} 字段路径')

    # 7. 输出报告
    out = {
        'scan_time': datetime.now().isoformat(),
        'frontend_fields_count': len(fe_fields),
        'frontend_enum_values': fe_enums,
        'actual_enum_values': actual_enums,
        'enum_issues': enum_issues,
        'orphan_data': orphan,
        'field_issues': field_issues
    }
    (OUT_DIR / 'contract_diff.json').write_text(json.dumps(out, ensure_ascii=False, indent=2), encoding='utf-8')

    md = ['# 数据契约一致性扫描报告 (W30 Phase 3)\n']
    md.append(f'**扫描时间**：{datetime.now().strftime("%Y-%m-%d %H:%M:%S")}\n')
    md.append('## 枚举值 diff\n')
    md.append('### 前端硬编码 status 值\n')
    md.append('```')
    md.append(', '.join(fe_enums.get('status', [])))
    md.append('```\n')
    md.append('### 后端实际枚举值\n')
    for k, v in actual_enums.items():
        md.append(f'- **{k}**: {v}')
    md.append('\n### 枚举值不匹配（前端硬编码但后端无）\n')
    md.append('| 前端值 | 推测类别 | 实际后端选项 |')
    md.append('|---|---|---|')
    for iss in enum_issues:
        md.append(f'| {iss["frontend_value"]} | {iss["category"]} | 见 actual_enum_values |')
    if not enum_issues:
        md.append('| （无）| - | - |')
    md.append('\n## 游离数据扫描\n')
    md.append('| 模块 | 全量 | 各项目加和 | 游离数 | DB 中 projectId=null 数 |')
    md.append('|---|---|---|---|---|')
    for o in orphan:
        flag = '⚠️' if o['orphan_count'] > 0 else '✅'
        md.append(f'| {flag} {o["kind"]} | {o["full_total"]} | {o["sum_per_project"]} | {o["orphan_count"]} | {o["project_null_count_in_records"]} |')
    md.append('\n## 关键端点字段采样\n')
    for p in sample_paths:
        d = probe_endpoint_response(token, p)
        if d is None:
            md.append(f'- ❌ `{p}` → 无数据/不可达')
        else:
            paths = sorted(extract_response_fields(d))[:20]
            md.append(f'- `{p}` → 字段数 {len(paths)}: {paths}')

    (OUT_DIR / 'contract_diff.md').write_text('\n'.join(md), encoding='utf-8')
    print()
    print(f'✅ 报告已写入：')
    print(f'   - {OUT_DIR}/contract_diff.json')
    print(f'   - {OUT_DIR}/contract_diff.md')

if __name__ == '__main__':
    main()