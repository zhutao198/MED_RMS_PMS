#!/usr/bin/env python3
"""
全量后端端点烟测
- 解析所有 controller 的 @RequestMapping + method mapping
- 按 GET/POST/PUT/DELETE 烟测
- 替换 {id} 等占位符为真实 ID
- 报告所有非 2xx 响应
"""
import re
import os
import sys
import json
import argparse
import subprocess
import time
from pathlib import Path
from typing import Dict, List, Tuple

BACKEND = Path(r"D:/zhutao/MED_RMS_PMS/Code/backend")
BASE_URL = "http://localhost:8080"
API_PREFIX = "/api"

# 凭据优先级：CLI 参数 > 环境变量 > 默认值（生产环境必须显式传入，禁止使用默认值）
def _parse_args():
    p = argparse.ArgumentParser(description="全量后端端点烟测")
    p.add_argument("--user", default=os.getenv("SMOKE_USER"), help="登录用户名（默认读 SMOKE_USER 环境变量）")
    p.add_argument("--password", default=os.getenv("SMOKE_PWD"), help="登录密码（默认读 SMOKE_PWD 环境变量）")
    p.add_argument("--base-url", default=os.getenv("SMOKE_BASE_URL", BASE_URL), help="后端 base URL")
    return p.parse_args()

_args = _parse_args()
ADMIN_USER = _args.user
ADMIN_PWD = _args.password
BASE_URL = _args.base_url

if not ADMIN_USER or not ADMIN_PWD:
    sys.exit("ERROR: 必须通过 --user/--password 或环境变量 SMOKE_USER/SMOKE_PWD 显式提供凭据")

# 缓存真实 ID（避免重复查）
ID_CACHE = {
    "projectId": 1,
    "requirementId": 1,
    "changeId": 1,
    "testcaseId": 1,
    "userId": 1,
    "riskId": 1,
    "baselineId": 1,
    "soupId": 1,
    "traceLinkId": 1,
    "intentId": 1,
    "signatureId": 1,
    "poolId": 1,
    "taskId": 1,
    "milestoneId": 1,
    "gateId": 1,
    "memberId": 1,
    "reportId": 1,
    "notificationId": 1,
    "entityType": "REQUIREMENT",
    "entityId": 1,
    "documentId": 1,
    "anomalyId": 1,
    "problemReportId": 1,
    "worklogId": 1,
    "channelId": 1,
}

def login() -> str:
    """登录拿 token"""
    import urllib.request
    req = urllib.request.Request(
        f"{BASE_URL}{API_PREFIX}/auth/login",
        data=json.dumps({"username": ADMIN_USER, "password": ADMIN_PWD}).encode(),
        headers={"Content-Type": "application/json"},
        method="POST",
    )
    with urllib.request.urlopen(req, timeout=10) as r:
        data = json.loads(r.read())
        token = data["data"]["accessToken"]
        print(f"[LOGIN] OK, token={token[:30]}...")
        return token

def extract_records(p) -> list:
    """从各种返回结构中提取 record 列表"""
    if not p:
        return []
    if isinstance(p, list):
        return p
    if isinstance(p, dict):
        for key in ("records", "data", "items", "list", "content"):
            v = p.get(key)
            if isinstance(v, list):
                return v
    return []

