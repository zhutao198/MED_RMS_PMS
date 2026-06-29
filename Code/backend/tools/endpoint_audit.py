"""端点契约扫描器 v1.0（W30 Phase 1）
基于 R82-R86 发现的 T1/T2/T3/T10 bug 模式。

功能：
1. 静态扫描 frontend src/views/**.vue + api/**.ts 中所有 request.{get,post,put,delete}(...) 调用
2. 静态扫描 backend *Controller.java 中所有 @RequestMapping/@GetMapping/@PostMapping
3. 对每个前端调用路径 + 默认方法探测后端：
   - HTTP 状态
   - 响应 code 字段
   - 业务异常包在 HTTP 200 里的情况
4. 输出 diff：前端有但后端无 / 端点存在但方法错 / HTTP 200 但 SY01xx

输出：
- D:/zhutao/MED_RMS_PMS/Code/backend/output/endpoint_audit.json
- D:/zhutao/MED_RMS_PMS/Code/backend/output/endpoint_audit.md
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
BE_SRC = Path('D:/zhutao/MED_RMS_PMS/Code/backend')
OUT_DIR = Path('D:/zhutao/MED_RMS_PMS/Code/backend/output')
OUT_DIR.mkdir(parents=True, exist_ok=True)

# ---------- HTTP 探测 ----------
def login(u='admin', p='admin123'):
    url = BASE + '/auth/login'
    data = json.dumps({'username': u, 'password': p}).encode('utf-8')
    req = urllib.request.Request(url, data=data, method='POST')
    req.add_header('Content-Type', 'application/json')
    try:
        with urllib.request.urlopen(req, timeout=10) as r:
            txt = r.read().decode('utf-8')
            j = json.loads(txt)
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
            try:
                j = json.loads(txt) if txt else None
            except json.JSONDecodeError:
                return r.status, {'_raw': True}, txt[:200]
            return r.status, j, txt[:200]
    except urllib.error.HTTPError as e:
        try:
            txt = e.read().decode('utf-8')
            return e.code, json.loads(txt) if txt else None, txt[:200]
        except Exception:
            return e.code, None, None
    except Exception as e:
        return 0, None, str(e)

# ---------- 静态扫描 ----------
RE_REQ = re.compile(r"""request\.(get|post|put|delete)\s*\(\s*['"`]([^'"`]+)['"`]""", re.IGNORECASE)
RE_AXIOS = re.compile(r"""axios\.(get|post|put|delete)\s*\(\s*['"`]([^'"`]+)['"`]""", re.IGNORECASE)
# 后端 controller
RE_REQMAP = re.compile(r'@RequestMapping\s*\(\s*["\']([^"\']+)["\']')
RE_GETMAP = re.compile(r'@GetMapping\s*(?:\(\s*["\']([^"\']*)["\']\s*\))?')
RE_POSTMAP = re.compile(r'@PostMapping\s*(?:\(\s*["\']([^"\']*)["\']\s*\))?')
RE_PUTMAP = re.compile(r'@PutMapping\s*(?:\(\s*["\']([^"\']*)["\']\s*\))?')
RE_DELETEMAP = re.compile(r'@DeleteMapping\s*(?:\(\s*["\']([^"\']*)["\']\s*\))?')

def scan_frontend():
    """扫描前端所有 request 调用"""
    calls = []
    for f in FE_SRC.rglob('*.vue'):
        text = f.read_text(encoding='utf-8', errors='replace')
        for m in RE_REQ.finditer(text):
            calls.append({'method': m.group(1).upper(), 'path': m.group(2), 'file': str(f.relative_to(FE_SRC.parent.parent))})
        for m in RE_AXIOS.finditer(text):
            calls.append({'method': m.group(1).upper(), 'path': m.group(2), 'file': str(f.relative_to(FE_SRC.parent.parent))})
    for f in FE_SRC.rglob('*.ts'):
        text = f.read_text(encoding='utf-8', errors='replace')
        for m in RE_REQ.finditer(text):
            calls.append({'method': m.group(1).upper(), 'path': m.group(2), 'file': str(f.relative_to(FE_SRC.parent.parent))})
    # 去重
    unique = {}
    for c in calls:
        # 路径里可能含模板 ${xxx} 替换为 {id}
        norm = re.sub(r'\$\{[^}]+\}', '{id}', c['path'])
        key = f"{c['method']} {norm}"
        if key not in unique:
            unique[key] = {'method': c['method'], 'path': norm, 'files': []}
        unique[key]['files'].append(c['file'])
    return list(unique.values())

def scan_backend():
    """扫描后端所有 endpoint"""
    endpoints = []
    for f in BE_SRC.rglob('*Controller.java'):
        text = f.read_text(encoding='utf-8', errors='replace')
        # 找类级别 @RequestMapping
        cls_map = ''
        for m in RE_REQMAP.finditer(text):
            cls_map = m.group(1)
            break
        # 按行处理方法
        for line_no, line in enumerate(text.splitlines(), 1):
            line_strip = line.strip()
            method = None
            sub_path = None
            if line_strip.startswith('@GetMapping'):
                method = 'GET'
                m = RE_GETMAP.search(line_strip)
                sub_path = m.group(1) if (m and m.group(1)) else ''
            elif line_strip.startswith('@PostMapping'):
                method = 'POST'
                m = RE_POSTMAP.search(line_strip)
                sub_path = m.group(1) if (m and m.group(1)) else ''
            elif line_strip.startswith('@PutMapping'):
                method = 'PUT'
                m = RE_PUTMAP.search(line_strip)
                sub_path = m.group(1) if (m and m.group(1)) else ''
            elif line_strip.startswith('@DeleteMapping'):
                method = 'DELETE'
                m = RE_DELETEMAP.search(line_strip)
                sub_path = m.group(1) if (m and m.group(1)) else ''
            if method:
                full_path = (cls_map + sub_path).replace('//', '/')
                endpoints.append({
                    'method': method,
                    'path': full_path,
                    'file': str(f.relative_to(BE_SRC.parent.parent))
                })
    # 去重
    unique = {}
    for e in endpoints:
        key = f"{e['method']} {e['path']}"
        if key not in unique:
            unique[key] = e
    return list(unique.values())

# ---------- 探测 ----------
def probe(path, method, token, params=None):
    """探测一个端点"""
    status, body, raw = http(method, path, token, params=params)
    code = None
    msg = None
    if isinstance(body, dict):
        code = body.get('code')
        msg = body.get('message')
    elif isinstance(body, str):
        msg = body[:80]
    return {
        'status': status,
        'code': code,
        'message': msg,
        'raw': raw if status != 200 else None
    }

def main():
    print('=' * 60)
    print(f'端点契约扫描 v1.0 — {datetime.now().strftime("%Y-%m-%d %H:%M:%S")}')
    print('=' * 60)

    # 1. 登录
    token = login()
    if not token:
        print('❌ 登录失败，无法继续')
        return
    print('✅ 登录成功')

    # 2. 静态扫描
    fe_calls = scan_frontend()
    be_endpoints = scan_backend()
    print(f'📊 前端调用: {len(fe_calls)} 个 unique')
    print(f'📊 后端端点: {len(be_endpoints)} 个 unique')

    # 3. 探测后端端点（采样 + 全部）
    print()
    print('🔍 探测后端端点状态...')
    be_status = {}
    for i, ep in enumerate(be_endpoints):
        # 加默认 path variable 1
        path = ep['path'].replace('{id}', '1').replace('{requirementId}', '1').replace('{projectId}', '1').replace('{userId}', '1').replace('{gateId}', '1').replace('{changeId}', '1').replace('{intentId}', '1').replace('{signatureId}', '1').replace('{attId}', '1').replace('{entityId}', '1').replace('{entityType}', 'REQUIREMENT').replace('{soupId}', '1').replace('{requirementNo}', 'X').replace('{taskId}', '1').replace('{fileId}', '1').replace('{decisionForm.gateId}', '1')
        r = probe(path, ep['method'], token)
        be_status[f"{ep['method']} {ep['path']}"] = r
        if (i+1) % 20 == 0:
            print(f'  {i+1}/{len(be_endpoints)}')

    # 4. 探测前端调用（每个只探一次）
    print()
    print('🔍 探测前端调用...')
    fe_status = {}
    for i, c in enumerate(fe_calls):
        path = c['path'].replace('{id}', '1').replace('{requirementId}', '1').replace('{projectId}', '1').replace('{userId}', '1').replace('{gateId}', '1').replace('{changeId}', '1').replace('{intentId}', '1').replace('{signatureId}', '1').replace('{attId}', '1').replace('{entityId}', '1').replace('{entityType}', 'REQUIREMENT').replace('{soupId}', '1').replace('{requirementNo}', 'X').replace('{taskId}', '1').replace('{fileId}', '1').replace('{decisionForm.gateId}', '1')
        # 移除 query string
        if '?' in path:
            path = path.split('?')[0]
        # 移除模板字面量 ${...}
        path = re.sub(r'\$\{[^}]+\}', '1', path)
        r = probe(path, c['method'], token)
        fe_status[f"{c['method']} {c['path']}"] = {
            **r,
            'files': c['files']
        }
        if (i+1) % 20 == 0:
            print(f'  {i+1}/{len(fe_calls)}')

    # 5. 分析结果
    print()
    print('=' * 60)
    print('📋 分析结果')
    print('=' * 60)

    issues = []
    # 后端异常端点
    for k, r in be_status.items():
        if r['status'] == 0:
            issues.append({'type': 'BE_NETWORK', 'endpoint': k, **r})
        elif r['code'] == 'SY0301':
            issues.append({'type': 'BE_SY0301_NOTFOUND', 'endpoint': k, **r})
        elif r['code'] == 'SY0101':
            issues.append({'type': 'BE_SY0101_BADMETHOD', 'endpoint': k, **r})
        elif isinstance(r['code'], str) and r['code'].startswith('SY'):
            issues.append({'type': 'BE_SY_OTHER', 'endpoint': k, **r})

    # 前端异常调用
    for k, r in fe_status.items():
        if r['status'] == 0:
            issues.append({'type': 'FE_NETWORK', 'endpoint': k, **r, 'files': r['files']})
        elif r['code'] == 'SY0301':
            issues.append({'type': 'FE_SY0301_NOTFOUND', 'endpoint': k, **r, 'files': r['files']})
        elif r['code'] == 'SY0101':
            issues.append({'type': 'FE_SY0101_BADMETHOD', 'endpoint': k, **r, 'files': r['files']})
        elif isinstance(r['code'], str) and r['code'].startswith('SY'):
            issues.append({'type': 'FE_SY_OTHER', 'endpoint': k, **r, 'files': r['files']})

    # 按类型统计
    by_type = defaultdict(list)
    for iss in issues:
        by_type[iss['type']].append(iss)

    print()
    for t, items in sorted(by_type.items()):
        print(f'  {t}: {len(items)} 个')
        for it in items[:5]:
            print(f'    - {it["endpoint"]} → {it.get("code")} / {it.get("status")}: {it.get("message", "")[:60]}')
        if len(items) > 5:
            print(f'    ... 还有 {len(items)-5} 个')

    # 6. 输出 JSON
    out_json = {
        'scan_time': datetime.now().isoformat(),
        'summary': {
            'frontend_calls': len(fe_calls),
            'backend_endpoints': len(be_endpoints),
            'issues_by_type': {t: len(items) for t, items in by_type.items()},
            'total_issues': len(issues)
        },
        'frontend_status': fe_status,
        'backend_status': be_status,
        'issues': issues
    }
    (OUT_DIR / 'endpoint_audit.json').write_text(json.dumps(out_json, ensure_ascii=False, indent=2), encoding='utf-8')

    # 7. 输出 Markdown
    md = ['# Endpoint Audit Report (W30 Phase 1)\n']
    md.append(f'**扫描时间**：{datetime.now().strftime("%Y-%m-%d %H:%M:%S")}\n')
    md.append(f'**前端调用 unique**：{len(fe_calls)}')
    md.append(f'**后端端点 unique**：{len(be_endpoints)}\n')
    md.append('## 问题汇总\n')
    md.append('| 类型 | 数量 | 说明 |')
    md.append('|---|---|---|')
    type_desc = {
        'BE_NETWORK': '后端端点网络错误',
        'BE_SY0301_NOTFOUND': '后端端点 SY0301 资源不存在',
        'BE_SY0101_BADMETHOD': '后端端点 SY0101 方法/参数不匹配',
        'BE_SY_OTHER': '后端其他业务异常',
        'FE_NETWORK': '前端调用网络错误',
        'FE_SY0301_NOTFOUND': '前端调用 SY0301（端点不存在）',
        'FE_SY0101_BADMETHOD': '前端调用 SY0101（方法错）',
        'FE_SY_OTHER': '前端调用其他业务异常'
    }
    for t, items in sorted(by_type.items()):
        md.append(f'| {t} | {len(items)} | {type_desc.get(t, "")} |')

    md.append('\n## 详细问题（按类型）\n')
    for t, items in sorted(by_type.items()):
        md.append(f'### {t} ({len(items)} 个)\n')
        for it in items[:30]:
            files = it.get('files', [])
            file_str = ', '.join(files[:2])
            if len(files) > 2:
                file_str += f' +{len(files)-2}'
            md.append(f'- `{it["endpoint"]}` → code={it.get("code")} status={it.get("status")}: {it.get("message", "")[:80]}')
            if file_str:
                md.append(f'  - 调用方：{file_str}')
        if len(items) > 30:
            md.append(f'- ... 还有 {len(items)-30} 个')
        md.append('')

    (OUT_DIR / 'endpoint_audit.md').write_text('\n'.join(md), encoding='utf-8')
    print()
    print(f'✅ 报告已写入：')
    print(f'   - {OUT_DIR}/endpoint_audit.json')
    print(f'   - {OUT_DIR}/endpoint_audit.md')

if __name__ == '__main__':
    main()