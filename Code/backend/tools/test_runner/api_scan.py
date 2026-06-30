#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""
批量 API 端点扫描器 — R114 配套
- 支持单角色全端点扫描
- 支持多角色关键端点验证（RBAC）
- 输出 JSON 结果
"""
import sys
import json
import os
from typing import List, Tuple, Dict
sys.path.insert(0, os.path.dirname(__file__))
from common import login, http_request, check_endpoint, TEST_USERS, summary, print_summary


def scan_module(module_name: str, endpoints: List[Tuple[str, str]],
                role: str = "admin",
                output_file: str = None) -> Dict:
    """
    扫描单模块所有端点（单角色）
    endpoints: [(method, path), ...]
    """
    print(f"\n>>> 开始扫描 [{module_name}] as [{role}]")
    token = login(role, TEST_USERS[role]["password"])
    if not token:
        print(f"  [FAIL] {role} 登录失败")
        return {"module": module_name, "role": role, "login_ok": False, "results": []}

    # R114 修复：每个模块先做一次预热请求，确保 token 完全生效
    # 根因：之前 admin token 在跨角色测试中正常，但主扫描首次调用偶发 403
    # 推测：JwtAuthenticationFilter 内部状态或 cold-start 缓存问题
    warmup_status, warmup_body, _ = http_request("GET", "/auth/me", token=token)
    if warmup_status != 200:
        # 重新登录一次（fallback）
        print(f"  [warmup-fail] /auth/me HTTP={warmup_status}, 重新登录...")
        token = login(role, TEST_USERS[role]["password"])
    else:
        print(f"  [warmup-ok] /auth/me HTTP={warmup_status}")

    results = []
    for method, path in endpoints:
        r = check_endpoint(method, path, token)
        r["module"] = module_name
        r["role"] = role
        results.append(r)
        sym = "[OK]" if r["ok"] else ("[WARN]" if 200 <= r["http"] < 500 else "[FAIL]")
        print(f"  {sym} {method:6s} {path:55s} HTTP={r['http']:>3} code={str(r['code']):>5} {r['latency_ms']:>4}ms")

    stats = summary(results)
    print_summary(module_name, stats)

    out = {
        "module": module_name, "role": role, "login_ok": True,
        "stats": stats, "results": results,
    }
    if output_file:
        with open(output_file, "w", encoding="utf-8") as f:
            json.dump(out, f, ensure_ascii=False, indent=2, default=str)
        print(f"  [SAVE] {output_file}")
    return out


def cross_role_test(module_name: str,
                    key_endpoints: List[Tuple[str, str]],
                    roles: List[str] = None,
                    output_file: str = None) -> Dict:
    """
    跨角色测试关键端点
    """
    if roles is None:
        roles = ["admin", "qa_mgr", "viewer"]
    print(f"\n>>> 跨角色测试 [{module_name}] - {len(key_endpoints)} 端点 x {len(roles)} 角色")

    tokens = {}
    for role in roles:
        t = login(role, TEST_USERS[role]["password"])
        tokens[role] = t
        sym = "[OK]" if t else "[FAIL]"
        print(f"  {sym} {role} login: {'ok' if t else 'fail'}")

    matrix = []
    for method, path in key_endpoints:
        row = {"method": method, "path": path, "results": {}}
        for role in roles:
            if not tokens[role]:
                row["results"][role] = {"http": 0, "code": None, "ok": False, "msg": "login failed"}
                continue
            r = check_endpoint(method, path, tokens[role])
            row["results"][role] = {
                "http": r["http"], "code": r["code"],
                "ok": r["ok"], "msg": r["msg"]
            }
            sym = "[OK]" if r["ok"] else ("[WARN]" if 200 <= r["http"] < 500 else "[FAIL]")
            print(f"  {sym} {method:6s} {path:50s} [{role:10s}] HTTP={r['http']} code={r['code']}")
        matrix.append(row)

    out = {"module": module_name, "matrix": matrix, "roles": roles}
    if output_file:
        with open(output_file, "w", encoding="utf-8") as f:
            json.dump(out, f, ensure_ascii=False, indent=2, default=str)
        print(f"  [SAVE] {output_file}")
    return out


if __name__ == "__main__":
    # 自检：测试 common.py 是否可用
    print("=== api_scan self-check ===")
    print("Test users:")
    for role, info in TEST_USERS.items():
        t = login(role, info["password"])
        sym = "[OK]" if t else "[FAIL]"
        print(f"  {sym} {role:12s} ({info['role']:12s})")