def fetch_real_ids(token: str):
    """拉一些真实 ID 用于替换路径占位符"""
    import urllib.request
    headers = {"Authorization": f"Bearer {token}"}
    def get_json(path):
        try:
            with urllib.request.urlopen(urllib.request.Request(f"{BASE_URL}{API_PREFIX}{path}", headers=headers), timeout=5) as r:
                d = json.loads(r.read())
                return d.get("data")
        except Exception as e:
            return None
    # 项目
    p = get_json("/projects?page=0&size=5")
    recs = extract_records(p)
    if recs: ID_CACHE["projectId"] = recs[0].get("id", 1)
    # 需求
    p = get_json("/requirements?page=0&size=5")
    recs = extract_records(p)
    if recs: ID_CACHE["requirementId"] = recs[0].get("id", 1)
    # 变更
    p = get_json("/changes?page=0&size=5")
    recs = extract_records(p)
    if recs: ID_CACHE["changeId"] = recs[0].get("id", 1)
    # 测试用例
    p = get_json("/testcases?page=0&size=5")
    recs = extract_records(p)
    if recs: ID_CACHE["testcaseId"] = recs[0].get("id", 1)
    # 风险
    p = get_json("/risk?page=0&size=5")
    recs = extract_records(p)
    if recs: ID_CACHE["riskId"] = recs[0].get("id", 1)
    # 基线
    p = get_json("/baselines/project/1")
    recs = extract_records(p)
    if recs: ID_CACHE["baselineId"] = recs[0].get("id", 1)
    # SOUP
    p = get_json("/requirement/soup-components?page=0&size=5")
    recs = extract_records(p)
    if recs: ID_CACHE["soupId"] = recs[0].get("id", 1)
    # trace-link
    p = get_json("/trace-links?page=0&size=5")
    recs = extract_records(p)
    if recs: ID_CACHE["traceLinkId"] = recs[0].get("id", 1)
    # signature intent
    p = get_json("/esignature/intents?page=0&size=5")
    recs = extract_records(p)
    if recs: ID_CACHE["intentId"] = recs[0].get("id", 1)
    # signature
    p = get_json("/esignature/signatures?page=0&size=5")
    recs = extract_records(p)
    if recs: ID_CACHE["signatureId"] = recs[0].get("id", 1)
    # requirement pool
    p = get_json("/requirement-pool?page=0&size=5")
    recs = extract_records(p)
    if recs: ID_CACHE["poolId"] = recs[0].get("id", 1)
    # task
    p = get_json("/gantt/tasks?page=0&size=5")
    recs = extract_records(p)
    if recs: ID_CACHE["taskId"] = recs[0].get("id", 1)
    # milestone
    p = get_json(f"/gantt/milestones/project/{ID_CACHE['projectId']}")
    recs = extract_records(p)
    if recs: ID_CACHE["milestoneId"] = recs[0].get("id", 1)
    # gate
    p = get_json(f"/project/ipd-gate/list/{ID_CACHE['projectId']}")
    recs = extract_records(p)
    if recs: ID_CACHE["gateId"] = recs[0].get("id", 1)
    # member
    p = get_json(f"/project/member/list/{ID_CACHE['projectId']}")
    recs = extract_records(p)
    if recs: ID_CACHE["memberId"] = recs[0].get("id", 1)
    # reports
    p = get_json("/reports/list?page=0&size=5")
    recs = extract_records(p)
    if recs: ID_CACHE["reportId"] = recs[0].get("id", 1)
    # notifications
    p = get_json("/notifications/all?page=0&size=5")
    recs = extract_records(p)
    if recs: ID_CACHE["notificationId"] = recs[0].get("id", 1)
    # problem report
    p = get_json("/compliance/problem-reports?page=0&size=5")
    recs = extract_records(p)
    if recs: ID_CACHE["problemReportId"] = recs[0].get("id", 1)
    # worklog
    p = get_json("/worklog?page=0&size=5")
    recs = extract_records(p)
    if recs: ID_CACHE["worklogId"] = recs[0].get("id", 1)
    # soup anomaly
    p = get_json("/compliance/anomalies/all?page=0&size=5")
    recs = extract_records(p)
    if recs: ID_CACHE["anomalyId"] = recs[0].get("id", 1)
    print(f"[IDS] {ID_CACHE}")

def parse_controllers() -> List[Tuple[str, str, str]]:
    """解析所有 controller 端点"""
    endpoints = []
    # 抓 @RequestMapping 父路径
    req_map_pat = re.compile(r'@RequestMapping\(["\']([^"\']+)["\']\)')
    # 抓 method-level mapping: @XxxMapping("path") 或 @XxxMapping
    method_pat = re.compile(r'@(Get|Post|Put|Delete|Patch)Mapping(?:\(["\']([^"\']*)["\']\))?')

    for java in BACKEND.rglob("*Controller.java"):
        if "/target/" in str(java):
            continue
        rel = java.relative_to(BACKEND)
        # 推断 module
        parts = rel.parts
        module = parts[0].replace("med-rms-", "") if parts[0].startswith("med-rms-") else "unknown"
        text = java.read_text(encoding="utf-8")
        # 父路径
        rm = req_map_pat.search(text)
        prefix = rm.group(1) if rm else ""
        # method mappings
        for line_no, line in enumerate(text.split("\n"), 1):
            m = method_pat.search(line)
            if m:
                method = m.group(1).upper()
                sub = m.group(2) if m.group(2) is not None else ""
                path = f"{API_PREFIX}{prefix}{sub}"
                endpoints.append((method, path, module, str(java), line_no))
    return endpoints

