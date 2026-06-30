#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""
前后端联合测试公共工具 — R114 配套
- 复用 tools/req_e2e_runner.py 的请求风格
- 提供 login / http_request / check_endpoint / batch_test 四个核心 API
"""
import sys
import json
import time
import requests
from datetime import datetime
from typing import Optional, Tuple, Dict, List

BASE = "http://localhost:8080/api"
FRONTEND_BASE = "http://localhost:5173"

# 8 个测试账号（密码统一 admin123）
TEST_USERS = {
    "admin":       {"password": "admin123",    "role": "ADMIN",      "user_id": 1},
    "qa_mgr":      {"password": "admin123",    "role": "QA_MGR",     "user_id": None},
    "pm":          {"password": "admin123",    "role": "PM",         "user_id": None},
    "re":          {"password": "admin123",    "role": "RE",         "user_id": None},
    "reviewer":    {"password": "admin123",    "role": "REVIEWER",   "user_id": None},
    "risk_mgr":    {"password": "admin123",    "role": "RISK_MGR",   "user_id": None},
    "compliance":  {"password": "admin123",    "role": "COMPLIANCE", "user_id": None},
    "viewer":      {"password": "admin123",    "role": "VIEWER",     "user_id": None},
}


def login(username: str = "admin", password: str = "admin123") -> Optional[str]:
    """登录获取 access_token。失败返回 None。"""
    try:
        r = requests.post(f"{BASE}/auth/login",
                          json={"username": username, "password": password},
                          timeout=5)
        if r.status_code != 200:
            return None
        data = r.json().get("data", {})
        return data.get("token") or data.get("accessToken")
    except Exception as e:
        print(f"[login] {username} 异常: {e}", file=sys.stderr)
        return None


def http_request(method: str, path: str, token: str = None,
                 params: dict = None, body: dict = None,
                 timeout: int = 5) -> Tuple[int, dict, int]:
    """
    统一 HTTP 请求封装。
    返回: (http_status, body_dict, latency_ms)
    """
    url = f"{BASE}{path}"
    headers = {"Content-Type": "application/json"}
    if token:
        headers["Authorization"] = f"Bearer {token}"
    fn = {"GET": requests.get, "POST": requests.post,
          "PUT": requests.put, "DELETE": requests.delete,
          "PATCH": requests.patch}.get(method.upper())
    if not fn:
        return 0, {"error": f"unknown method {method}"}, 0
    t0 = time.time()
    try:
        kw = {"headers": headers, "timeout": timeout}
        if params:
            kw["params"] = params
        if body and method.upper() in ("POST", "PUT", "PATCH"):
            kw["json"] = body
        r = fn(url, **kw)
        latency = int((time.time() - t0) * 1000)
        try:
            body_out = r.json()
        except Exception:
            body_out = {"_raw": r.text[:200]}
        return r.status_code, body_out, latency
    except requests.exceptions.Timeout:
        return 0, {"error": "timeout"}, int((time.time() - t0) * 1000)
    except Exception as e:
        return 0, {"error": str(e)}, int((time.time() - t0) * 1000)


def check_endpoint(method: str, path: str, token: str = None,
                   expect_http: int = 200, body: dict = None) -> dict:
    """
    单端点检查。返回:
      {
        "method": ..., "path": ..., "http": ..., "code": ...,  # 业务码
        "latency_ms": ..., "ok": bool, "msg": str
      }
    """
    http, body_resp, latency = http_request(method, path, token, body=body)
    biz_code = body_resp.get("code") if isinstance(body_resp, dict) else None
    msg = body_resp.get("message", "")[:100] if isinstance(body_resp, dict) else str(body_resp)[:100]
    ok = (http == expect_http and biz_code in (200, None))
    return {
        "method": method, "path": path,
        "http": http, "code": biz_code,
        "latency_ms": latency, "ok": ok, "msg": msg,
        "raw": body_resp if not ok else None,
    }


def batch_test(endpoints: List[Tuple[str, str]], token: str,
               module: str = "unknown") -> List[dict]:
    """
    批量端点测试。endpoints = [(method, path), ...]
    """
    results = []
    for method, path in endpoints:
        r = check_endpoint(method, path, token)
        r["module"] = module
        results.append(r)
        sym = "[OK]" if r["ok"] else ("[WARN]" if 200 <= r["http"] < 500 else "[FAIL]")
        print(f"  {sym} {method:6s} {path:60s} HTTP={r['http']} code={r['code']} {r['latency_ms']:4d}ms {r['msg'][:50]}")
    return results


def summary(results: List[dict]) -> Dict[str, int]:
    """汇总统计"""
    total = len(results)
    pass_n = sum(1 for r in results if r["ok"])
    warn_n = sum(1 for r in results if not r["ok"] and 200 <= r["http"] < 500)
    fail_n = sum(1 for r in results if not r["ok"] and (r["http"] == 0 or r["http"] >= 500))
    return {
        "total": total, "pass": pass_n, "warn": warn_n, "fail": fail_n,
        "pass_rate": f"{(pass_n*100//total) if total else 0}%",
    }


def print_summary(module: str, stats: Dict[str, int]):
    print(f"\n=== {module} 汇总 ===")
    print(f"  Total: {stats['total']} | Pass: {stats['pass']} | Warn: {stats['warn']} | Fail: {stats['fail']}")
    print(f"  Pass rate: {stats['pass_rate']}")