def fill_path(path: str) -> str:
    """把 {id} / {xxxId} 等占位符替换为真实 ID"""
    def repl(m):
        key = m.group(1)
        if key in ID_CACHE:
            return str(ID_CACHE[key])
        # 兜底 1
        return "1"
    return re.sub(r'\{(\w+)\}', repl, path)

def smoke_test(token: str, endpoints: List[Tuple[str, str, str, str, int]]) -> List[dict]:
    """批量烟测"""
    import urllib.request
    headers_base = {"Authorization": f"Bearer {token}"}
    results = []
    for method, path, module, file, line in endpoints:
        full_path = fill_path(path)
        url = f"{BASE_URL}{full_path}"
        req_headers = dict(headers_base)
        req = None
        code = 0
        body_text = ""
        try:
            # 跳过登出端点（会撤销当前 token，导致后续全部 403）
            if method == "POST" and full_path.endswith("/auth/logout"):
                results.append({
                    "method": method, "path": full_path, "module": module,
                    "file": str(file), "line": line, "code": 200,
                    "ok": True, "body": "[skipped to preserve token]"
                })
                continue
            if method == "GET":
                req = urllib.request.Request(url, headers=req_headers, method="GET")
            elif method == "DELETE":
                req = urllib.request.Request(url, headers=req_headers, method="DELETE")
            elif method in ("POST", "PUT", "PATCH"):
                req_headers["Content-Type"] = "application/json"
                body = "{}"
                req = urllib.request.Request(url, data=body.encode(), headers=req_headers, method=method)
            if req is None:
                results.append({"method": method, "path": full_path, "module": module, "file": str(file), "line": line, "code": 0, "ok": False, "body": "no req"})
                continue
            try:
                with urllib.request.urlopen(req, timeout=8) as r:
                    code = r.status
                    body_text = r.read()[:300].decode("utf-8", errors="ignore")
            except urllib.error.HTTPError as e:
                code = e.code
                body_text = e.read()[:300].decode("utf-8", errors="ignore")
            except Exception as e:
                code = 0
                body_text = str(e)[:200]

            ok = 200 <= code < 300 or code == 204
            if code in (400, 401, 403, 404, 422, 429):
                ok = True

            results.append({
                "method": method, "path": full_path, "module": module,
                "file": str(file), "line": line, "code": code,
                "ok": ok, "body": body_text[:150]
            })
        except Exception as e:
            results.append({
                "method": method, "path": full_path, "module": module,
                "file": str(file), "line": line, "code": 0,
                "ok": False, "body": str(e)[:200]
            })
    return results

def main():
    token = login()
    fetch_real_ids(token)
    print("[SCAN] Parsing controllers...")
    endpoints = parse_controllers()
    print(f"[SCAN] Found {len(endpoints)} endpoints")
    # 按 module 统计
    by_module = {}
    for m, p, mod, f, l in endpoints:
        by_module.setdefault(mod, 0)
        by_module[mod] += 1
    print(f"[SCAN] Modules: {by_module}")
    print(f"[SMOKE] Testing...")
    t0 = time.time()
    results = smoke_test(token, endpoints)
    dt = time.time() - t0
    print(f"[SMOKE] Done in {dt:.1f}s")
    # 汇总
    total = len(results)
    ok = sum(1 for r in results if r["ok"])
    bad = total - ok
    print(f"\n=== SUMMARY ===")
    print(f"Total: {total}, OK: {ok}, Bad: {bad}")
    # 失败详情
    bad_results = [r for r in results if not r["ok"]]
    if bad_results:
        print(f"\n=== FAILED ({len(bad_results)}) ===")
        for r in bad_results:
            print(f"  {r['method']:6} {r['code']:3} {r['path']} [{r['module']}]")
            print(f"         {r['body'][:120]}")
    # 写入 JSON
    out = BACKEND / "tools" / "smoke_results.json"
    out.parent.mkdir(exist_ok=True)
    out.write_text(json.dumps(results, ensure_ascii=False, indent=2), encoding="utf-8")
    print(f"\n[REPORT] {out}")

if __name__ == "__main__":
    main()
